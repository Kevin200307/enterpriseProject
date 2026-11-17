from book import add,p,h,code,box,table,steps,diagram

PART='03 / Code සිට running environment එක දක්වා'

def populate():
    add('configuration',PART,'Configuration කොහෙන් එන්නේද?','Defaults, environment injection and runtime evidence',
        p('එකම image එක environments කිහිපයක ධාවනය කිරීමට code සහ configuration වෙන්කර තබයි. එහෙත් environment variable නමක් ලියා තිබීමෙන් ඒක application එක කියවන බව තහවුරු නොවේ. Producer සහ consumer දෙකේ names සසඳන්න.')+
        table(['Layer එක','ඔබ බලන තැන'],[
            ('Java defaults / binding','<code>application.yaml</code> placeholders සහ configuration classes.'),
            ('Python settings','Assistant Settings model; feature flags සහ required URLs.'),
            ('Helm values','Chart defaults + environment values + command overrides.'),
            ('Rendered Pod','ConfigMap / Secret references, ports සහ actual container args.'),
            ('Browser runtime','<code>config.js</code> → VITE fallback → local default.'),
            ('Running process','Resolved behaviour; credentials නොපෙන්වා metadata සහ safe endpoints මඟින් තහවුරු කරන්න.')])+
        code('''helm template swiftdrop infra/kubernetes/helm/swiftdrop `
          -n swiftdrop `
          -f infra/kubernetes/helm/swiftdrop/values-local.yaml''')+
        box('Config change එකක් release එකක් විය හැක','Secret / ConfigMap environment value වෙනස් කළහොත් දැනට ධාවනය වන process එකේ environment එක සාමාන්‍යයෙන් මාරු නොවේ. අදාළ consumers rollout කර behaviour තහවුරු කරන්න. Browser config.js එකද startup-generated වේ.')+
        p('Rendered manifests archive කිරීමට පෙර sensitive fields තිබේද බලන්න. Book source ledger එක actual Secret values හෝ .env files capture නොකරයි.'),('configmap','secret'),('R03','R06','R29','R35'))

    add('build-artifacts',PART,'Build එක ඇත්තටම සාදන්නේ මොනවාද?','Source, compiled artifacts, images and running containers',
        diagram('build-artifacts',[
            ('s',5,10,190,'Source code','Git working tree + dependencies','blue'),('a',235,10,190,'Build artifacts','JAR / dist / Python package','teal'),('i',465,10,190,'OCI image','Runtime + application files','gold'),
            ('r',235,120,190,'Running process','Image + config + data + network','blue')],
            [('s','a'),('a','i'),('i','r')],205,'රූපය 15 · Source commit එක, image tag එක සහ running process එක සමාන identity එකක් නොවේ.')+
        table(['Stack එක','Build / runtime මාරුව'],[
            ('Java','Maven compile / tests / packaging → executable JAR සහ libraries → Java 21 runtime.'),
            ('React / TypeScript','Typecheck / Vite build → static assets → nginx → browser execution.'),
            ('Python','Pinned dependencies සහ app package → Python runtime; migrations වෙනම command එකක්.'),
            ('Shared contracts','Common library reactor එකෙන් build වේ; වෙනස්කම් consuming services වෙත බලපායි.')])+
        code('''# Build one Java service and the reactor modules it needs.
        .\\mvnw.cmd -pl services/parcel-service -am package
        # Local full-image build: choose a fresh tag.
        $tag = "arch-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
        .\\infra\\kubernetes\\scripts\\build-images.ps1 -Tag $tag''')+
        box('ගැටලුවක් වෙන්කර හඳුනාගන්න','Source compile fail, image build fail සහ container startup fail වෙන වෙනම අදියර තුනකි. Log එකේ පළමු අසාර්ථක අදියර සොයන්න.'),('containers',),('R01','R02','R33','R34'))

    add('supply-chain',PART,'Versions සහ software supply chain','Know which code and dependencies you are operating',
        table(['Artifact / evidence','ඔබට උත්තර දෙන ප්‍රශ්නය'],[
            ('Source SHA / working-tree hash','සෑදුණේ කුමන code එකෙන්ද? Uncommitted changes ඇතුළත්ද?'),
            ('Lockfiles / Maven dependencies','කවර third-party versions ලබාගත්තාද?'),
            ('Image digest','ඇත්තටම deploy කළ bytes කවර artifact එකද?'),
            ('SBOM','Image එකේ අඩංගු dependencies මොනවාද?'),
            ('Provenance','Artifact එක සාදන builder / source context ගැන attestation එක.'),
            ('Signature / policy','කවුරු sign කළාද සහ deployment එක එය verify කරනවාද?'),
            ('Vulnerability review','Known findings, exploitability සහ accepted remediation කුමක්ද?')])+
        p('Tag එකක් immutable වීමට registry policy එකත් අවශ්‍ය විය හැක. <code>latest</code> හෝ නැවත භාවිත කරන dev tag එකකින් incident එකේ exact image එක හඳුනාගැනීම අමාරු වේ. Release workflow එක Git SHA tags publish කර digest මත scan / sign කරයි.')+
        box('වත්මන් gap එක','Deployment manifests දැනට SHA tags reference කරයි. Digest promotion සහ signature admission enforcement වෙනම hardening කාර්යයන්ය. Scan pass වීමෙන් අවදානම් සම්පූර්ණයෙන් නැති බවක් නොකියයි.')+
        p('මෙම පොතේ source map එක reviewed files 42ක SHA-256 hashes තබයි. Current Git HEAD එක පමණක් uncommitted implementation files හඳුනාගැනීමට ප්‍රමාණවත් නොවන්නේ එබැවිනි.'),evidence=('R01','R33','R34','R41','R42'))

    add('environments',PART,'Compose, Kind සහ production වෙන්කර බලන්න','Keep the application contract while changing the hosting model',
        table(['Environment එක','භාවිතය / සීමාව'],[
            ('Docker Compose','Single-machine development. Service DNS, port mappings සහ volumes Compose එකෙන් සකස් වේ.'),
            ('Kind Kubernetes','Docker තුළ local nodes. Helm resources, migration hooks සහ ingress behaviour පරීක්ෂා කරයි.'),
            ('Production cluster','Failure domains, managed data services, maintained edge, TLS, isolation සහ operations අවශ්‍යයි.'),
            ('Unit / integration tests','Synthetic / isolated dependency behaviour; production network topology එකට සමාන නොවිය හැක.')])+
        p('Compose gateway host port 8081 සහ Vite dev port 5173 වැනි defaults local development සඳහාය. Kind ingress URL එක 8088 ය. Internal service port 8080 තවමත් එලෙසම විය හැක. Browser configuration එක environment එකට අනුව වෙනස් විය යුතුය.')+
        code('''docker version
        kind get clusters
        kubectl config get-contexts
        kubectl --context kind-swiftdrop get nodes''')+
        box('Local node දෙකේ සීමාව','Kind control-plane සහ worker දෙකම එකම Docker Desktop VM / host එකේය. Laptop failure එකකදී දෙකම නැතිවිය හැක. මෙය production high-availability topology එකකට සමාන නොවේ.')+
        p('මෙම workspace එකේ වෙනත් clusters ද ඇත. SwiftDrop commands සඳහා <code>kind-swiftdrop</code> context සහ <code>swiftdrop</code> namespace පැහැදිලිව දක්වන්න.'),evidence=('R03','R06','R35','R42'))

    add('deployment-view',PART,'Application map එක Kubernetes වෙත ගැළපීම','A workload contract becomes controllers, Services and storage',
        table(['Application අවශ්‍යතාව','Kubernetes representation'],[
            ('Replaceable stateless app','Deployment → ReplicaSet → Pods.'),
            ('Stable service discovery','ClusterIP Service → Ready Pod endpoints.'),
            ('Local stateful dependency','StatefulSet + PVC; backup / replication වෙනමයි.'),
            ('Schema initialization','Bounded Job / Helm hook.'),
            ('Public HTTP route','Ingress + controller; production migration to maintained implementation අවශ්‍යයි.'),
            ('Config / credentials','ConfigMap සහ existing Secret references.'),
            ('Availability / scale','Probes, resource requests, HPA, topology සහ PDB settings.')])+
        p('Kubernetes object එකක් application architecture එකේ business ownership වෙනස් නොකරයි. Parcel database එක තවම parcel service අයිතියේය. Pods දෙකකට scale කළ පමණින් process-local data shared state බවට පත් නොවේ.')+
        code('''kubectl --context kind-swiftdrop -n swiftdrop get deploy,sts
        kubectl --context kind-swiftdrop -n swiftdrop get svc,ingress
        kubectl --context kind-swiftdrop -n swiftdrop get pvc,jobs''')+
        box('වැඩිදුර local පියවර','පෙර සෑදූ Kubernetes A–Z පොතේ build → load → deploy → verify commands සම්පූර්ණයෙන් ඇත. මෙම පොතෙන් එම resources අවශ්‍ය වන්නේ ඇයි සහ failures පසුපස යන්නේ කෙසේද පැහැදිලි කරයි.'),('deployment','stateful','service'),('R35','R36','R37'))

    add('startup-migrations',PART,'Startup order සහ schema compatibility','Data readiness is part of release orchestration',
        diagram('startup-order',[
            ('d',5,10,190,'Dependencies','DB / Redis / broker / bucket','gold'),('m',235,10,190,'Schema Jobs','6 Flyway + assistant migration','blue'),('a',465,10,190,'Application rollout','Runtime replicas','teal'),
            ('v',235,120,190,'Verification','Health + user journeys','blue')],
            [('d','m'),('m','a'),('a','v')],205,'රූපය 16 · පළමු local install එක dependency-only bootstrap පසු full upgrade කරයි.')+
        p('Schemas නැති database එකකට replicas කිහිපයක් එකවර migrations කරන්න යැවීම වෙනුවට controlled Job එකක් ධාවනය කරයි. Java Flyway runner එක full application server හෝ listeners start නොකර schema work පමණක් කරයි. Assistant Job එක Alembic පසු checkpoint schema සකස් කරයි.')+
        table(['Gate එක','අවශ්‍යතාව'],[
            ('Before Job','DB connectivity සහ matching credentials.'),
            ('Job execution','Bounded deadline, nonzero failure සහ logs.'),
            ('Runtime','Schema setup replicas තුළ නැවත නොකිරීම.'),
            ('Rollback compatibility','New schema සමඟ old app version එක ක්‍රියා කළ හැකිද?')])+
        box('Expand / contract උදාහරණය','පළමුව නව optional column එක add කරන්න. Code එක දෙවර්ගයට ගැළපෙන ලෙස release කරන්න. Data backfill කර readers මාරු කරන්න. පරණ field එක ඉවත් කරන්නේ compatible releases පසුය. ඒක එක් destructive migration එකකට වඩා recover කළ හැකි ක්‍රමයකි.'),('hooks','flyway','alembic'),('R36','R37'))

    add('secrets-tls',PART,'Credentials, certificates සහ rotation','Treat changes as coordinated consumer updates',
        table(['රහස / trust material','හිමිකම සහ rotation concern'],[
            ('DB / Redis / broker credentials','Secret provider value සහ server account එක ගැළපිය යුතුය; පවතින data අහඹුව orphan නොකරන්න.'),
            ('JWT signing key','Private key identity තුළ; public keys overlap publish කර token lifetimes සලකා rotation කරන්න.'),
            ('Webhook shared secret','Sender / receiver දෙකම coordinated update අවශ්‍යයි.'),
            ('TLS certificate','DNS hostname, trust chain, expiry සහ controller-managed renewal.'),
            ('Gemini / OAuth keys','Approved provider scope, quota සහ only required workload injection.')])+
        p('Secret value වෙනස් කිරීමෙන් process එක පරණ connection pool එකම භාවිත කළ හැක. පළමුව provider / server update, synchronization, consumer rollout සහ connectivity check අනුපිළිවෙළ තීරණය කරන්න. Incident අතර data wipe කිරීම rotation strategy එකක් නොවේ.')+
        box('Local convenience සීමාව','Local generator එක credentials preserve කරයි. Java databases සඳහා වෙනස් users තිබුණත් password reuse ඇත. Production සඳහා distinct credentials සහ secret synchronization පරීක්ෂණ අවශ්‍යයි.')+
        p('TLS terminate වන තැන අඳින්න: browser → edge, edge → gateway, service → managed DB යන connections එකම encryption policy භාවිත නොකළ හැක. Public HTTPS තිබීමෙන් internal paths සියල්ල encrypted බව නොසිතන්න.'),('secret',),('R08','R35','R37'))

    add('cloud-iac',PART,'Cloud topology සහ Infrastructure as Code','A proposed production view, not provisioned infrastructure',
        diagram('cloud-topology',[
            ('u',225,0,210,'Public clients','DNS + HTTPS','teal'),('e',225,85,210,'Public edge','Maintained LB / gateway + TLS','blue'),
            ('a',5,180,300,'Private application nodes','Services / controlled ingress','blue'),('d',355,180,300,'Managed private data','Postgres / Redis / broker / objects','gold'),
            ('o',225,285,210,'Operations plane','CI identity / monitoring / backups','teal')],
            [('u','e'),('e','a'),('a','d'),('o','a'),('o','d')],355,'රූපය 17 · උදාහරණ design එකකි. Repository තුළ සම්පූර්ණ cloud IaC stack එකක් ඇතැයි අදහස් නොවේ.')+
        p('VPC / private subnet යනු internal connectivity සීමා කරන network structure එකකි. Load balancer එක public traffic ගෙනයයි. Cloud firewall, Kubernetes policy සහ application authorization එකිනෙකට එකතු වේ. Managed database එක internet වෙත විවෘත කිරීම අවශ්‍ය නොවිය හැක.')+
        box('IaC workflow එක','Desired infrastructure version control තුළ තබා plan එක review කරන්න; approved apply පසු state සහ drift බලන්න. Terraform state එකට sensitive values තිබිය හැකි නිසා encryption, access control සහ locking සැලසුම් කරන්න.')+
        p('Helm application resources පාලනය කරයි. Cloud network, cluster සහ managed database provisioning වෙනම platform / IaC වගකීම් විය හැක. මේ repository එකේ example production values සම්පූර්ණ cloud provisioning tool එකක් නොවේ.'),('terraform',),('R35',))

    add('ci-cd',PART,'CI සහ CD architecture එක','Confidence before release; controlled promotion afterward',
        diagram('ci-cd-path',[
            ('s',5,10,190,'Pull request','Source review','blue'),('t',235,10,190,'CI validation','Tests / render / schema / scans','teal'),('b',465,10,190,'Publish artifacts','SHA tags / SBOM / signatures','gold'),
            ('a',465,120,190,'Release gate','Protected production environment','gold'),('d',235,120,190,'CD deployment','Short-lived credentials / Helm','blue'),('v',5,120,190,'Post-deploy checks','Business flow + telemetry','teal')],
            [('s','t'),('t','b'),('b','a'),('a','d'),('d','v')],205,'රූපය 18 · Repository workflows සහ අවශ්‍ය deployment acceptance එක සම්බන්ධ කරන ගමන.')+
        p('CI changes එකිනෙකට එකතු කරන විට tests සහ validation කරයි. CD approved artifacts environment එකකට ලබාදෙයි. Automatic deployment කරනවාද manual approval තිබේද යන්න project policy එකට අනුව තීරණය වේ.')+
        table(['වත්මන් file එක','කරන දේ / ඉතිරි සැකසුම'],[
            ('kubernetes-validate.yml','Helm / script / migration / runtime regression checks සහ scans; full role E2E නොවේ.'),
            ('kubernetes-release.yml','Images දහය publish / scan / sign; production runner සහ environment gates configure කළ යුතුය.'),
            ('GitHub settings','Required reviewers, tag restrictions සහ required checks workflow YAML එකෙන් පමණක් සම්පූර්ණ නොවේ.')])+
        box('GitOps ගැන','Git desired state එකට controller එක continuously reconcile කරන model එක GitOps ලෙස හඳුන්වයි. වත්මන් release path direct Helm workflow එකකි; GitOps controller එක දැනට තිබෙන බව නොසලකන්න.'),('oidc','gitops'),('R40','R41'))

    add('release-rollback',PART,'Release එකක් භාරගැනීම සහ rollback','Image compatibility, database compatibility and user-visible outcomes',
        steps(['Exact source / image identity, values diff සහ migration impact review කරන්න.',
            'Stage තුළ same artifact එකෙන් critical journeys සහ negative auth checks කරන්න.',
            'Production approval පෙර rollback-compatible image සහ backup / restore status දැනගන්න.',
            'Rollout අතර readiness, restarts, latency, failures සහ async lag බලන්න.',
            'Rollback අවශ්‍ය නම් app-bearing revision එක තෝරා schema compatibility සනාථ කර නැවත smoke කරන්න.'])+
        code('''helm --kube-context kind-swiftdrop history swiftdrop -n swiftdrop
        helm --kube-context kind-swiftdrop status swiftdrop -n swiftdrop
        # Read first; choose a reviewed revision before a rollback.
        kubectl --context kind-swiftdrop -n swiftdrop get deploy,sts''')+
        table(['Rollback කරන්නේ','ස්වයංක්‍රීයව ආපසු නොයන්නේ'],[
            ('Deployment specification / image reference','Applied DB schema, external email හෝ webhook side effects.'),
            ('Helm resource revision','User විසින් කළ bookings සහ object storage contents.'),
            ('Application process','In-memory pending state; process restart එකකින් නැතිවිය හැක.')])+
        box('Bootstrap revision එකේ අවදානම','Local dependency-only revision එකට rollback කළහොත් apps ඉවත් විය හැක. Revision number අන්ධව තෝරා නොගන්න. Helm rollback එක database restore procedure එකක් නොවේ.','warning'),('rollback',),('R31','R36','R37','R41'))
