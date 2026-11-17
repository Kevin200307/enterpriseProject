from book import add, p, h, code, box, table, steps, ul, diagram

PART='04 / පරීක්ෂණ, ආරක්ෂාව සහ මෙහෙයුම්'

def populate():
    add('probes',PART,'Probes තුනේ වගකීම්','Startup protects slow boots; readiness controls traffic',
        table(['Probe එක','Fail වූ විට / නිවැරදි ප්‍රශ්නය'],[
            ('Startup','Application එක තවම start වෙමින්ද? සාර්ථක වනතෙක් readiness/liveness checks ආරම්භ නොවේ; deadline ඉක්මවුවහොත් restart විය හැක.'),
            ('Readiness','දැනට request භාරගත හැකිද? Fail වුවහොත් සාමාන්‍ය Service routing වලින් ඉවත් වේ.'),
            ('Liveness','Process එක නැවත start කිරීමෙන් ප්‍රයෝජනයක් තිබේද? Repeated failure container restart එකකට හේතු වේ.')])+
        code('''startupProbe:
          httpGet: {path: /actuator/health/liveness, port: 8080}
          periodSeconds: 5
          timeoutSeconds: 3
          failureThreshold: 120
        readinessProbe:
          httpGet: {path: /actuator/health/readiness, port: 8080}
        livenessProbe:
          httpGet: {path: /actuator/health/liveness, port: 8080}''','YAML · gateway paths / local startup window')+
        p('Local startup budget එක 120 × 5 ≈ 600 seconds ය. Shared CPU මත Java cold starts සඳහා එය දීර්ඝ කර ඇත. Production default එක 30 × 5 ≈ 150 seconds ය; target capacity සහ measured boot time අනුව tune කරන්න. Startup budget එකට අමතරව Deployment progress deadline එකත් ඉඩ ලබාදිය යුතුය.')+
        box('Gateway fix එක','Aggregate <code>/actuator/health</code> එක downstream services සහ Redis පරීක්ෂා කරයි. එය gateway liveness සඳහා භාවිත කිරීමෙන් බාහිර dependency failure නිසා healthy gateway restart විය හැක. Dedicated readiness / liveness groups භාවිත කර එම paths දෙකට පමණක් anonymous health access විවෘත කර ඇත.')+
        p('RabbitMQ එකට වෙනම probe strategy ඇත. Database / broker failure එකකට සෑම application එකම restart කර cascading failure එකක් ඇති නොකරන්න.'),('probes','boot'))

    add('resources',PART,'CPU, memory සහ scaling','Requests, limits, scheduling and HPA prerequisites',
        table(['Setting එක','තේරුම'],[
            ('cpu: 150m','CPU core එකක 0.15 ප්‍රමාණයක්. Request එක scheduling සඳහා යොදාගනී.'),
            ('memory: 256Mi','Binary memory units. Limit ඉක්මවීම OOM termination එකකට හේතු විය හැක.'),
            ('CPU limit','Linux CPU throttling ඇති කළ හැක; ප්‍රමාණවත් memory තිබුණත් latency වැඩිවිය හැක.'),
            ('replicas','App copies ගණන; database connection සහ external quota භාවිතයත් වැඩිවේ.'),
            ('Topology / affinity / taints','Pods තැබිය හැකි nodes සහ failure domains පාලනය කරයි.')])+
        p('HPA එකට metrics source එකක් අවශ්‍යය. CPU utilization target එකක් භාවිත කරන විට අදාල CPU requests නිවැරදිව තිබිය යුතුය. Local chart එක autoscaling disable කරයි; production example එක minReplicas 2, maxReplicas 8 ලෙස දක්වයි. Metrics Server / production metrics integration තව configure කළ යුතුය.')+
        code('''kubectl --context kind-swiftdrop -n swiftdrop get hpa
        # Requires a working metrics API; not installed by every local setup.
        kubectl --context kind-swiftdrop top nodes
        kubectl --context kind-swiftdrop -n swiftdrop top pods''')+
        box('Scale කිරීමට පෙර shared state බලන්න','Assistant pending confirmations process memory තුළ ඇත. SSE / WebSocket reconnect behaviour, in-process state සහ database pools review නොකර replicas වැඩි කිරීමෙන් correctness සහතික නොවේ.','warning')+
        p('Capacity planning සඳහා replicas × per-pod database pool එක managed database connection budget එකට ගැළපේද ගණනය කරන්න. CPU අඩු වූ නිසා probe timeout එක වැඩි කිරීම පමණක් ප්‍රමාණවත්ද යන්න latency සහ saturation metrics මඟින් තීරණය කරන්න.'),('resources','hpa'))

    add('security',PART,'Pod සහ Kubernetes API ආරක්ෂාව','Least privilege at the process, namespace and API layers',
        table(['Layer එක','මෙහි සැකසුම / production අවශ්‍යතාව'],[
            ('Pod identity','Dedicated ServiceAccount; token automount default අක්‍රියයි.'),
            ('Process permissions','Non-root UID, privilege escalation අක්‍රිය, Linux capabilities සියල්ල drop.'),
            ('Syscalls','RuntimeDefault seccomp profile.'),
            ('Filesystem','අවශ්‍ය writable paths සඳහා bounded emptyDir / PVC. සෑම image එකකම root filesystem read-only නොවේ.'),
            ('Namespace admission','Local baseline Pod Security label; production platform ownership සහ restricted compatibility review.'),
            ('Deploy permissions','Namespace-scoped deployer RBAC; short-lived projected token.')])+
        code('''kubectl --context kind-swiftdrop -n swiftdrop get sa
        kubectl --context kind-swiftdrop -n swiftdrop get role,rolebinding
        kubectl --context kind-swiftdrop auth can-i get pods -n swiftdrop
        kubectl --context kind-swiftdrop auth can-i get secrets -n swiftdrop''')+
        p('RBAC හි Role එක namespace එකකට බලතල දක්වයි; RoleBinding එක එය user / ServiceAccount එකකට බැඳේ. ClusterRole / ClusterRoleBinding cluster-wide access සඳහා භාවිත විය හැකි නමුත් application deployment එකකට cluster-admin අවශ්‍ය නොවිය යුතුය.')+
        box('Logs සහ evidence','JWT, API keys, private keys, customer addresses, phone numbers සහ raw chat secrets incident tickets තුළ තබන්න එපා. Request ID, conversation ID, operation ID සහ redacted status codes භාවිත කරන්න.')+
        p('Secret reader permission එක credential access එකකි. Application API access, Kubernetes API access සහ database access වෙන වෙනම review කරන්න.'),('rbac','security'))

    add('network-policy',PART,'NetworkPolicy හි සැබෑ ආරක්ෂාව','Policy manifests require an enforcing network implementation',
        p('Default-deny එකකින් ආරම්භ කර අවශ්‍ය traffic පමණක් allow කිරීම හොඳ අරමුණකි. එහෙත් Policy object එක API server එක පිළිගැනීමෙන් traffic නතර වන බව තහවුරු නොවේ. CNI / network implementation එක enforcement සහාය සැපයිය යුතුය.')+
        box('මෙම local cluster එකේ සීමාව','Default Kind CNI එක NetworkPolicy enforce නොකරයි. තවද chart එකේ release-wide internal allow rule සහ broad external-port egress rules ඇත. Strict per-service isolation තිබෙන බව කියන්න බැහැ. Production පෙර enforcing CNI මත rules පටුකර tests කරන්න.','warning')+
        table(['අවසර ලබාදිය යුතු path','උදාහරණය'],[
            ('DNS','අදාළ DNS Pods වෙත UDP / TCP 53.'),
            ('Public edge','Ingress controller namespace → web / gateway :8080.'),
            ('Service API dependency','Gateway → අවශ්‍ය downstream service port පමණක්.'),
            ('Database / broker','Owning services / migration Jobs → අදාළ endpoints.'),
            ('External providers','Approved OAuth, SMTP, model සහ webhook endpoints සඳහා සීමිත egress.')])+
        p('NetworkPolicies additive වේ: පටු allow එකක් එක් කිරීමෙන් පවතින පුළුල් allow එක අහෝසි නොවේ. නව rules පරීක්ෂා කරන විට පැරණි broad rule ඉවත් කිරීමේ migration එකත් සැලසුම් කරන්න.')+
        steps(['Stage cluster එක enforcing CNI සමඟ සකස් කරන්න.',
            'Expected-allowed connections සාර්ථකද පරීක්ෂා කරන්න.',
            'Expected-denied connections timeout / deny වෙනවාද තහවුරු කරන්න.',
            'DNS, migration hooks සහ provider egress නොකැඩෙන බව පරීක්ෂා කරන්න.']),('network',))

    add('observability',PART,'පද්ධතිය නිරීක්ෂණය කරන workflow එක','Logs, metrics, traces and user-visible service objectives',
        table(['සංඥාව','මැනිය යුතු දේ'],[
            ('User experience','Booking success rate, p95 latency, tracking freshness, notification delivery delay.'),
            ('Workloads','Pod readiness, restarts, OOM, CPU throttling, rollout duration.'),
            ('Database / Redis','Connections, query latency, lock waits, memory / evictions.'),
            ('Messaging','Outbox backlog, queue age/depth, unacked messages, DLQ growth.'),
            ('Assistant','SSE completion rate, provider errors, limit rejects, tool failure / denial rate.'),
            ('Platform','Node disk pressure, storage capacity, certificate expiry, backup age.')])+
        code('''kubectl --context kind-swiftdrop -n swiftdrop get pods -o wide
        kubectl --context kind-swiftdrop -n swiftdrop get events `
          --sort-by=.metadata.creationTimestamp
        kubectl --context kind-swiftdrop -n swiftdrop logs `
          deployment/gateway --tail=100''')+
        p('Production logs node / Pod lifetime එකට බැඳී නොතිබිය යුතුය. Central log backend, metrics collector සහ alert delivery path සකස් කර stage තුළ alert එකක් සැබැවින්ම ලැබෙනවාද පරීක්ෂා කරන්න. Chart හි observability templates තිබීමෙන් monitoring stack එක install වූ බව නොසලකන්න.')+
        box('SLO උදාහරණය','“Booking requests වලින් 99.9% සාර්ථකව අවසන් විය යුතුය” වැනි අරමුණක් කණ්ඩායමෙන් තීරණය කරන්න. මෙහි සංඛ්‍යාව ඉගැන්වීමේ උදාහරණයකි; project එකේ තහවුරු කළ SLA එකක් නොවේ.')+
        p('එකම request ID එක gateway → service → async correlation logs තුළ පසුපස යා හැකි වීම root cause සොයාගැනීමට උපකාරී වේ.'),('logs','rabbit'))

    add('verification',PART,'පරීක්ෂණ ස්ථර සහ ලැබුණු සාක්ෂි','A dated implementation record, not a new production certification',
        table(['පරීක්ෂණය','2026-11-16 repository record'],[
            ('Helm / schemas','Lint/render pass; chart regression cases 14; K8s 1.35 strict schema resources: local 64 / production 67; errors හෝ skips නැත.'),
            ('Java migrations','Process harness cases 4 pass; live database migration Jobs 7 සාර්ථකයි; නැවත ධාවනයත් සාර්ථකයි.'),
            ('Gateway / notification','Gateway security tests 18; notification targeted tests 17 pass.'),
            ('Assistant focused checks','Ruff / mypy සහ migration / checkpoint / SSE focused tests 12 pass.'),
            ('Ingress customer flow','Register/login, pricing, idempotent booking, public tracking, EMAIL SENT, cancellation, SSE / history pass.'),
            ('Restart recovery','Assistant rolling restart පසු history රැඳුණි; core parcel API available විය.')])+
        box('තවම සම්පූර්ණ නොවූ දේ','Final source එකෙන් අලුත්ම cluster එකක full rerun, role E2E, live Gemini, enforcing CNI tests, load/fault tests සහ backup restore exercise තව අවශ්‍යයි. පෙර full assistant suite එකේ passes 89ක්, live test skip එකක් සහ existing courier-tool registry expectation failure එකක් තිබුණි.','warning')+
        code('''.\\infra\\kubernetes\\scripts\\verify-cluster.ps1
        .\\infra\\kubernetes\\tests\\smoke-tests.ps1 -Workflow''')+
        p('Ready status එක infrastructure gate එකකි. Business success gate එක වෙනමය. Test evidence එකට source revision, image tag/digest, time, environment සහ exact outcome තබන්න. මෙම පොත නිර්මාණය කිරීමේදී live cluster එක නැවත deploy කර නැත.'))

    add('ci-validation',PART,'Pull request validation workflow','Catch manifest, migration and runtime regressions before publishing',
        p('<code>.github/workflows/kubernetes-validate.yml</code> එක relevant paths වෙනස් වූ pull requests සහ main branch pushes සඳහා validation කරයි. Runner එකට cluster credentials අවශ්‍ය නොවන checks ප්‍රමුඛ වේ.')+
        steps(['Helm install කර local / production chart lint සහ render කරන්න.',
            'PowerShell orchestration mocks මඟින් context, credentials, bootstrap සහ image-loader branches පරීක්ෂා කරන්න.',
            'Python chart regression tests සහ Java migration runner harness ධාවනය කරන්න.',
            'Gateway probe security සහ RabbitMQ event decoding regression tests ධාවනය කරන්න.',
            'Rendered manifests kubeconform මඟින් validate කර Trivy config scan සහ gitleaks secret scan කරන්න.'])+
        code('''helm lint infra/kubernetes/helm/swiftdrop
        .\\infra\\kubernetes\\tests\\helm-render-tests.ps1
        .\\infra\\kubernetes\\tests\\local-script-tests.ps1
        # Use a development Python environment with pytest and PyYAML.
        py -3.12 -m pytest infra/kubernetes/tests/test_chart.py -q''')+
        table(['Validation සීමාව','වැඩිදුර කළ යුතු දේ'],[
            ('CI uses ignore-missing-schemas','Skipped schemas වෙනම review කරන්න; CRDs සඳහා schemas සපයන්න. Recorded local strict run එකේ skips නොතිබුණි.'),
            ('No runtime cluster in this workflow','Fresh-cluster smoke සහ role workflows සඳහා වෙනම integration stage අවශ්‍යයි.'),
            ('Path filters','අදාළ runtime / shared-contract changes trigger වනවාද review කරන්න.')])+
        box('Branch protection','Checks ලියා තිබීමෙන් merge එක නවතින්නේ නැත. Repository settings තුළ required checks සහ review rules සකස් කළ යුතුය.'))

    add('release-cd',PART,'Image publishing සිට deployment දක්වා','Build once, scan, sign, approve and deploy',
        diagram('release-pipeline',[
            ('s',5,10,190,'Tag / manual trigger','v* or workflow_dispatch','blue'),
            ('b',235,10,190,'Build matrix','10 app images / SHA tags','teal'),
            ('p',465,10,190,'Publish + scan + sign','GHCR / Trivy / Cosign OIDC','gold'),
            ('a',465,115,190,'Production environment','Configure required reviewers','gold'),
            ('d',235,115,190,'Deploy runner','Projected short-lived SA token','blue'),
            ('v',5,115,190,'Helm rollout','Migrations / readiness / record','teal')],
            [('s','b'),('b','p'),('p','a'),('a','d'),('d','v')],200,'රූපය 16 · මෙම release workflow source එකේ ඇත; production publish / deployment මෙතෙක් තහවුරු කර නැත.')+
        p('<code>kubernetes-release.yml</code> හි <code>v*</code> tag push එක publish පසු deploy job එකට යයි. Manual dispatch එකේ deploy default false වේ. Production Environment reviewers සහ tag restrictions GitHub settings තුළ සකස් කිරීම අත්‍යවශ්‍යයි.')+
        table(['භාගය','වත්මන් workflow'],[
            ('Artifacts','Git SHA tags, BuildKit provenance සහ SBOM attach කරයි.'),
            ('Scan / sign','Published digest එක scan කර Cosign OIDC signing කරයි.'),
            ('Credentials','Deployer Kubernetes Pod එකේ projected short-lived token; namespace RBAC.'),
            ('Rollout','Helm rollback-on-failure / atomic සහ wait-for-jobs; release status record කරයි.')])+
        box('තව harden කළ යුතුය','Deployed manifests දැනට SHA tags reference කරයි. Digest promotion, signature admission enforcement, action digest pinning සහ artifact policy review තව අවශ්‍යයි. Signature publish වීමෙන් deployment-time verification ස්වයංක්‍රීයව සිදු නොවේ.','warning'),('oidc',))

    add('production',PART,'Production යාමට පෙර අවසාන gates','The example values file is a starting point',
        table(['Gate එක','Go-live පෙර අවශ්‍ය සාක්ෂිය'],[
            ('Maintained edge','ingress-nginx වෙනුවට maintained implementation; TLS සහ streaming regression tests.'),
            ('Data services','Managed DB / Redis / RabbitMQ / object storage connectivity, distinct credentials සහ HA design.'),
            ('Secrets / identity','Secret sync, signing-key overlap rotation, revoked sessions සහ least privilege.'),
            ('Network isolation','Enforcing CNI; strict service allow matrix; allowed සහ denied tests.'),
            ('Application correctness','Merchant / courier / dispatch / admin E2E; assistant runtime integration සහ shared confirmation state.'),
            ('Supply chain','Reviewed vulnerabilities, approved digests, signature verification සහ rollback artifacts.'),
            ('Operations','Dashboards, alerts, on-call ownership, RPO/RTO සහ tested restore.'),
            ('Capacity / failure','Load targets, node failure, dependency outage, rolling update සහ recovery exercise.')])+
        p('Production example එකේ replicas සහ TLS flags තිබුණත් DNS, certificate issuer, secret provider, metrics API සහ external data services ස්වයංක්‍රීයව සම්පූර්ණ නොවේ. <code>*.example.*</code> values executable production settings නොවේ.')+
        box('Local success එකේ අර්ථය','මෙතෙක් ලැබුණු ප්‍රතිඵලය සාර්ථක local implementation සහ customer workflow verification එකකි. එය production readiness sign-off එකක් ලෙස භාවිත නොකරන්න.','warning')+
        p('Stage environment එක production topology එකට හැකි තරම් සමාන කර, build කළ image එකම වෙනස් environment configuration සමඟ promote කරන්න. Approval එකට release evidence සහ recovery plan සූදානම්ව තිබිය යුතුය.'))

    add('rollback',PART,'Rolling update සහ rollback workflow','Preserve service and schema compatibility across revisions',
        steps(['අලුත් release එකට පෙර current image identities, Helm revision සහ backup status සටහන් කරන්න.',
            'Backward-compatible migrations සාර්ථක වූ පසු නව replicas rollout කරන්න.',
            'Readiness, error rate, latency සහ business smoke checks නිරීක්ෂණය කරන්න.',
            'Failure එකකදී evidence preserve කර automatic rollback ප්‍රතිඵලය බලන්න.',
            'Manual rollback අවශ්‍ය නම් app-bearing revision එක තෝරා database compatibility තහවුරු කරන්න.'])+
        code('''helm --kube-context kind-swiftdrop history swiftdrop -n swiftdrop
        helm --kube-context kind-swiftdrop status swiftdrop -n swiftdrop
        # Replace REVISION after reviewing history and schema compatibility.
        helm --kube-context kind-swiftdrop rollback swiftdrop REVISION `
          -n swiftdrop --wait --timeout 15m
        .\\infra\\kubernetes\\scripts\\verify-cluster.ps1
        .\\infra\\kubernetes\\tests\\smoke-tests.ps1 -Workflow''')+
        box('Schema rollback නොවේ','Helm rollback එක workload specifications ආපසු ගෙනයයි. Applied Flyway / Alembic schema වෙනස්කම් හෝ සිදු වූ business transactions අහෝසි නොකරයි. Expand / contract migrations සහ compatible old image එක අවශ්‍ය වන්නේ එබැවිනි.','warning')+
        p('Initial dependency-only bootstrap revision එකට rollback වීමෙන් application Deployments ඉවත් විය හැක. Functional release එකක recovery සඳහා blindly revision 1 තෝරා නොගන්න. Failed migration Job logs cleanup වීමට පෙර අවශ්‍ය redacted evidence තබාගන්න.'),('deployment','rollback'))

    from chapters_recovery import populate as recovery
    recovery()
