"""A read-only, public-source ledger: paths, useful lines and file hashes."""
from pathlib import Path
import hashlib
import json
import subprocess

ROOT=Path(__file__).resolve().parent
REPO=ROOT.parent.parent

def java(service,name):return f'services/{service}/src/main/java/**/{name}.java'

# ID, path or unique source glob, useful symbol, why the reader should open it.
DEFINITIONS=[
 ('R01','pom.xml','<modules>','Java reactor සහ shared package build order'),
 ('R02','apps/web/package.json','"scripts"','Frontend build / test commands'),
 ('R03','apps/web/src/config/env.ts','const runtime','Browser runtime configuration precedence'),
 ('R04','apps/web/src/api/client.ts','async function send','Fetch, auth retry සහ errors'),
 ('R05','apps/web/src/auth/tokenStore.ts','export const tokenStore','In-memory access token / single-flight refresh'),
 ('R06','services/gateway/src/main/resources/application.yaml','routes:','Gateway routes / timeout settings'),
 ('R07',java('gateway','IdentityHeaderFilter'),'withTrustedHeaders','Untrusted identity headers වෙනස් කිරීම'),
 ('R08',java('gateway','SecurityConfig'),'SecurityWebFilterChain','Gateway authorization boundary'),
 ('R09',java('identity-service','AuthController'),'public ResponseEntity<AuthResponse> login','Login response සහ refresh cookie'),
 ('R10',java('identity-service','RefreshTokenServiceImpl'),'public TokenRotationResult rotate','Refresh rotation / replay handling'),
 ('R11',java('parcel-service','ParcelController'),'public ResponseEntity<ParcelView> book','HTTP mapping → domain service'),
 ('R12',java('parcel-service','ParcelService'),'public ParcelView book','Booking / ownership / outbox transaction'),
 ('R13','services/parcel-service/src/main/resources/db/migration/V1__parcel_foundation.sql','uq_parcel_customer_booking','Database uniqueness constraints'),
 ('R14',java('parcel-service','ParcelStateMachine'),'TRANSITIONS','Allowed parcel transitions'),
 ('R15',java('parcel-service','OutboxPublisher'),'public void publish','Publish confirms / retry retention'),
 ('R16',java('parcel-service','OutboxRepository'),'PESSIMISTIC_WRITE','Publisher locking boundary'),
 ('R17',java('common','EventEnvelope'),'public record EventEnvelope','Event ID / version / timestamp contract'),
 ('R18',java('pricing-service','PricingEngine'),'class PricingEngine','Pricing strategy composition'),
 ('R19',java('tracking-service','TrackingEventListener'),'private void processEvent','Projection update සහ dedup markers'),
 ('R20',java('tracking-service','DefaultTrackingProjectionService'),'getProjection(String','Cache lookup / snapshot backfill'),
 ('R21','apps/web/src/realtime/ladder.ts','export function runLadder','WebSocket → SSE → polling fallback'),
 ('R22',java('notification-service','NotificationService'),'void handleParcelStatusChanged','Preferences / channel send / delivery log'),
 ('R23',java('notification-service','RabbitMQConfig'),'class RabbitMQConfig','Event converter සහ listener configuration'),
 ('R24',java('merchant-service','MerchantOrderService'),'public View create','Order reference → parcel API integration'),
 ('R25',java('merchant-service','WebhookService'),'void deliver','HMAC headers / retries / receiver URL checks'),
 ('R26',java('dispatch-service','AssignmentService'),'private CommandView execute','RPC පිටත තබන command orchestration'),
 ('R27',java('dispatch-service','AssignmentTransactions'),'public AssignmentCommand prepare','Prepare / reserve / confirm / reject transactions'),
 ('R28',java('parcel-service','ProofService'),'class ProofService','Private object upload / metadata / ownership'),
 ('R29','services/ai-assistant-service/app/main.py','def create_app','Runtime persistence සහ clients wiring'),
 ('R30','services/ai-assistant-service/app/graph/runner.py','async def run','Actual chat handlers / model fallback'),
 ('R31','services/ai-assistant-service/app/workflows/operations.py','class InMemoryOperationStore','Process-local pending operations'),
 ('R32','services/ai-assistant-service/app/api/health.py','@router.get("/actuator/metrics")','Actual health සහ JSON metric routes'),
 ('R33','apps/web/Dockerfile','FROM node','Frontend build → nginx runtime'),
 ('R34','services/parcel-service/Dockerfile','FROM','Java build / runtime artifact'),
 ('R35','infra/kubernetes/helm/swiftdrop/values.yaml','services:','Deployment defaults සහ workload contract'),
 ('R36','infra/kubernetes/helm/swiftdrop/templates/migrations.yaml','kind: Job','Bounded schema migration hooks'),
 ('R37','infra/kubernetes/scripts/deploy-local.ps1','param(','Two-stage local deployment orchestration'),
 ('R38','infra/kubernetes/helm/swiftdrop/templates/networkpolicies.yaml','swiftdrop-internal-traffic','Current broad internal policy'),
 ('R39','infra/kubernetes/helm/swiftdrop/templates/observability.yaml','kind: ServiceMonitor','Scrape paths / rules / missing runtime integration checks'),
 ('R40','.github/workflows/kubernetes-validate.yml','jobs:','Pre-release validation gates'),
 ('R41','.github/workflows/kubernetes-release.yml','jobs:','Build / publish / sign / deploy'),
 ('R42','kubernetesclusterimplementationplan.md','Recorded local release','Dated verified results සහ pending work'),
]
# The common module is outside services/.
DEFINITIONS[16]=('R17','packages/common/src/main/java/**/EventEnvelope.java','public record EventEnvelope',DEFINITIONS[16][3])

def capture():
    records=[]
    for ident,pattern,focus,purpose in DEFINITIONS:
        matches=list(REPO.glob(pattern))
        if len(matches)!=1:raise ValueError(f'{ident}: expected one source for {pattern}, got {len(matches)}')
        path=matches[0].resolve()
        path.relative_to(REPO.resolve())
        if path.name.startswith('.env') or path.suffix in ('.key','.pem'):raise ValueError('Secret material is outside the source ledger')
        data=path.read_bytes()
        lines=data.decode('utf-8-sig').splitlines()
        line=next((i for i,text in enumerate(lines,1) if focus in text),None)
        if line is None:raise ValueError(f'{ident}: source focus not found: {focus}')
        records.append({'id':ident,'path':path.relative_to(REPO).as_posix(),'line':line,'focus':focus,'purpose':purpose,'sha256':hashlib.sha256(data).hexdigest()})
    commit=subprocess.run(['git','rev-parse','HEAD'],cwd=REPO,capture_output=True,text=True,check=True).stdout.strip()
    dirty=bool(subprocess.run(['git','status','--porcelain'],cwd=REPO,capture_output=True,text=True,check=True).stdout)
    result={'date':'2026-11-16','commit':commit,'working_tree_dirty':dirty,'scope':'Public source files; working-tree hashes identify the reviewed contents. No secrets or live cluster state captured.','files':records}
    (ROOT/'source-map.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    return result

if __name__=='__main__':print(f'Captured {len(capture()["files"])} source references')
