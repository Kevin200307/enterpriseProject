from book import add, p, h, code, box, table, steps, ul, diagram

PART='05 / ගැටලු විසඳීම සහ ප්‍රායෝගික මෙහෙයුම්'

def populate():
    add('troubleshooting',PART,'ගැටලුවේ මූල හේතුව සොයායන්න','Start with status and evidence before changing resources',
        table(['ලක්ෂණය','පළමු පරීක්ෂණය / හේතු'],[
            ('Pending','describe Pod → FailedScheduling, resources, node taints, unbound PVC.'),
            ('ImagePullBackOff','Image name/tag, import, registry auth, TLS, DNS සහ architecture.'),
            ('CrashLoopBackOff','Previous logs, exit reason, missing configuration, OOM සහ application startup error.'),
            ('Running but NotReady','Probe path/timeout, auth policy, dependency readiness සහ startup budget.'),
            ('Ingress 404','Host / path / IngressClass සහ app endpoint එක වෙන්කර පරීක්ෂා කරන්න.'),
            ('Ingress 502 / 503','Service port, EndpointSlice readiness සහ controller connectivity.'),
            ('401 / 403','Token validity, issuer / audience, role සහ resource ownership.'),
            ('Booking success, no email','Outbox → broker → converter/consumer → notification status → SMTP.')])+
        code('''kubectl --context kind-swiftdrop -n swiftdrop get pods -o wide
        kubectl --context kind-swiftdrop -n swiftdrop describe pod POD_NAME
        kubectl --context kind-swiftdrop -n swiftdrop logs POD_NAME `
          --previous --tail=100
        kubectl --context kind-swiftdrop -n swiftdrop get events `
          --sort-by=.metadata.creationTimestamp''')+
        box('වෙනස්කම් එකවර එකක්','Restart සියල්ල, Secret replace කිරීම සහ PVC delete කිරීම එකවර කළහොත් හේතුවත් evidence එකත් අහිමි වේ. පළමුව hypothesis එකක් තබා එය තහවුරු කරන check එක කරන්න.','warning'))

    add('image-dns-errors',PART,'Image import, TLS සහ DNS errors','Use the project workarounds without weakening registry security',
        table(['Error එක','නිර්දේශිත ක්‍රියාවලිය'],[
            ('content digest ... not found','Modern Docker platform-filtered archive loader භාවිත කරන්න; image එක අලුතින් build විය යුතුම බව නොසිතන්න.'),
            ('node pull TLS failure','Docker Desktop හරහා pinned dependency pull කර SwiftDrop nodes වෙත import කරන්න.'),
            ('DNS timeout','Host, Docker engine, Kind node සහ CoreDNS path එක වෙන්කර පරීක්ෂා කරන්න.'),
            ('exec format error','Image platform සහ node architecture එක ගැළපේද බලන්න.'),
            ('ImagePullBackOff after import','Imported cluster name, exact tag සහ imagePullPolicy බලන්න.')])+
        code('''docker info --format '{{.OSType}}/{{.Architecture}}'
        docker image inspect "swiftdrop/gateway:$releaseTag" `
          --format '{{.Os}}/{{.Architecture}}'
        .\\infra\\kubernetes\\scripts\\load-images.ps1 -Tag $releaseTag
        kubectl --context kind-swiftdrop -n kube-system get pods''')+
        p('Docker bridge gateway DNS සම්බන්ධතාව අසාර්ථක බව තහවුරු වූ විට පමණක් project DNS helper එක සලකා බලන්න. එය SwiftDrop node ownership පරීක්ෂා කර එම nodes සහ එහි CoreDNS පමණක් වෙනස් කරයි.')+
        code('''# Example only: choose resolver IPs reachable and allowed on your network.
        .\\infra\\kubernetes\\scripts\\configure-local-dns.ps1 `
          -DnsServers @('8.8.8.8','8.8.4.4')''')+
        box('එකම fix එක හැම තැනම නොයොදන්න','Public DNS addresses සමහර networks වල block වී ඇත. Proxy / corporate CA අවශ්‍ය නම් එය නිසි trust configuration හරහා සකස් කරන්න; TLS verification අක්‍රිය කිරීම සාමාන්‍ය විසඳුම නොවේ.','warning'),('kind-issues','save'))

    add('data-errors',PART,'Database සහ message failures','Diagnose ownership, compatibility and replay safety',
        table(['සිදුවීම','පරීක්ෂා කර නැවත යථා තත්ත්වයට ගන්න'],[
            ('Migration Job failed','Job logs, database DNS/port, credentials reference, schema history සහ deadline බලන්න.'),
            ('App login to DB fails','Secret key name සහ database user mapping බලන්න. Existing PVC සමඟ password අහඹු ලෙස මාරු නොකරන්න.'),
            ('No tracking projection','Outbox unpublished backlog, RabbitMQ bindings, consumer logs සහ Redis access බලන්න.'),
            ('DLQ grows','Event schema / timestamp converter, missing fields, authorization සහ poison-message හේතුව සොයන්න.'),
            ('Duplicate side effects','Event deduplication key සහ downstream idempotency behaviour පරීක්ෂා කරන්න.'),
            ('MinIO upload error','Bucket existsද, endpoint / credentials / browser public endpoint ගැළපේද බලන්න.')])+
        steps(['Failed event එකේ ID, schema version සහ sanitized error එක තබාගන්න.',
            'Consumer fix එක regression test එකකින් තහවුරු කරන්න.',
            'Owning event / delivery පමණක් controlled replay කර duplicate prevention බලන්න.',
            'Tracking, notification හෝ webhook final state නැවත කියවා backlog අඩුවනවාද මැන බලන්න.'])+
        box('DLQ purge එක recovery නොවේ','Messages සියල්ල මකාදැමීමෙන් error rate අඩුවුණත් business events අහිමි විය හැක. Unrelated messages purge / replay නොකර ownership සහ replay plan තහවුරු කරන්න.','warning')+
        p('Flyway validation failure එකකට schema history table අතින් වෙනස් කිරීම හෝ checksum mismatch නොසලකා හැරීමෙන් පෙර migration source, deployed artifact සහ database snapshot එක සසඳන්න. නිවැරදි කිරීම versioned change එකක් ලෙස review කරන්න.'))

    add('backup',PART,'Backup සිට verified restore දක්වා','RPO, RTO and service-owned recovery',
        diagram('restore-flow',[
            ('b',5,10,190,'Verified backup','Encrypted / access controlled','gold'),
            ('r',235,10,190,'Restore a new target','Keep original for diagnosis','blue'),
            ('m',465,10,190,'Validate schemas','Owning migrations / versions','teal'),
            ('s',465,115,190,'Service smoke','Data + object consistency','blue'),
            ('c',235,115,190,'Cut over','Secret reference + rollout','teal'),
            ('a',5,115,190,'Audit recovery','Measured RPO / RTO','gold')],
            [('b','r'),('r','m'),('m','s'),('s','c'),('c','a')],200,'රූපය 17 · Backup file එකක් තිබීම සහ restore කළ හැකි වීම වෙන වෙනම සාක්ෂි දෙකකි.')+
        p('<strong>RPO</strong> යනු අහිමි විය හැකි data කාල පරාසයයි. <strong>RTO</strong> යනු සේවාව නැවත ලබාගැනීමට ඉඩදෙන කාලයයි. මේ අගයන් business team එකෙන් තීරණය කර recovery drill එකකින් මැන බලන්න.')+
        steps(['Owning service writes නවතන්න හෝ coordinated maintenance mode භාවිත කරන්න.',
            'Backup time, integrity, encryption keys සහ target ownership තහවුරු කරන්න.',
            'හැකි නම් නව database instance එකකට restore කර schema compatibility පරීක්ෂා කරන්න.',
            'Object metadata / MinIO proofs / events සහ DB state consistency සලකා smoke tests කරන්න.',
            'Secret reference මාරු කර අදාළ workload පමණක් restart කරන්න; පරණ target එක verification අවසන් වනතෙක් තබාගන්න.'])+
        box('මෙතෙක් නොකළ check එක','Local assistant restart එක data durability පෙන්වයි. එය off-cluster backup restore හෝ disaster recovery rehearsal එකකට සමාන නොවේ. Production restore exercise තව අවශ්‍යයි.','warning'),('postgres','storage'))

    add('node-recovery',PART,'Pod සහ node failure recovery','Controllers, disruption budgets and storage topology',
        table(['Failure එක','අපේක්ෂිත හැසිරීම / සීමාව'],[
            ('Container crash','Restart policy ප්‍රතිචාර දක්වයි; previous logs හේතුව පෙන්විය හැක.'),
            ('Deployment Pod removed','ReplicaSet replacement Pod එකක් නිර්මාණය කරයි.'),
            ('Node unavailable','Pods නැවත schedule විය හැකි නමුත් detection delay, capacity සහ storage attachment සීමා බලපායි.'),
            ('Local Kind host fails','Node containers දෙකම එකම VM / host එකේ නිසා host failure domain එක බෙදාගනී.'),
            ('Planned maintenance','Drain / eviction flow සහ PDB constraints භාවිත කරන්න.')])+
        p('PDB එක voluntary evictions සඳහා availability සීමාවක් ලබාදෙයි. එය node crash එකක් නවත්වන්නේ නැත. Direct Pod deletion හෝ Deployment update strategy එක PDB එකෙන් සම්පූර්ණයෙන් පාලනය නොවේ; rollout settings වෙනම බලපායි.')+
        code('''# Read-only preparation.
        kubectl --context kind-swiftdrop get nodes
        kubectl --context kind-swiftdrop -n swiftdrop get pdb,pods -o wide
        # Planned lab maintenance only, after capacity/storage review.
        kubectl --context kind-swiftdrop drain NODE_NAME --ignore-daemonsets
        kubectl --context kind-swiftdrop uncordon NODE_NAME''')+
        box('Force flags වලට පෙර','PVC attachment, singleton dependencies සහ data integrity තහවුරු නොකර force delete / force drain නොකරන්න. Local host-path storage වෙනත් node එකකින් ඒ ආකාරයටම ලබාගත නොහැකි විය හැක.','warning'),('drain','pdb'))

    add('gateway-api',PART,'Gateway API වෙත migration සැලැස්ම','An illustrative route; install a supported controller and CRDs first',
        p('මෙය අනාගත migration සඳහා teaching example එකකි. Repository chart එකේ දැනට deploy වී ඇති Ingress එකට ආදේශයක් ලෙස මෙම file එක පමණක් apply නොකරන්න. Controller එක, GatewayClass එක, Gateway listener එක සහ TLS policy එක කලින් සකස් කළ යුතුය.')+
        code('''apiVersion: gateway.networking.k8s.io/v1
        kind: HTTPRoute
        metadata:
          name: swiftdrop-routes
          namespace: swiftdrop
        spec:
          parentRefs:
            - name: swiftdrop-edge
          hostnames: ["swiftdrop.example.com"]
          rules:
            - matches:
                - path: {type: PathPrefix, value: /api}
                - path: {type: PathPrefix, value: /ws}
              backendRefs:
                - {name: gateway, port: 8080}
            - matches:
                - path: {type: PathPrefix, value: /}
              backendRefs:
                - {name: web, port: 8080}''','YAML · illustrative migration fragment')+
        steps(['Maintained implementation එකේ conformance, support සහ deployment needs සසඳන්න.',
            'Stage තුළ side-by-side edge endpoint සකස් කර HTTP routing / TLS පරීක්ෂා කරන්න.',
            'Nginx-specific annotations වෙනුවට controller-supported streaming / timeout settings දක්වන්න.',
            'SSE, WebSocket, uploads, redirects සහ authorization regression tests පසු traffic මාරු කරන්න.'])+
        box('පද දෙකේ වෙනස','මෙහි <code>backendRefs: gateway</code> යනු Spring application Service එකයි. Kubernetes Gateway API resource එකේ නම <code>swiftdrop-edge</code> ය. දෙකේ වගකීම් වෙනස්ය.'),('gateway','retirement'))

    add('fault-load',PART,'Fault සහ load testing සැලැස්ම','Stage-only drills with measurable pass and recovery criteria',
        table(['අභ්‍යාසය','සාර්ථකත්වය ලෙස සලකන්නේ'],[
            ('Assistant rolling restart','Conversation history රැඳේ; core parcel API සාර්ථකයි. මෙහි local test එක කළා.'),
            ('RabbitMQ unavailable','Core transaction policy පැහැදිලියි; outbox backlog නිරීක්ෂණය කර broker recover පසු deduplicated delivery.'),
            ('Redis unavailable','Tracking / limits සඳහා expected degradation; app crash cascade නොවේ.'),
            ('DB connection failure','Bounded timeout; request hang නොවේ; recover පසු correct transaction state.'),
            ('Duplicate / reordered events','Projection සහ notifications duplicate / stale overwrite නොකරයි.'),
            ('Load ramp','Target concurrency දක්වා p95/p99, error rate, CPU, memory, pools සහ queue lag මැනේ.'),
            ('Node maintenance','PDB, remaining capacity සහ storage constraints සලකා availability මැනේ.'),
            ('Backup restore','Verified data සහ measured RPO/RTO; object references ද නිවැරදියි.')])+
        p('Test එකට පෙර expected blast radius, recovery action සහ abort threshold ලියන්න. සැබෑ customer data වෙනුවට synthetic accounts / parcels භාවිත කරන්න. Local laptop එකේ අගයන් cloud sizing සඳහා සෘජුව ගණනය නොකරන්න.')+
        box('Load acceptance උදාහරණය','Concurrent users ගණන, request mix, payload sizes සහ test duration එක කණ්ඩායම තීරණය කළ යුතුය. “Requests ගොඩක් යැවුවා” යන්න ප්‍රමාණවත් evidence එකක් නොවේ.')+
        p('Test report එක source revision, image identities, configuration, test window, observed failures සහ recovery outcome සමඟ තබන්න. සියලු drills මෙම project එකේ මෙතෙක් සිදුකර ඇතැයි නොසලකන්න.'))

    add('operator-runbook',PART,'දිනපතා operator workflow එක','Release identity → health → business signal → evidence',
        steps(['Current context, namespace සහ Helm release revision සනාථ කරන්න.',
            'Deployment / StatefulSet availability, restart count, events සහ failed Jobs බලන්න.',
            'Public web path සහ authorized critical API request එක තහවුරු කරන්න.',
            'Queue lag, notification delay, DB saturation සහ object capacity වැනි business-related signals බලන්න.',
            'Incident එකක් නම් change එකට පෙර redacted evidence ගන්න; එකවර සීමිත mitigation එකක් කරන්න.',
            'Recovery පසු customer flow සහ අදාළ role-specific workflow නැවත පරීක්ෂා කර timeline එක සටහන් කරන්න.'])+
        code('''kubectl config current-context
        helm --kube-context kind-swiftdrop status swiftdrop -n swiftdrop
        kubectl --context kind-swiftdrop -n swiftdrop get deploy,sts,pods
        kubectl --context kind-swiftdrop -n swiftdrop get jobs,pvc,ingress
        .\\infra\\kubernetes\\scripts\\verify-cluster.ps1''')+
        table(['Evidence record','රඳවාගත යුතු අගයන්'],[
            ('Release','Revision, source SHA, image digests / tags සහ environment.'),
            ('Incident','Time, request ID, error code, impacted workflow සහ sanitized logs.'),
            ('Action','කළ වෙනස, rollback target සහ කවුද භාරගත්තේ යන්න.'),
            ('Recovery','Exact smoke result, backlog recovery සහ නැවත ඇතිවීම වැළැක්වීමේ කාර්යය.')])+
        box('Workflow smoke එක writes කරයි','<code>-Workflow</code> synthetic account සහ parcel සාදන නිසා දිනපතා read-only health check එකට සමාන නොවේ. එය local environment එකට සීමා කර ඇත.'))

    add('exercises',PART,'ප්‍රායෝගික අභ්‍යාස සහ viva ප්‍රශ්න','Explain the reason behind each operational choice',
        table(['අභ්‍යාසය','පෙන්විය යුතු ප්‍රතිඵලය'],[
            ('Request tracing','Browser → Ingress → gateway → parcel → DB → outbox → consumers යන ගමන අඳින්න.'),
            ('Readiness reasoning','Pod Running වුවත් user request නොලැබිය හැකි හේතු තුනක් දක්වන්න.'),
            ('Idempotency','එකම booking payload/key දෙවරක් යවා එකම ID ලැබෙන බව පෙන්වන්න.'),
            ('Eventual consistency','Booking response පසු projection එකට bounded polling අවශ්‍ය වන්නේ ඇයි පැහැදිලි කරන්න.'),
            ('Probe design','Gateway aggregate health එක liveness සඳහා භාවිත නොකළේ ඇයි දක්වන්න.'),
            ('Migration safety','App rollback එකකින් DB schema rollback නොවන අවස්ථාවක් පැහැදිලි කරන්න.'),
            ('Network policy','Manifest render pass වීම enforcement proof නොවන්නේ ඇයි පෙන්වන්න.'),
            ('Assistant correctness','Conversation persistence සහ pending-operation persistence වෙනස් බව කියන්න.'),
            ('Production handover','Unfinished gates සඳහා owner සහ acceptance criteria ලියන්න.')])+
        h('පිළිතුරු සඳහා මාර්ගෝපදේශය')+
        p('හොඳ පිළිතුරකට object එකේ නම පමණක් නොව failure mode එක, තෝරාගත් configuration එක සහ ඒ තීරණය තහවුරු කරන test එකත් ඇතුළත් වේ. උදාහරණයක් ලෙස “RabbitMQ ඇත” කියනවා වෙනුවට outbox, at-least-once delivery, consumer deduplication සහ DLQ recovery සම්බන්ධ කර පැහැදිලි කරන්න.')+
        box('තමන්ගේ lab සටහන්','පළමු install time, cold-start time, observed failures සහ fixes ලියා තබන්න. Default values අන්ධව copy කරනවාට වඩා තම පරිසරයේ evidence එකෙන් තීරණ ගන්න.'))

    add('command-reference',PART,'ඉක්මන් command reference','Read-only inspection first; replace uppercase placeholders',
        code('''# Context and platform
        kubectl config get-contexts
        kubectl --context kind-swiftdrop get nodes

        # Workloads and routing
        kubectl --context kind-swiftdrop -n swiftdrop get deploy,sts,pods
        kubectl --context kind-swiftdrop -n swiftdrop get svc,ingress
        kubectl --context kind-swiftdrop -n swiftdrop get endpointslices

        # Events and diagnostic logs
        kubectl --context kind-swiftdrop -n swiftdrop describe pod POD_NAME
        kubectl --context kind-swiftdrop -n swiftdrop logs POD_NAME --tail=100
        kubectl --context kind-swiftdrop -n swiftdrop logs POD_NAME --previous

        # Release and storage
        helm --kube-context kind-swiftdrop history swiftdrop -n swiftdrop
        helm --kube-context kind-swiftdrop status swiftdrop -n swiftdrop
        kubectl --context kind-swiftdrop -n swiftdrop get jobs,pvc

        # Safe local verification
        .\\infra\\kubernetes\\scripts\\verify-cluster.ps1
        .\\infra\\kubernetes\\tests\\smoke-tests.ps1''')+
        p('PowerShell හි දිගු commands සඳහා backtick අගට පසු spaces නොතබන්න. මෙම PDF හි code එක visual line wrap විය හැකි නිසා editable HTML / Python source එකෙන් copy කිරීම පහසුය. Linux shell සඳහා PowerShell syntax සෘජුව භාවිත නොකරන්න.')+
        box('වටිනා command එකක්','<code>kubectl describe</code> output එකේ Events කොටස Pending, image pull සහ probe problems ඉක්මනින් වෙන්කර හඳුනාගැනීමට බොහෝවිට ප්‍රමාණවත් මුල් සාක්ෂියක් ලබාදෙයි.'))
