from book import add, p, h, code, box, table, steps, ul, diagram

PART='01 / පදනම සහ පද්ධති සැලැස්ම'

def populate():
    add('roadmap',PART,'මුල සිට අවසානය දක්වා ගමන','A–Z learning map · concept → implementation → operation',
        p('මෙම පොතේ ප්‍රායෝගික උදාහරණය <strong>SwiftDrop</strong> parcel delivery පද්ධතියයි. Docker image එකක් සෑදීමෙන් පටන්ගෙන, එය Kubernetes තුළ ධාවනය කිරීම, සැබෑ request එකක ගමන සහ අසාර්ථක deployment එකකින් නැවත සේවාව ලබාගැනීම දක්වා එකම කතාවක් අනුගමනය කරන්න.')+
        diagram('learning-roadmap',[
            ('a',5,10,190,'Understand','Containers / cluster / objects','blue'),
            ('b',235,10,190,'Package','Images / configuration / secrets','teal'),
            ('c',465,10,190,'Deploy','Kind / Helm / migrations','gold'),
            ('d',465,115,190,'Verify','Business flows / security','blue'),
            ('e',235,115,190,'Operate','Metrics / scale / recover','teal'),
            ('f',5,115,190,'Promote','Production readiness gates','gold')],
            [('a','b'),('b','c'),('c','d'),('d','e'),('e','f')],190,'රූපය 01 · ඉගෙනීම සහ ක්‍රියාත්මක කිරීම සඳහා මාර්ග සිතියම.')+
        table(['කියවන ආකාරය','කරන්න ඕනෑ දේ'],[
            ('පළමු වරට Kubernetes භාවිත කරන්නේ නම්','පදනම් පිටු කියවා request flow එක තමන්ගේ වචනවලින් අඳින්න.'),
            ('SwiftDrop ධාවනය කරන්නේ නම්','Local installation, images, Helm සහ migration අනුපිළිවෙළ අනුගමනය කරන්න.'),
            ('Demo / viva සූදානම','Customer workflow, event flow, assistant guardrails සහ recovery පැහැදිලි කරන්න.'),
            ('Production භාරගන්නේ නම්','CNI enforcement, controller migration, TLS, backup restore සහ release gates සම්පූර්ණ කරන්න.')])+
        box('කියවීමෙන් පසු ලැබිය යුතු හැකියාව','“Pod එක Ready” බවට අමතරව, පාරිභෝගිකයාගේ booking එක, notification එක සහ conversation history එක සැබැවින්ම ක්‍රියා කරන බව සාක්ෂි සහිතව පෙන්විය හැකි විය යුතුය.'))

    add('containers',PART,'Docker සහ Kubernetes','Container packaging and orchestration',
        p('<strong>Image</strong> යනු application code, runtime සහ අවශ්‍ය files අඩංගු read-only package එකකි. <strong>Container</strong> යනු එම image එකෙන් ධාවනය වන process එකයි. එය සම්පූර්ණ virtual machine එකක් නොවන අතර host kernel එක බෙදාගනී.')+
        table(['අවශ්‍යතාව','මෙහි විසඳුම'],[
            ('එකම code එක වෙනත් machine එකක ධාවනය කිරීම','Dockerfile → reproducible image → registry / local import'),
            ('Process එක නැවත නැවත අසාර්ථක වීම','kubelet restart policy සහ controller reconciliation'),
            ('Replicas කිහිපයකට requests යැවීම','Deployment + Service + readiness'),
            ('Version එක ක්‍රමයෙන් මාරු කිරීම','Rolling update + health verification'),
            ('Data අඛණ්ඩව තබාගැනීම','PVC / managed database + backup ක්‍රමවේදය')])+
        h('Compose සිට Kubernetes වෙත')+
        p('Compose service එකක් Kubernetes Deployment එකකට සමාන කළ හැකි නමුත් සෑම කොටසක්ම සෘජුව පරිවර්තනය නොවේ. Compose network name වෙනුවට Service DNS භාවිත වේ. Startup ordering සඳහා dependency names පමණක් ප්‍රමාණවත් නොවන නිසා migration Jobs, probes සහ application retry ක්‍රම අවශ්‍ය වේ.')+
        code('''docker version
        docker info --format '{{.OSType}}/{{.Architecture}}'
        kubectl version --client
        helm version
        kind version''')+
        box('මෙහි පරිසරය','Docker Desktop Linux containers භාවිත කරයි. Kind node දෙක Docker containers ලෙස එම Desktop VM එක තුළම ධාවනය වේ. Node දෙකක් තිබීමෙන් laptop failure එකකට එරෙහි high availability නොලැබේ.'))

    add('control-plane',PART,'Cluster එක ඇතුළත සිදුවන්නේ කුමක්ද?','Control plane, worker nodes and supporting components',
        diagram('control-plane',[
            ('u',225,0,210,'kubectl / Helm','Desired state submitted','teal'),
            ('a',225,90,210,'API server','Authentication / admission','blue'),
            ('e',5,180,190,'etcd','API object state','gold'),
            ('c',235,180,190,'Controllers','Reconcile observed state','blue'),
            ('s',465,180,190,'Scheduler','Choose a suitable node','blue'),
            ('n',225,285,210,'Worker: kubelet + CRI','Run and report containers','teal')],
            [('u','a'),('a','e'),('a','c'),('a','s'),('c','n'),('s','n')],355,'රූපය 02 · මෙය control relationships පෙන්වයි; සියලු application traffic API server හරහා ගමන් නොකරයි.')+
        p('<strong>API server</strong> හරහා cluster objects කියවීම සහ වෙනස් කිරීම සිදු වේ. <strong>etcd</strong> තුළ cluster configuration state තබයි. <strong>Scheduler</strong> සුදුසු node එක තෝරයි. <strong>Controllers</strong> අවශ්‍ය replicas පවත්වාගනී. Node එකේ <strong>kubelet</strong> container runtime සමඟ කතා කරයි.')+
        p('CNI plugin එක Pod networking සකස් කරයි. CoreDNS මඟින් Service names resolve කරයි. Service forwarding සඳහා kube-proxy හෝ ඒ සඳහා විකල්ප implementation එකක් භාවිත විය හැක. Storage සඳහා CSI driver / provisioner එක target environment අනුව වෙනස් වේ.')+
        box('දත්ත දෙක වෙන්කර හඳුනාගන්න','etcd backup එක application PostgreSQL database, Redis projection හෝ MinIO objects සඳහා සම්පූර්ණ backup එකක් නොවේ.'),('components',))

    add('reconciliation',PART,'Desired state සහ reconciliation','Why controllers keep working after kubectl exits',
        p('Deployment එකේ <code>replicas: 2</code> යනු “දැන් containers දෙකක් පමණක් start කරන්න” යන command එකක් නොවේ. එය තබාගත යුතු තත්ත්වයක ප්‍රකාශයකි. Observed state එක වෙනස් වූ විට controller එක නැවත එයට ළඟාවීමට කටයුතු කරයි.')+
        diagram('reconcile',[
            ('d',5,15,190,'Desired: 2 replicas','Deployment specification','blue'),
            ('o',235,15,190,'Observed: 1 replica','Pod or node failure','red'),
            ('r',465,15,190,'Reconcile','Create a replacement Pod','teal'),
            ('s',235,120,190,'Ready: 2 replicas','Service endpoints updated','gold')],
            [('d','o'),('o','r'),('r','s')],200,'රූපය 03 · Replica count සහ Ready count අතර වෙනසක් තිබිය හැක.')+
        steps(['Manifest එක API server වෙත යවයි; schema, authorization සහ admission පරීක්ෂා වේ.',
               'Deployment controller එක ReplicaSet එකක් සහ අවශ්‍ය Pods නිර්මාණය කරයි.',
               'Scheduler එක resources හා scheduling constraints සලකා node එකක් තෝරයි.',
               'kubelet image එක ලබාගෙන container එක ධාවනය කර probes ප්‍රතිඵල වාර්තා කරයි.',
               'Pod එක Ready වූ විට සාමාන්‍ය Service traffic සඳහා endpoint එකක් වේ.'])+
        box('Self-healing හි සීමාව','වැරදි password එකක්, database corruption එකක් හෝ application bug එකක් Kubernetes විසින් ස්වයංක්‍රීයව නිවැරදි නොකරයි. එකම වැරදි image එක නැවත start කිරීමෙන් මූල හේතුව පවතී.','warning'),('objects','deployment'))

    add('objects',PART,'නිවැරදි Kubernetes object එක තෝරන්න','Pod · Deployment · StatefulSet · Job · CronJob · DaemonSet',
        table(['Object එක','භාවිතය සහ SwiftDrop උදාහරණය'],[
            ('Pod','එකට schedule වන containers සඳහා කුඩාම deployment unit එක. සාමාන්‍ය app Pod සෘජුව අතින් පාලනය නොකරයි.'),
            ('Deployment / ReplicaSet','Stateless app replicas සහ rollout. Web, gateway සහ domain services මෙහි ඇත.'),
            ('StatefulSet','ස්ථාවර ordinal identity හා persistent storage සම්බන්ධ workloads. Local Postgres, Redis, RabbitMQ, MinIO.'),
            ('Job','සාර්ථකව අවසන් විය යුතු සීමිත කාර්යයක්. Flyway / Alembic migration Jobs.'),
            ('CronJob','කාලසටහනකට Job සෑදීම. අනාගත retention / backup automation සඳහා සලකා බලන්න; සෑම එවැනි Job එකක්ම දැනට තිබෙන බවක් අදහස් නොවේ.'),
            ('DaemonSet','තෝරාගත් සෑම node එකකම Pod එකක්. Node log collector වැනි platform agents.'),
            ('Service / Ingress','අභ්‍යන්තර discovery සහ පිටතින් HTTP routing.'),
            ('ConfigMap / Secret / PVC','සාමාන්‍ය configuration, රහස් references සහ storage claim.')])+
        p('StatefulSet එකක් database එකකට භාවිත කළ පමණින් replication, failover හෝ backup සෑදෙන්නේ නැත. SwiftDrop local stateful dependencies එක් replica බැගින් යෙදෙයි. Production සැලැස්ම managed data services භාවිත කිරීමයි.')+
        code('''kubectl --context kind-swiftdrop -n swiftdrop get deploy,sts
        kubectl --context kind-swiftdrop -n swiftdrop get jobs,pvc
        kubectl --context kind-swiftdrop -n swiftdrop get svc,ingress'''),('stateful','jobs'))

    add('context',PART,'Context, namespace සහ labels','Make every operation target the intended cluster',
        p('<strong>Context</strong> එකක cluster, user සහ default namespace තොරතුරු ඇත. Namespace එක එකම cluster එක තුළ resources සඳහා logical සීමාවකි. එය තනිවම සම්පූර්ණ security boundary එකක් නොවේ; RBAC, networking සහ policy ද අවශ්‍ය වේ.')+
        code('''Set-Location F:\\EAD\\enterpriseProject
        kubectl config get-contexts
        kubectl config current-context
        kubectl config use-context kind-swiftdrop
        kubectl --context kind-swiftdrop get nodes
        kubectl --context kind-swiftdrop -n swiftdrop get pods''')+
        table(['Label / සංකල්පය','වැදගත්කම'],[
            ('app.kubernetes.io/instance','Helm release එකට අයත් resources හඳුනාගනී.'),
            ('app.kubernetes.io/component','web, gateway, parcel-service වැනි workload තෝරාගනී.'),
            ('Service selector','Pod labels ගැළපෙන්නේ නැත්නම් Service එකට endpoints නොලැබේ.'),
            ('Annotations','Ingress settings, Helm hooks වැනි controller metadata රඳවයි.')])+
        box('මෙම workspace එකේ සීමාව','Local commands සඳහා <code>kind-swiftdrop</code> context සහ <code>swiftdrop</code> namespace භාවිත කරන්න. <code>churn-model</code> සහ <code>churn-model2</code> වෙනත් වැඩ සඳහා ඇති clusters ය. ඒවා වෙනස් කිරීම SwiftDrop ක්‍රියාවලියේ කොටසක් නොවේ.','warning')+
        p('Helm එකෙන් පාලනය කරන resource එකක් අතින් edit කළහොත් ඊළඟ upgrade එකේ එම වෙනස නැතිවිය හැක. ස්ථිර වෙනස values / templates තුළ සටහන් කරන්න.'))

    add('yaml',PART,'YAML manifest එකක් කියවීම','A small teaching example; not a replacement for the Helm chart',
        p('පහත නිදසුන ක්‍රියාකාරී SwiftDrop image එක build සහ load කර තිබෙන lab එකක් සඳහාය. <code>NEW_TAG</code> ඔබ සෑදූ tag එකෙන් මාරු කරන්න. Main application එකට යෙදීම සඳහා repository හි Helm chart එකම භාවිත කරන්න.')+
        code('''apiVersion: apps/v1
        kind: Deployment
        metadata:
          name: web-example
          namespace: learning
        spec:
          replicas: 1
          selector:
            matchLabels: {app: web-example}
          template:
            metadata:
              labels: {app: web-example}
            spec:
              automountServiceAccountToken: false
              containers:
                - name: web
                  image: swiftdrop/web:NEW_TAG
                  ports: [{containerPort: 8080}]
                  resources:
                    requests: {cpu: 50m, memory: 64Mi}
                    limits: {cpu: 250m, memory: 128Mi}
                  readinessProbe:
                    httpGet: {path: /healthz, port: 8080}''','YAML · teaching example')+
        p('<code>apiVersion</code> API schema එක තෝරයි. <code>kind</code> resource වර්ගයයි. <code>metadata</code> හඳුනාගැනීම සපයයි. <code>spec</code> අවශ්‍ය තත්ත්වයයි. Controller විසින් වාර්තා කරන <code>status</code> අතින් සකස් නොකරයි.')+
        box('අභ්‍යාසය','Selector එක සහ Pod template label එක සමාන විය යුත්තේ ඇයි යන්න පැහැදිලි කරන්න. Readiness probe එක fail වුවහොත් container එක හැමවිටම restart වෙනවාද? පිළිතුර: නැත; එය සාමාන්‍ය Service traffic වලින් ඉවත් වේ.'),('objects','deployment'))

    add('service-dns',PART,'Service සහ DNS request එක ගෙනයන හැටි','Stable names over replaceable Pod IP addresses',
        diagram('service-dns',[
            ('c',0,15,185,'gateway','Caller in swiftdrop namespace','blue'),
            ('s',235,15,190,'parcel-service:8080','ClusterIP Service / DNS','teal'),
            ('p1',470,0,185,'Parcel Pod A','Ready endpoint','gold'),
            ('p2',470,100,185,'Parcel Pod B','Ready endpoint','gold')],
            [('c','s'),('s','p1'),('s','p2')],180,'රූපය 04 · Application එක Pod IP එකක් hard-code නොකර Service name එකට සම්බන්ධ වේ.')+
        p('එකම namespace එක තුළ <code>http://parcel-service:8080</code> භාවිත කළ හැක. වෙනත් namespace එකකින් අවශ්‍ය නම් <code>parcel-service.swiftdrop.svc.cluster.local</code> වැනි full name එක භාවිත කළ හැක. සැබෑ cluster domain වෙනස් කර තිබේ නම් එයට ගැළපිය යුතුය.')+
        table(['Service වර්ගය','භූමිකාව'],[
            ('ClusterIP','Cluster තුළ private virtual endpoint එක. SwiftDrop app dependencies සඳහා සාමාන්‍ය තේරීම.'),
            ('Headless: clusterIP: None','ස්ථාවර virtual IP එකක් වෙනුවට backing endpoints වෙත DNS ලබාදීම. Stateful discovery සඳහා ප්‍රයෝජනවත්.'),
            ('NodePort / LoadBalancer','Node port හෝ cloud load balancer හරහා exposure. සියලු microservices පිටතට විවෘත කිරීම අවශ්‍ය නැත.')])+
        code('''kubectl --context kind-swiftdrop -n swiftdrop get svc
        kubectl --context kind-swiftdrop -n swiftdrop get endpointslices
        kubectl --context kind-swiftdrop -n swiftdrop describe svc gateway''')+
        p('Service එක තිබුණත් endpoint නැත්නම් selectors, Pod readiness සහ targetPort පරීක්ෂා කරන්න. DNS resolve වීම සහ TCP connection සාර්ථක වීම වෙන වෙනම තහවුරු කරන්න.'),('service','dns'))

    add('architecture',PART,'SwiftDrop සම්පූර්ණ සැලැස්ම','Only the web application and API gateway are public',
        diagram('architecture',[
            ('b',225,0,210,'Browser / API client','http://localhost:8088','teal'),
            ('i',225,85,210,'Ingress controller','/ → web; /api, /ws → gateway','blue'),
            ('w',5,170,190,'Web SPA','React → nginx :8080','teal'),
            ('g',235,170,190,'API gateway','JWT / routes / limits','blue'),
            ('a',465,170,190,'AI assistant','FastAPI / LangGraph / SSE','teal'),
            ('d',110,270,440,'Domain services','Identity · Parcel · Pricing · Dispatch · Merchant','blue'),
            ('t',5,370,190,'Event consumers','Tracking / Notification','blue'),
            ('m',235,370,190,'Messaging / cache','RabbitMQ / Redis','gold'),
            ('p',465,370,190,'Persistent data','PostgreSQL / MinIO','gold')],
            [('b','i'),('i','w'),('i','g'),('g','a'),('g','d'),('a','d'),('d','m'),('d','p'),('m','t')],450,'රූපය 05 · තාර්කික සැලැස්ම. සියලු internal call එකක්ම වෙන වෙනම මෙම රූපයේ ඇඳ නැත.')+
        p('Local cluster එක තුළ application Deployments දහයක් ඇත. Mailpit වෙනම local SMTP helper Deployment එකකි. PostgreSQL, Redis, RabbitMQ සහ MinIO StatefulSets හතරකි. Java applications අටක්, Python assistant එකක් සහ web application එකක් deploy වේ.')+
        box('Public boundary','PostgreSQL, RabbitMQ management, Redis සහ MinIO සඳහා public Ingress නැත. අවශ්‍ය local inspection සඳහා සීමිත port-forward එකක් භාවිත කරන්න.'))

    add('service-map',PART,'Service එකකට අයිති වගකීම','Ports, state and ownership boundaries',
        table(['Workload','Port','අයිතිය / data'],[
            ('web','8080','SPA සහ runtime browser configuration'),
            ('gateway','8080','Routing, JWT, limits, health aggregation'),
            ('identity-service','8080','Users / roles / JWT signing; identity DB'),
            ('parcel-service','8080','Booking / state machine / outbox; parcel DB'),
            ('pricing-service','8080','Zones / pricing rules; pricing DB'),
            ('dispatch-service','8080','Roster / queue / assignment commands; dispatch DB'),
            ('merchant-service','8080','Organisations / API keys / webhooks; merchant DB'),
            ('tracking-service','8080','Privacy-reduced Redis read projection'),
            ('notification-service','8086','Email / SMS / preferences / log; notification DB'),
            ('ai-assistant-service','8080','Conversations / audit / checkpoints; assistant DB')])+
        p('Service එකක් වෙනත් service එකක database tables සෘජුව update නොකරයි. API හෝ versioned events හරහා සම්බන්ධ වේ. Cross-service UUID එකක් තිබීමෙන් database foreign key එකක් තිබෙන බව අදහස් නොවේ.')+
        box('Local සහ production වෙනස','Local PostgreSQL instance එක databases කිහිපයක් සහ users කිහිපයක් සපයයි. Production සඳහා managed storage, වෙන වෙනම credentials, backup සහ recovery targets අවශ්‍ය වේ.'))

    add('request-flow',PART,'Browser සිට database එක දක්වා','HTTP, JWT, WebSocket and server-sent events',
        steps(['Browser එක <code>http://localhost:8088</code> වෙත යයි. Ingress හි <code>/</code> path එක web Service වෙත යයි.',
            'SPA එක API request යවන්නේ එම origin එකේ <code>/api</code> යටතේය. Gateway එක route එක තෝරා JWT සහ rate limits පරීක්ෂා කරයි.',
            'අදාළ Java service එක ownership සහ role authorization නැවත පරීක්ෂා කර තම database transaction එක කරයි.',
            'Response එක browser වෙත පැමිණේ. Event-driven side effects පසුව asynchronous ලෙස සම්පූර්ණ විය හැක.'])+
        table(['Traffic','අවශ්‍ය සැකසුම / ලක්ෂණය'],[
            ('සාමාන්‍ය REST','Status code, timeout, authentication සහ request ID තබාගන්න.'),
            ('Tracking WebSocket / STOMP','<code>/ws</code> routing සහ connection upgrade සහාය අවශ්‍යයි.'),
            ('Tracking fallback','WebSocket → SSE → 15-second polling ලෙස frontend degrade විය හැක.'),
            ('Assistant SSE','Response <code>text/event-stream</code>; buffering අක්‍රිය කළ යුතුය. දිගු read/send timeouts local Ingress තුළ ඇත.'),
            ('Proof upload','Request size, content type, authorization සහ object storage endpoint සලකා බලන්න.')])+
        p('Streaming response එකේ HTTP 200 ලැබීම පමණක් ප්‍රමාණවත් නැත. Events මැද proxy එක buffer කරනවාද, <code>done</code> event එක ලැබෙනවාද, conversation එක පසුව නැවත කියවිය හැකිද යන්න පරීක්ෂා කරන්න.')+
        box('බාධාවක් හඳුනාගැනීම','UI load වෙන අතර API fail නම් web image එක පමණක් නැවත build කිරීමෙන් ගැටලුව විසඳෙන්නේ නැත. Gateway routes, DNS, auth සහ downstream status එක අනුව request එක පසුපස යන්න.'))

    add('prerequisites',PART,'ස්ථාපනයට පෙර සූදානම','Windows PowerShell · Docker Desktop · kubectl · Kind · Helm',
        p('Commands repository root එකෙන් ධාවනය කරන්න. Docker Desktop විවෘත කර Linux engine එක ප්‍රතිචාර දක්වන තෙක් රැඳෙන්න. Version numbers පහත දක්වන්නේ මේ project එකේ පරීක්ෂා කළ snapshot එකයි; ඒවා අලුත්ම versions බවක් නොකියයි.')+
        table(['Tool / පරිසරය','ලියාපදිංචි කළ local version'],[
            ('Docker Engine','29.5.3 · Linux containers'),('Kind','0.31.0'),
            ('Kubernetes node image','v1.35.0'),('Helm','4.1.4 · scripts Helm 3 / 4 හඳුනාගනී'),
            ('Java / Python','Java 21; assistant Python 3.12'),
            ('Local topology','1 control-plane + 1 worker; context kind-swiftdrop')])+
        code('''Set-Location F:\\EAD\\enterpriseProject
        docker version
        docker info --format '{{.OSType}}/{{.Architecture}}'
        kubectl version --client
        kind version
        helm version
        Get-Command docker,kubectl,kind,helm''')+
        p('ලියාපදිංචි කළ Docker VM එකේ CPU cores 4ක් සහ RAM 8 GBක් වෙනත් clusters සමඟ බෙදාගෙන තිබුණි. එකවර Java cold starts නිසා වැඩි කාලයක් ගත විය. ඔබේ workload සඳහා capacity වෙනම මැන බලන්න; මෙම සංඛ්‍යා production sizing formula එකක් නොවේ.')+
        box('Credentials සකස් කිරීම','Local deployment script එක credentials සකස් කර පවතින Secret එක preserve කරයි. Production සඳහා secret manager සහ approved endpoints සැපයිය යුතුය. මෙම පොතේ usable passwords හෝ private keys නැත.'))

    add('create-cluster',PART,'Local cluster එක නිර්මාණය','Create only the named SwiftDrop Kind cluster',
        code('''.\\infra\\kubernetes\\scripts\\create-local-cluster.ps1
        kubectl config current-context
        kubectl --context kind-swiftdrop get nodes -o wide
        kubectl --context kind-swiftdrop -n kube-system get pods''')+
        steps(['Script එක <code>infra/kubernetes/kind/cluster.yaml</code> භාවිත කර <code>swiftdrop</code> නමින් cluster එක සකස් කරයි.',
            'Control-plane node එකට ingress-ready label සහ host port mappings ලැබේ. HTTP 8088 → node 80; 18443 → node 443.',
            'Readiness සඳහා default විනාඩි 10ක් ඉඩ ඇත. අසාර්ථක node containers diagnosis සඳහා retain කරයි.',
            'Nodes Ready වූ පසු CoreDNS සහ network components සෞඛ්‍යවත්ද පරීක්ෂා කර ඊළඟ පියවරට යන්න.'])+
        table(['නිරීක්ෂණය','අර්ථය / ඊළඟ පරීක්ෂණය'],[
            ('Docker daemon unreachable','Desktop engine එකේ readiness පරීක්ෂා කරන්න.'),
            ('Port 8088 in use','වෙනත් process එකක් අයිතිද හඳුනාගෙන mapping එක සවිස්තරව වෙනස් කරන්න.'),
            ('Node NotReady','Node conditions, kube-system Pods සහ container logs කියවන්න.'),
            ('DNS / registry timeout','Network සහ DNS path පරීක්ෂා කරන්න; image import workaround පසුව ඇත.')])+
        box('TLS port එකක් තිබීම ප්‍රමාණවත් නැත','18443 mapping එක තිබුණත් local chart එක TLS enable කර නැත. Browser එකට භාවිත කරන ලිපිනය <code>http://localhost:8088</code> ය.','warning'),('kind',))

    add('local-ingress',PART,'Ingress සහ controller ජීවනචක්‍රය','Recorded local setup and a required production migration',
        code('''.\\infra\\kubernetes\\scripts\\install-local-addons.ps1
        kubectl --context kind-swiftdrop -n ingress-nginx get pods
        kubectl --context kind-swiftdrop get ingressclass''')+
        p('Repository හි local installer එක ingress-nginx chart <code>4.13.3</code> සහ controller <code>1.13.3</code> pin කරයි. Cold image downloads සඳහා විනාඩි 15ක් ඉඩ ලබාදේ. මෙය දැනට පරීක්ෂා කළ project setup එක නැවත තේරුම් ගැනීමට දක්වන ක්‍රියාවලියයි.')+
        box('2026 production අවශ්‍යතාව','Kubernetes නිල නිවේදනය අනුව community ingress-nginx සඳහා maintenance 2026 මාර්තු මාසයෙන් අවසන් වේ. මෙම පොතේ දිනය වන 2026-11-16 වන විට එය production සඳහා තවදුරටත් සහාය ලබන controller එකක් ලෙස සැලකිය නොහැක. Production go-live පෙර maintained controller / Gateway API implementation එකකට මාරු වී පරීක්ෂා කරන්න.','warning')+
        table(['සංකල්පය','වෙන්කර තේරුම්ගත යුතු දේ'],[
            ('Ingress API','Routing rules ප්‍රකාශ කරන Kubernetes resource එක.'),
            ('Ingress controller','එම rules ක්‍රියාත්මක කරන software එක; API resource එක පමණක් සෑදීම ප්‍රමාණවත් නැත.'),
            ('Gateway API','GatewayClass / Gateway / HTTPRoute ලෙස වගකීම් වෙන්කරන නව routing model එක.'),
            ('Spring API gateway','Application JWT / rate limit / service routing layer එක. Kubernetes Gateway API සමඟ එකම දෙයක් නොවේ.')])+
        p('Migration එකේදී <code>/api</code>, <code>/ws</code>, hostnames, TLS, uploads සහ SSE buffering/timeouts නැවත පරීක්ෂා කළ යුතුය. Local controller එක මාරු කිරීම මෙම PDF සෑදීමේදී කර නැත.'),('ingress','retirement','gateway'))
