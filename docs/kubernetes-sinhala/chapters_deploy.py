from book import add, p, h, code, box, table, steps, ul, diagram

PART='02 / Images සිට ක්‍රියාත්මක release එක දක්වා'

def populate():
    add('images',PART,'Application එක image එකකට සකස් කිරීම','Build contexts, multi-stage builds and immutable identity',
        p('Dockerfile එක build කරන ආකාරයත් අවසානයේ ධාවනය වන runtime එකත් ප්‍රකාශ කරයි. Multi-stage build එකක compiler සහ package managers build stage තුළ තබා runtime image එකට අවශ්‍ය artifacts පමණක් ගෙනයයි.')+
        table(['Image කණ්ඩායම','මෙම repository හි ක්‍රමය'],[
            ('Java services 8','Java 21; service Dockerfiles; executable JAR සහ migration runner runtime තුළ ඇත.'),
            ('Python assistant','Python 3.12 dependencies + app; runtime migration command එකද ලබාගත හැක.'),
            ('Web','Frontend build → nginx port 8080; runtime browser API configuration.'),
            ('MinIO server + client','<code>infra/minio/Dockerfile</code> හි pinned source targets දෙක build කරයි.')])+
        diagram('image-lifecycle',[
            ('s',5,15,190,'Source + dependencies','Reviewed commit / lockfiles','blue'),
            ('b',235,15,190,'Build once','Application + migration tools','teal'),
            ('i',465,15,190,'Versioned image','Tag + content digest','gold'),
            ('r',235,120,190,'Run / promote','Same artifact across stages','teal')],
            [('s','b'),('b','i'),('i','r')],200,'රූපය 06 · පරීක්ෂා කළ artifact එකම promote කිරීමේ අදහස.')+
        box('Tag සහ digest','Tag එක human-readable නමකි; registry policy අනුව එය වෙනත් image එකකට යොමු කළ හැක. Digest එක content identity එකයි. Production workflow එක දැනට SHA tags publish කරයි; digest pinning සහ admission verification තව review කළ යුතුය.')+
        p('Build context එකට <code>.env</code>, private keys, dependency caches හෝ අනවශ්‍ය local files නොයැවීමට <code>.dockerignore</code> පවත්වාගන්න. Secret values Dockerfile ARG / image layer තුළ තබන්න එපා.'))

    add('build',PART,'Images build කරන සම්පූර්ණ පියවර','Use one fresh tag for build, load and deployment',
        code('''Set-Location F:\\EAD\\enterpriseProject
        $releaseTag = "book-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
        .\\infra\\kubernetes\\scripts\\build-images.ps1 -Tag $releaseTag
        if ($LASTEXITCODE -ne 0) { throw 'Image build failed' }
        docker image ls --filter 'reference=swiftdrop/*' ''')+
        p('මෙම script එක application images දහය සහ local MinIO targets දෙක සකස් කරයි. Tag variable එක එම PowerShell session එකේ තබාගන්න. Build අසාර්ථක වුවහොත් load හෝ deploy පියවරට නොයන්න.')+
        h('Java 21 host build විකල්පය')+
        code('''java -version
        javac -version
        .\\infra\\kubernetes\\scripts\\build-images.ps1 `
          -UseHostJavaBuild -Tag $releaseTag''')+
        p('මෙම විකල්පය host එකේ Java reactor එක අලුතින් compile කර එම artifacts non-root Linux runtime images තුළ package කරයි. Local container JDK download බාධාවක් තිබෙන විට උපකාරී වේ. Release CI එක service multi-stage Dockerfiles භාවිත කරයි.')+
        table(['Build ගැටලුව','විමර්ශනය'],[
            ('Java compile failure','වැරදි JDK version, source errors සහ Maven output පරීක්ෂා කරන්න.'),
            ('Maven repository 401','Checked-in <code>infra/kubernetes/maven-settings.xml</code> mirror භාවිත වනවාද බලන්න.'),
            ('Image not found පසුව','Build / load / deploy tag සහ service repository නම එකද බලන්න.')])+
        box('පරණ tag නැවත භාවිත නොකරන්න','ලියාපදිංචි කළ local release එකට gateway සහ notification fixes වෙන වෙනම tags ඇත. සියලු නව sources එකට build කරන විට අලුත් common tag එකක් භාවිත කරන්න; පරණ baseline tag එක fixes අඩංගු බව සිතන්න එපා.','warning'))

    add('load',PART,'Docker images Kind තුළට ගෙනයන්න','The host image store and node containerd are separate',
        code('''.\\infra\\kubernetes\\scripts\\load-images.ps1 -Tag $releaseTag
        if ($LASTEXITCODE -ne 0) { throw 'Image import failed' }''')+
        p('Host Docker තුළ image එක තිබීමෙන් Kind node runtime එකට එය ස්වයංක්‍රීයව නොපෙනේ. Loader script එක images validate කර, target Linux platform හඳුනාගෙන, node image stores වෙත ඒවා import කරයි.')+
        table(['අවස්ථාව','Script එකේ හැසිරීම'],[
            ('Modern Docker API / platform export','<code>docker image save --platform</code> මඟින් එක් temporary archive එකක්; පසුව <code>kind load image-archive</code>.'),
            ('Older CLI / engine capability','Kind හි සාමාන්‍ය batched docker-image import ක්‍රමයට fallback වේ.'),
            ('Architecture override','අවශ්‍ය නම් <code>-Platform linux/amd64</code>; node architecture එකට ගැළපිය යුතුය.')])+
        h('Dependency pull එක node තුළ අසාර්ථක නම්')+
        code('''$dependencies = @('postgres:17.6-alpine', 'redis:7.4-alpine',
          'rabbitmq:4.1-management-alpine', 'axllent/mailpit:v1.27')
        foreach ($image in $dependencies) {
          docker pull $image
          if ($LASTEXITCODE -ne 0) { throw "Pull failed: $image" }
        }
        .\\infra\\kubernetes\\scripts\\import-kind-images.ps1 `
          -Images $dependencies''')+
        box('මෙම fix එකට හේතුව','Docker containerd image store එකේ අතුරුදන් platform blob references නිසා <code>content digest ... not found</code> ලැබිය හැක. නිල Kind workaround එකට ගැළපෙන platform-filtered archive import භාවිත කරයි; registry TLS verification අක්‍රිය නොකරයි.'),('kind-issues','save'))

    add('helm',PART,'Helm chart එක සංවිධානය වී ඇති හැටි','Templates + values + release history',
        code('''infra/kubernetes/
          helm/swiftdrop/
            Chart.yaml + values.schema.json
            values.yaml + values-local.yaml
            values-production.example.yaml
            templates/
          scripts/ + migrations/ + tests/
          kind/cluster.yaml''','Repository map')+
        p('Template එකේ structure සහ values එකේ configuration එක එකතු කර Kubernetes manifests render කරයි. <strong>Release</strong> යනු එම chart deployment එකේ නාමය සහ revision history එකයි. මෙහි release සහ namespace දෙකම <code>swiftdrop</code> වේ.')+
        table(['File / key','භාවිතය'],[
            ('values.yaml','Shared defaults, workloads, probes, resource settings.'),
            ('values-local.yaml','Local dependencies, එක් replica, loaded images සහ HTTP host settings.'),
            ('values-production.example.yaml','Managed endpoints, replicas, TLS, HPA සහ PDB සඳහා placeholders සහිත උදාහරණයක්.'),
            ('values.schema.json','Values වල type / structure validation.'),
            ('templates/migrations.yaml','Rollout පෙර ක්‍රියාත්මක වන schema migration Jobs.')])+
        code('''helm lint infra/kubernetes/helm/swiftdrop `
          -f infra/kubernetes/helm/swiftdrop/values-local.yaml
        helm template swiftdrop infra/kubernetes/helm/swiftdrop `
          -n swiftdrop `
          -f infra/kubernetes/helm/swiftdrop/values-local.yaml''')+
        box('Render කිරීමේ සීමාව','Template එක නිවැරදිව render වීමෙන් registry access, database connectivity හෝ customer workflow එක තහවුරු නොවේ.'),('helm',))

    add('secrets',PART,'ConfigMap සහ Secret නිවැරදි භාවිතය','Configuration injection without publishing credentials',
        table(['සාමාන්‍ය configuration','Secret හරහා සැපයිය යුතු දේ'],[
            ('Service URLs, JWT audience / issuer','Database passwords සහ database connection URLs තුළ ඇති credentials'),
            ('Web API origin, assistantEnabled','JWT private signing key, OAuth client secret'),
            ('Gemini model name සහ feature flags','GOOGLE_API_KEY, Redis / RabbitMQ / MinIO credentials')])+
        p('Chart එක <code>existingSecret: swiftdrop-secrets</code> reference එක භාවිත කරයි. Local secret script එක random credentials සකස් කරයි. නැවත ධාවනය කළ විට පවතින credentials මාරු නොකරන්නේ persistent databases සමඟ ඇති සම්බන්ධතාව රැකීමටයි.')+
        code('''# Metadata only; do not print Secret data.
        kubectl --context kind-swiftdrop -n swiftdrop get secret
        kubectl --context kind-swiftdrop -n swiftdrop get configmap''')+
        steps(['Production secret manager එකේ key names සහ service ownership තීරණය කරන්න.',
            'Secret synchronization controller / CSI integration එක stage පරිසරයේ තහවුරු කරන්න.',
            'Workload එකට අවශ්‍ය keys පමණක් inject කරන්න; frontend variables තුළ keys නොතබන්න.',
            'Rotation එකෙන් පසු අවශ්‍ය consumer rollout සහ database connectivity පරීක්ෂා කරන්න.'])+
        box('Base64 = encryption නොවේ','Kubernetes Secret manifest එකක base64 data කියවිය හැකි පුද්ගලයාට අගය ලබාගත හැක. Git access, RBAC, encryption at rest සහ secret manager එක වෙන වෙනම වැදගත්ය.','warning')+
        p('Local Java database users වෙනස් නමුත් generator එක password එකක් බෙදාගනී; assistant සඳහා වෙනම password එකක් ඇත. Production සඳහා service එකකට වෙන වෙනම credential boundaries අවශ්‍යයි. Local convenience එක production policy එකක් නොකරන්න.'),('secret','configmap'))

    add('bootstrap',PART,'පළමු deployment එකේ අදියර දෙක','Dependencies first → migrations → application rollout',
        diagram('bootstrap',[
            ('s',5,10,190,'Existing release?','deploy-local.ps1 checks','blue'),
            ('b',235,10,190,'First install only','bootstrapOnly=true','gold'),
            ('d',465,10,190,'Dependencies Ready','DB / cache / broker / bucket','teal'),
            ('m',465,115,190,'Migration hooks','Six Flyway + assistant Job','blue'),
            ('a',235,115,190,'App rollout','bootstrapOnly=false','teal'),
            ('v',5,115,190,'Verify','Ingress and business tests','gold')],
            [('s','b'),('b','d'),('d','m'),('m','a'),('a','v')],195,'රූපය 07 · පවතින application release එකකදී bootstrap අදියර මඟහැර full upgrade එකට යයි.')+
        code('''.\\infra\\kubernetes\\scripts\\deploy-local.ps1 -Tag $releaseTag
        .\\infra\\kubernetes\\scripts\\verify-cluster.ps1
        .\\infra\\kubernetes\\tests\\smoke-tests.ps1
        .\\infra\\kubernetes\\tests\\smoke-tests.ps1 -Workflow''')+
        p('පළමු Helm revision එක Postgres, Redis, RabbitMQ, Mailpit, MinIO සහ bucket initialization සඳහායි. එහි application Deployments, Ingress සහ migration hooks නොමැත. දෙවන අදියරේ pre-upgrade migration Jobs සාර්ථක වූ පසු applications rollout වේ.')+
        box('පවතින release එකකට bootstrap යොදන්න එපා','<code>bootstrapOnly=true</code> බවට functional release එක මාරු කළහොත් application resources ඉවත් වේ. Local deployment script එකේ first-install තීරණය භාවිත කරන්න.','warning')+
        p('Helm 3 හි <code>--atomic</code> සහ Helm 4 හි <code>--rollback-on-failure</code> හැකියාව scripts හඳුනාගනී. Rollout failure එකකින් resource specifications rollback කළ හැකි නමුත් database schema එක ස්වයංක්‍රීයව ආපස්සට නොයයි.'),('hooks','rollback'))

    add('flyway',PART,'Java database migrations','One bounded process per owning database before rollout',
        p('Identity, parcel, pricing, dispatch, merchant සහ notification services හය Flyway මඟින් තම schemas පාලනය කරයි. Kubernetes runtime replicas තුළ <code>SPRING_FLYWAY_ENABLED=false</code> යෙදෙන අතර වෙනම Job එකක migration runner ධාවනය වේ.')+
        code('''java -cp "migration-runner:application.jar:lib/*" DatabaseMigration''','Container process · chart runs this automatically')+
        table(['Job සැකසුම','ප්‍රතිඵලය'],[
            ('pre-install / pre-upgrade hooks','Application rollout පෙර migrations අවසන් විය යුතුය.'),
            ('Dedicated ServiceAccount','API token automount අක්‍රියයි; app runtime token අවශ්‍ය නැත.'),
            ('Deadline / retries','Default active deadline 300 seconds; backoffLimit 1.'),
            ('Hook cleanup / TTL','Successful hooks ඉවත් වේ; failed evidence උපරිම දිනක් පමණ තබයි.'),
            ('Standalone runner','Spring application, listeners හෝ full web server start නොකර Flyway පමණක් ක්‍රියාත්මක කරයි.')])+
        p('Migration එක deploy වීමට පෙර database backup policy සහ previous application version සමඟ compatibility තහවුරු කරන්න. Column rename / delete එකක් එකවර කරනවා වෙනුවට expand → migrate data → switch code → contract යන අදියර භාවිත කරන්න.')+
        code('''kubectl --context kind-swiftdrop -n swiftdrop get jobs
        # Replace JOB_NAME with a failed migration Job name.
        kubectl --context kind-swiftdrop -n swiftdrop logs job/JOB_NAME''')+
        box('Failure එකකදී','Logs preserve කර schema ownership, connectivity සහ versioned migration එක නිවැරදි කරන්න. Tables අතින් වෙනස් කිරීම හෝ සියලු replicas වෙත migrations නැවත enable කිරීම සාමාන්‍ය විසඳුමක් නොවේ.','warning'),('flyway','hooks'))

    add('assistant-migration',PART,'Assistant persistence සහ checkpoints','Alembic first; LangGraph schema second',
        diagram('assistant-migrate',[
            ('e',5,15,190,'Secret references','Two database URL formats','gold'),
            ('a',235,15,190,'Alembic upgrade','Conversation / audit tables','blue'),
            ('c',465,15,190,'Checkpoint setup','LangGraph persistence','teal'),
            ('r',235,120,190,'Runtime replicas','CHECKPOINT_AUTO_SETUP=false','blue')],
            [('e','a'),('a','c'),('c','r')],200,'රූපය 08 · Kubernetes තුළ schema initialization සහ request serving වෙන්කර ඇත.')+
        code('''python -m app.migrate''','Container process · assistant migration Job')+
        table(['Variable','Driver / අරමුණ'],[
            ('ASSISTANT_DATABASE_URL','<code>postgresql+asyncpg://USER:PASS@HOST/DB</code> — application storage.'),
            ('CHECKPOINT_DATABASE_URL','<code>postgresql://USER:PASS@HOST/DB</code> — psycopg checkpoint storage.'),
            ('CHECKPOINT_AUTO_SETUP','Kubernetes runtime සඳහා <code>false</code>; Compose / standalone default වෙනස් විය හැක.')])+
        p('මෙහි URL values syntax පෙන්වන placeholders පමණි. <code>app.migrate</code> URLs දෙකම අවශ්‍ය කර Alembic පසු checkpoint setup කරයි. එකක් අසාර්ථක නම් nonzero exit ලැබී rollout නතර වේ.')+
        box('Durability test එක','Conversation එකක් නිර්මාණය කර එහි ID එක තබාගන්න. Assistant rolling restart එකකින් පසු ඒ ID එකේ history නැවත කියවා messages පවතින බව සහ parcel API එකට බලපෑමක් නොමැති බව තහවුරු කරන්න.')+
        p('Retention default දින 30කි. Application tables, audit data සහ checkpoints සඳහා production retention සහ backup policies එකිනෙක ගැළපිය යුතුය. Checkpoint එකක් conversation audit record එකේ ආදේශකයක් නොවේ.'),('alembic',))

    add('storage',PART,'Persistent data රැකගැනීම','PVC, PV, StorageClass and the limits of local durability',
        p('Pod filesystem එක temporary ය. Pod එක වෙනස් වූ පසුත් තබාගත යුතු data සඳහා PersistentVolumeClaim එකක් භාවිත කරයි. StorageClass එක provision කිරීමේ ක්‍රමය තෝරයි; provisioner එක backing volume සපයයි.')+
        table(['දත්ත','Local storage / production සැලැස්ම'],[
            ('PostgreSQL schemas','Local PVC; production managed PostgreSQL + PITR.'),
            ('RabbitMQ broker data','Local PVC; production managed / supported HA topology සහ durability review.'),
            ('Redis projection / state','Local PVC configuration; persistence / cache-loss behaviour වෙනම පරීක්ෂා කරන්න.'),
            ('MinIO proof objects','Local PVC සහ private bucket; production object storage සහ lifecycle policy.'),
            ('Temporary files','<code>emptyDir</code>; Pod lifetime එකට අයත් වේ.')])+
        code('''kubectl --context kind-swiftdrop -n swiftdrop get pvc
        kubectl --context kind-swiftdrop get pv,storageclass
        kubectl --context kind-swiftdrop -n swiftdrop describe pvc PVC_NAME''')+
        box('Pod restart ≠ cluster destruction','PVC එක Pod restart එකකට data රැකිය හැකි නමුත් Kind node containers / cluster මකාදැමීමෙන් host-local storage අහිමි විය හැක. PVC තිබීම backup එකක් නොවේ.','warning')+
        p('Reclaim policy <code>Delete</code> ද <code>Retain</code> ද කියා දැනගන්න. Claim එක මකාදැමීමේ ප්‍රතිඵල storage backend අනුව වෙනස් වේ. Production delete / restore ක්‍රියාදාමයට පෙර ownership සහ tested backup එක සනාථ කරන්න.'),('storage','stateful'))

    add('dependencies',PART,'Infrastructure dependencies හතර','PostgreSQL · Redis · RabbitMQ · MinIO',
        table(['Dependency','භූමිකාව / operational check'],[
            ('PostgreSQL :5432','Service-owned databases; credential boundary, migration status සහ connection pool saturation බලන්න.'),
            ('Redis :6379','Tracking read model, limits සහ වෙනත් cache/state භාවිත. Memory, evictions සහ restore/rebuild හැකියාව මැන බලන්න.'),
            ('RabbitMQ :5672','Outbox events fan-out. Queue depth, oldest message age, unacked සහ dead letters පරීක්ෂා කරන්න.'),
            ('MinIO :9000','Private proof-of-delivery objects. <code>parcel-proofs</code> bucket initialization Job එක idempotent වේ.'),
            ('Mailpit','Local email capture helper; production email delivery / reputation verification එකක් නොවේ.')])+
        p('RabbitMQ සඳහා AMQP TCP startup/readiness checks භාවිත කරයි. නිරන්තර Erlang CLI diagnostic processes සහ aggressive liveness restarts නිසා ඇති CPU contention වැළැක්වීමට local fix එක මෙසේ සකස් කර ඇත. Process exit එකට container restart policy ප්‍රතිචාර දක්වයි.')+
        code('''# Local browser access to signed proof URLs, when needed.
        kubectl --context kind-swiftdrop -n swiftdrop `
          port-forward service/minio 9000:9000''')+
        p('Signed URL එක browser එකෙන් resolve වන public endpoint එක භාවිත කළ යුතුය. Internal <code>minio</code> DNS නම browser එකට නොපෙනේ. Local port-forward එක අවසන් කළ විට එම browser access path එකත් නවතී.')+
        box('Production values සම්පූර්ණ configuration එකක් නොවේ','Example values හි <code>*.example.internal</code> placeholders වෙනස් කළ පමණින් HA, TLS trust, private bucket, SMTP policy සහ credentials සම්පූර්ණ නොවේ. සැබෑ connectivity tests අවශ්‍යයි.'),('rabbit',))

    add('installation-checklist',PART,'ස්ථාපන ක්‍රියාවලිය එක පිටුවක','A repeatable local run from a prepared workspace',
        code('''Set-Location F:\\EAD\\enterpriseProject
        $releaseTag = "local-$(Get-Date -Format 'yyyyMMdd-HHmmss')"

        .\\infra\\kubernetes\\scripts\\create-local-cluster.ps1
        .\\infra\\kubernetes\\scripts\\install-local-addons.ps1
        .\\infra\\kubernetes\\scripts\\build-images.ps1 -Tag $releaseTag
        .\\infra\\kubernetes\\scripts\\load-images.ps1 -Tag $releaseTag
        .\\infra\\kubernetes\\scripts\\deploy-local.ps1 -Tag $releaseTag

        .\\infra\\kubernetes\\scripts\\verify-cluster.ps1
        .\\infra\\kubernetes\\tests\\smoke-tests.ps1
        .\\infra\\kubernetes\\tests\\smoke-tests.ps1 -Workflow''')+
        table(['Gate එක','ඊළඟට යාමට පෙර'],[
            ('Cluster','Nodes Ready; intended context පමණක් තෝරා ඇත.'),
            ('Images','Build සහ import සාර්ථකයි; එකම fresh tag භාවිත කරයි.'),
            ('Data','Dependencies Ready; bucket initialization සහ migrations සාර්ථකයි.'),
            ('Applications','Deployments / StatefulSets Available; Failed Pods නැත.'),
            ('Behaviour','Booking, tracking, email, cancellation සහ assistant history තහවුරුයි.')])+
        p('Commands තනි තනිව ධාවනය කර එක් එක් පියවරේ ප්‍රතිඵල පරීක්ෂා කරන්න. අසාර්ථක පියවරක් මඟහැර ඉදිරියට යාමෙන් පසුව පෙනෙන error එක මූල හේතුවෙන් ඈත් විය හැක.')+
        box('අවසන් ලිපිනය','<strong>http://localhost:8088</strong> — hosts file වෙනස් කිරීමක් අවශ්‍ය නැත. <code>swiftdrop.local</code> alias එක optional වේ. Smoke script එක අවශ්‍ය Host header සපයයි.'))
