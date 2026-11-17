"""Cluster-free regression checks for the migration and bootstrap contracts."""

import shutil
import subprocess
from pathlib import Path

import pytest
import yaml

CHART = Path(__file__).resolve().parents[1] / "helm" / "swiftdrop"
HELM = shutil.which("helm")
JAVA_SERVICES = ("identity", "parcel", "pricing", "dispatch", "merchant", "notification")


def render(values="values-local.yaml", *overrides):
    result = subprocess.run(
        [HELM, "template", "swiftdrop", str(CHART), "--namespace", "swiftdrop",
         "--values", str(CHART / values), *overrides],
        capture_output=True, text=True, check=True,
    )
    return [doc for doc in yaml.safe_load_all(result.stdout) if doc]


@pytest.mark.parametrize("values", ["values-local.yaml", "values-production.example.yaml"])
@pytest.mark.parametrize("upgrade", [False, True])
def test_migrations_precede_rollout_and_use_owner_credentials(values, upgrade):
    docs = render(values, *(["--is-upgrade"] if upgrade else []))
    jobs = [doc for doc in docs if doc["kind"] == "Job" and "migrate" in doc["metadata"]["name"]]
    assert len(jobs) == 7
    accounts = {doc["metadata"]["name"]: doc for doc in docs if doc["kind"] == "ServiceAccount"}
    for job in jobs:
        annotations = job["metadata"]["annotations"]
        assert annotations["helm.sh/hook"] == "pre-install,pre-upgrade"
        pod = job["spec"]["template"]["spec"]
        account = accounts[pod["serviceAccountName"]]
        assert account["metadata"]["annotations"]["helm.sh/hook-weight"] == "-10"
        assert not account["automountServiceAccountToken"]
        container = pod["containers"][0]
        assert "envFrom" not in container  # Pre-install cannot rely on ordinary ConfigMaps.
        keys = {env["valueFrom"]["secretKeyRef"]["key"] for env in container["env"]}
        component = job["metadata"]["labels"]["app.kubernetes.io/component"]
        if component.startswith("ai-assistant"):
            assert keys == {"ASSISTANT_DATABASE_URL", "CHECKPOINT_DATABASE_URL"}
            assert container["command"] == ["python", "-m", "app.migrate"]
        else:
            owner = component.split("-")[0].upper()
            assert keys == {f"{owner}_DB_URL", f"{owner}_DB_USERNAME", f"{owner}_DB_PASSWORD"}
            assert container["command"][-1] == "DatabaseMigration"
            assert "-jar" not in container["command"]
        assert job["spec"]["activeDeadlineSeconds"] > 0
        assert job["spec"]["backoffLimit"] == 1
    policy = next(doc for doc in docs if doc["metadata"]["name"].endswith("migration-egress"))
    assert policy["metadata"]["annotations"]["helm.sh/hook-weight"] == "-10"
    ports = {port["port"] for rule in policy["spec"]["egress"] for port in rule["ports"]}
    assert ports == {53, 5432}


def test_application_replicas_do_not_run_migrations():
    docs = render()
    deployments = {doc["metadata"]["name"]: doc for doc in docs if doc["kind"] == "Deployment"}
    for owner in JAVA_SERVICES:
        env = deployments[f"{owner}-service"]["spec"]["template"]["spec"]["containers"][0]["env"]
        assert {item["name"]: item.get("value") for item in env}["SPRING_FLYWAY_ENABLED"] == "false"
    config = next(doc for doc in docs if doc["kind"] == "ConfigMap" and doc["metadata"]["name"].endswith("-config"))
    assert config["data"]["CHECKPOINT_AUTO_SETUP"] == "false"


def test_bootstrap_contains_dependencies_without_application_rollout():
    docs = render("values-local.yaml", "--set", "bootstrapOnly=true")
    assert {doc["metadata"]["name"] for doc in docs if doc["kind"] == "StatefulSet"} == {
        "postgres", "redis", "rabbitmq", "minio"
    }
    assert not any(doc["kind"] in {"Ingress", "HorizontalPodAutoscaler", "PodDisruptionBudget"} for doc in docs)
    assert not any("migrate" in doc["metadata"]["name"] for doc in docs)
    assert {doc["metadata"]["name"] for doc in docs if doc["kind"] == "Deployment"} == {"mailpit"}


def test_production_keeps_data_services_external_and_secrets_unrendered():
    docs = render("values-production.example.yaml")
    assert not any(doc["kind"] in {"StatefulSet", "Secret"} for doc in docs)
    assert all(doc["spec"].get("type", "ClusterIP") == "ClusterIP" for doc in docs if doc["kind"] == "Service")
    ingress = next(doc for doc in docs if doc["kind"] == "Ingress")
    assert {path["backend"]["service"]["name"] for rule in ingress["spec"]["rules"] for path in rule["http"]["paths"]} == {"web", "gateway"}


def test_migrations_disabled_and_private_registry_credentials():
    docs = render("values-production.example.yaml", "--set", "migrations.enabled=false")
    assert not any(doc["kind"] == "Job" for doc in docs)
    docs = render("values-production.example.yaml", "--set", "global.imagePullSecrets[0].name=registry-auth")
    for job in (doc for doc in docs if doc["kind"] == "Job"):
        assert job["spec"]["template"]["spec"]["imagePullSecrets"] == [{"name": "registry-auth"}]


def test_assistant_can_be_omitted():
    docs = render("values-production.example.yaml", "--set", "services.ai-assistant-service.enabled=false")
    assert not any(doc["metadata"]["name"].startswith("ai-assistant-service") for doc in docs)
    assert any(doc["kind"] == "Deployment" and doc["metadata"]["name"] == "gateway" for doc in docs)


def test_local_ingress_and_browser_config_work_without_hosts_file_changes():
    docs = render()
    ingress = next(doc for doc in docs if doc["kind"] == "Ingress")
    assert {rule["host"] for rule in ingress["spec"]["rules"]} == {"localhost", "swiftdrop.local"}
    config = next(doc for doc in docs if doc["kind"] == "ConfigMap" and doc["metadata"]["name"].endswith("-config"))
    assert config["data"]["WEB_BASE_URL"] == "http://localhost:8088"


def test_dependency_probes_tolerate_cold_start_without_restarting_a_healthy_broker():
    docs = render()
    stateful = {doc["metadata"]["name"]: doc for doc in docs if doc["kind"] == "StatefulSet"}
    rabbit = stateful["rabbitmq"]["spec"]["template"]["spec"]["containers"][0]
    assert rabbit["startupProbe"]["tcpSocket"] == {"port": "amqp"}
    assert rabbit["readinessProbe"]["tcpSocket"] == {"port": "amqp"}
    assert "livenessProbe" not in rabbit
    postgres = stateful["postgres"]["spec"]["template"]["spec"]["containers"][0]
    assert postgres["startupProbe"]["failureThreshold"] * postgres["startupProbe"]["periodSeconds"] >= 300
    for name in ("startupProbe", "readinessProbe", "livenessProbe"):
        assert postgres[name]["timeoutSeconds"] >= 5
    redis = stateful["redis"]["spec"]["template"]["spec"]["containers"][0]
    for name in ("startupProbe", "readinessProbe", "livenessProbe"):
        assert redis[name]["timeoutSeconds"] >= 5


@pytest.mark.parametrize("values, window", [("values-local.yaml", 600), ("values-production.example.yaml", 150)])
def test_startup_window_fits_the_rollout_deadline(values, window):
    for doc in render(values):
        if doc["kind"] != "Deployment" or doc["metadata"]["name"] == "mailpit":
            continue
        probe = doc["spec"]["template"]["spec"]["containers"][0]["startupProbe"]
        assert probe["failureThreshold"] * probe["periodSeconds"] == window
        assert probe["timeoutSeconds"] >= 3
        assert doc["spec"]["progressDeadlineSeconds"] >= window + 300


def test_gateway_probes_do_not_use_the_downstream_aggregate_health_check():
    deployments = {doc["metadata"]["name"]: doc for doc in render() if doc["kind"] == "Deployment"}
    gateway = deployments["gateway"]["spec"]["template"]["spec"]["containers"][0]
    assert gateway["startupProbe"]["httpGet"]["path"] == "/actuator/health/liveness"
    assert gateway["livenessProbe"]["httpGet"]["path"] == "/actuator/health/liveness"
    assert gateway["readinessProbe"]["httpGet"]["path"] == "/actuator/health/readiness"
    web = deployments["web"]["spec"]["template"]["spec"]["containers"][0]
    assert web["livenessProbe"]["httpGet"]["path"] == "/healthz"
