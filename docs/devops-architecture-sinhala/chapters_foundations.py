from book import add,p,h,code,box,table,steps,diagram

PART='01 / පද්ධතිය මුල සිට තේරුම් ගැනීම'

def populate():
    add('business',PART,'මුලින්ම ව්‍යාපාරය තේරුම් ගනිමු','The software exists to move a parcel and report what happened',
        p('SwiftDrop යනු parcel delivery පද්ධතියකි. පාරිභෝගිකයා delivery එකක් book කරයි. Dispatcher කෙනෙක් courier කෙනෙකුට වැඩ පවරයි. Courier parcel එක රැගෙන ගොස් භාරදුන් බව සටහන් කරයි. පාරිභෝගිකයාට තත්ත්වය සහ පණිවිඩ ලැබේ. Architecture එකේ සෑම කොටසක්ම මෙම ගමනේ අවශ්‍යතාවකට සම්බන්ධ කරන්න.')+
        diagram('business-journey',[
            ('c',5,10,190,'Customer','Quote and book','teal'),('d',235,10,190,'Dispatcher','Assign the work','blue'),('r',465,10,190,'Courier','Collect and deliver','teal'),
            ('p',465,115,190,'Delivery proof','Private object + metadata','gold'),('t',235,115,190,'Tracking / notifications','Show progress and outcome','blue'),('m',5,115,190,'Merchant systems','Orders and signed webhooks','gold')],
            [('c','d'),('d','r'),('r','p'),('p','t'),('t','m')],195,'රූපය 01 · Business journey එකයි; මෙහි arrows සියල්ල synchronous API calls නොවේ.')+
        table(['පුද්ගලයා','System එකෙන් බලාපොරොත්තු වන්නේ'],[
            ('Customer','නිවැරදි ගාස්තුව, duplicate නොවන booking එකක් සහ tracking.'),
            ('Courier / dispatcher','නිවැරදි assignments සහ අවසර ඇති status updates.'),
            ('Merchant','Approved account, API orders සහ විශ්වාස කළ හැකි events.'),
            ('Administrator','Users, roles, pricing සහ operational controls.')])+
        box('DevOps ප්‍රශ්නය','“Containers run වෙනවාද?” සමඟ “Customer කෙනෙකුට delivery book කර tracking බලන්න පුළුවන්ද?” යන්නත් තහවුරු කරන්න. මෙය ඉදිරි සෑම පරිච්ඡේදයකම මූලික මිනුමයි.'),evidence=('R12','R14','R42'))

    add('devops-role',PART,'DevOps engineer කෙනෙකුගේ වගකීම','Release safely, observe behaviour and recover predictably',
        p('ඔබ සියලු Java සහ Python code lines ලියන්න දැනගත යුතු නැත. නමුත් request එක යන්නේ කොහෙටද, data අයිති කාටද, වෙනසක් release කරන්නේ කොහොමද සහ failure එකකදී සාක්ෂි සොයන්නේ කොහෙද යන්න පැහැදිලි කළ හැකි විය යුතුය.')+
        table(['වගකීම','මෙම project එකේ උදාහරණය'],[
            ('Build සහ release','Source → tests → images → reviewed deployment.'),
            ('Runtime configuration','Service URLs, ports, secrets සහ environment වෙනස්කම්.'),
            ('Reliability','Probes, retries, capacity, data durability සහ recovery.'),
            ('Security','Public boundary, JWT, service permissions සහ credential rotation.'),
            ('Observability','Request IDs, useful logs, metrics, alerts සහ customer symptoms.'),
            ('Ownership','Incident එකක app developer, platform owner සහ database owner සම්බන්ධ කිරීම.')])+
        h('ඔබට පිළිතුරු දිය හැකි විය යුතු ප්‍රශ්න පහක්')+
        steps(['මේ service එක මොන business වගකීම දරනවාද?',
            'එයට අවශ්‍ය upstream / downstream dependencies මොනවාද?',
            'එය fail වුවහොත් කුමන user journey එක නවතීද?',
            'Data නැති නොකර rollback / recover කරන්නේ කෙසේද?',
            'වැඩ කරන බව කියන්නේ කුමන evidence එකෙන්ද?'])+
        box('පළමු ප්‍රතිඵලය','පොත කියවා තමන්ගේ system map එක අතින් නැවත අඳින්න. Component name පමණක් ලියනවා වෙනුවට වගකීම, protocol සහ data owner එකද සටහන් කරන්න.'))

    add('views',PART,'Architecture එක රූප කිහිපයකින් බලමු','Context → deployable units → internals → deployment',
        p('එකම විශාල රූපයක business roles, Java classes, Kubernetes nodes සහ database tables සියල්ල දැමීමෙන් කියවීම අපහසු වේ. එක වරකට එක් ප්‍රශ්නයකට පිළිතුරු දෙන view එකක් භාවිත කරන්න. C4 model එක මේ මට්ටම් වෙන්කර දැකීමට ප්‍රයෝජනවත් මාර්ගයකි.')+
        table(['View එක','ඔබ විසඳන ප්‍රශ්නය'],[
            ('System context','System එක භාවිත කරන්නේ කවුද? External providers කවුද?'),
            ('Container / deployable view','Web, gateway, services සහ data stores සම්බන්ධ වන්නේ කෙසේද?'),
            ('Component / code view','Controller, service, repository සහ client වෙන්කර තිබෙන්නේ කෙසේද?'),
            ('Dynamic / sequence view','එක booking request එකක පියවර අනුපිළිවෙළ කුමක්ද?'),
            ('Deployment view','Processes, images, Pods, nodes සහ managed services කොහේද?'),
            ('Operational dependency view','එක් dependency එකක් fail වූ විට බලපෑම යන්නේ කොතැනටද?')])+
        box('Container යන පදයේ අර්ථ දෙක','Architecture model එකේ container යනු deploy කළ හැකි application / data store එකකි. Docker container යනු isolated runtime process එකකි. හැම architecture diagram box එකක්ම Docker process එකක් විය යුතු නැත.')+
        p('මෙම පොතේ සමහර diagrams source code එකේ structure දක්වයි. Production cloud topology වැනි diagrams සැලසුම් උදාහරණ ලෙස ලකුණු කර ඇත. රූපයක් තිබීමෙන් ඒ infrastructure එක සැබැවින්ම provision වී ඇති බව අදහස් නොවේ.'),('c4',))

    add('context',PART,'System context: ඇතුළත සහ පිටත','People, SwiftDrop and external dependencies',
        diagram('system-context',[
            ('u',5,10,190,'People','Customer / courier / staff','teal'),('s',235,10,190,'SwiftDrop','Delivery platform','blue'),('m',465,10,190,'Merchant backend','REST client / webhook receiver','teal'),
            ('g',5,135,190,'Google identity','Optional sign-in integration','gold'),('a',235,135,190,'Gemini provider','Optional model responses','gold'),('n',465,135,190,'Email / SMS provider','Mailpit locally','gold')],
            [('u','s'),('m','s'),('s','m'),('s','g'),('s','a'),('s','n')],220,'රූපය 02 · External integration එකකට network, credentials සහ failure policy අවශ්‍යය.')+
        p('Browser එකට API request යැවීමට gateway URL එක අවශ්‍ය වේ. Merchant backend එක scoped API key භාවිත කළ හැක. Webhook එකක් එයට විරුද්ධ දිශාවට යන outbound HTTP request එකකි. මෙම දෙකේ credentials සහ timeout policies වෙනස්ය.')+
        table(['Boundary එක','ඔබ අසන operational ප්‍රශ්නය'],[
            ('Internet → application','TLS, DNS, routing, auth සහ rate limit තිබේද?'),
            ('Application → provider','Egress අවසර, DNS, API quota සහ bounded timeout තිබේද?'),
            ('Provider unavailable','Core booking නවතීද? Backlog එකක් සෑදේද? Fallback එකක් තිබේද?')])+
        box('Provider එකක් optional වීම','Gemini key නොමැතිව assistant fallback response ලබාදිය හැක. එයින් live model call එක සාර්ථක බව තහවුරු නොවේ. Local Mailpit delivery එක production inbox delivery එකට සමාන නොවේ.'),evidence=('R29','R30','R22'))

    add('architecture',PART,'සම්පූර්ණ application architecture','Ten application deployments and clearly owned responsibilities',
        diagram('application-architecture',[
            ('b',225,0,210,'Browser / API client','Public entry point','teal'),('e',225,85,210,'Edge routing','Ingress → web / gateway','blue'),
            ('w',0,170,170,'Web SPA','React + nginx','teal'),('g',225,170,210,'API gateway','Auth / routing / resilience','blue'),('a',490,170,170,'AI assistant','FastAPI / LangGraph','teal'),
            ('d',75,270,510,'Java domain services','Identity / parcel / pricing / dispatch / merchant','blue'),
            ('c',0,370,200,'Tracking / notification','Event consumers','teal'),('r',235,370,190,'RabbitMQ / Redis','Messages / projections / limits','gold'),('p',465,370,195,'PostgreSQL / objects','Owned durable state','gold')],
            [('b','e'),('e','w'),('e','g'),('g','a'),('g','d'),('a','d'),('d','r'),('d','p'),('r','c')],450,'රූපය 03 · තාර්කික dependency map එකක්; සෑම internal HTTP call එකක්ම දක්වා නැත.')+
        p('Java applications අටට gateway එකත් domain services හතත් ඇතුළත් වේ. Python assistant එක සහ web එක සමඟ application units දහයකි. Shared <code>packages/common</code> library එක වෙනම running microservice එකක් නොවේ.')+
        box('Microservices වල trade-off එක','වගකීම් වෙන වෙනම deploy / scale කළ හැකි වීම වාසියකි. Network failures, event consistency, configuration සහ operational complexity වැඩිවීම එහි වියදමයි.'),evidence=('R01','R06','R35'))

    add('communication',PART,'Synchronous සහ asynchronous වැඩ','Know which result the caller is waiting for',
        diagram('communication-patterns',[
            ('b',5,10,190,'Booking request','Caller waits for a response','teal'),('p',235,10,190,'Parcel service','Calls pricing; commits DB','blue'),('q',465,10,190,'Pricing service','Immediate quote dependency','blue'),
            ('o',5,135,190,'Committed outbox','Work to publish later','gold'),('r',235,135,190,'RabbitMQ','Queued event delivery','gold'),('n',465,135,190,'Consumers','Tracking / email / webhooks','teal')],
            [('b','p'),('p','q'),('p','o'),('o','r'),('r','n')],220,'රූපය 20 · ඉහළ පේළිය request/response; පහළ පේළිය පසුව සම්පූර්ණ වන event work එකකි.')+
        p('Synchronous call එකක caller එක next result එක ලැබෙනතෙක් රැඳේ. Pricing slow වුවහොත් booking latency වැඩිවිය හැක. Asynchronous event එකක sender සහ receiver එකම මොහොතේ අවසන් වීම අවශ්‍ය නැත; broker backlog එකක් තිබිය හැක.')+
        table(['ගුණාංගය','Operational ප්‍රතිඵලය'],[
            ('Synchronous dependency','Timeout budget සහ upstream availability critical path එකට බලපායි.'),
            ('Asynchronous dependency','Retries, duplicate handling, queue lag සහ final-state observation අවශ්‍යයි.'),
            ('202 Accepted','වැඩ භාරගෙන තිබේ; final command outcome පසුව කියවිය යුතු විය හැක.'),
            ('201 Created','Primary resource එක සෑදේ; email / webhook සියල්ල සාර්ථක බව නොකියයි.')])+
        box('User expectation එක','UI එකේ pending / retry / failed state පැහැදිලි කළ යුතුය. Eventual consistency යනු unlimited delay හෝ silent data loss පිළිගැනීමක් නොවේ.'),evidence=('R12','R15','R26'))

    add('design-tradeoffs',PART,'තාක්ෂණ තේරීම්වල වාසි සහ වියදම්','Separate an observed design from an inferred reason',
        table(['තේරීම','ලැබෙන වාසිය / ගෙවිය යුතු වියදම'],[
            ('PostgreSQL ownership','Transactions සහ constraints; schema evolution සහ service boundaries පවත්වාගත යුතුය.'),
            ('Redis projection','Fast read view සහ limits; cache loss, memory සහ consistency සැලකිය යුතුය.'),
            ('RabbitMQ + outbox','Independent side effects සහ retry; duplicates, ordering සහ backlog operations අවශ්‍යයි.'),
            ('Object storage','Binary proofs සඳහා වෙනම lifecycle; DB metadata consistency සහ access policy අවශ්‍යයි.'),
            ('API gateway','Public routing / policy එක තැනක; එය critical shared dependency එකක් වෙයි.'),
            ('Separate Python assistant','AI ecosystem සහ feature isolation; වෙනම dependencies, migrations සහ operational contract.'),
            ('Kubernetes / Helm','Repeatable orchestration; platform complexity සහ stateful recovery වගකීම් වැඩිවේ.')])+
        p('මෙය ව්‍යුහයෙන් පෙනෙන trade-off විග්‍රහයකි. මුල් කර්තෘ විසින් මේ හේතු ලියූ ADR එකක් ඇතැයි නොසලකන්න. “RabbitMQ තෝරාගත්තේ ඇයි?” යන ප්‍රශ්නයට invented history එකක් වෙනුවට observed requirements සහ costs පැහැදිලි කරන්න.')+
        box('ADR එකකට කුඩා ආකෘතියක්','Context → options → decision → consequences → evidence. උදාහරණයට “Tracking read model Redis තුළ තබනවා; source parcel state වෙනමයි; cache-miss backfill සහ freshness tests අවශ්‍යයි” ලෙස ලියන්න.'),evidence=('R06','R12','R15','R20','R28','R29','R35'))

    add('repository',PART,'Repository එක කියවීමේ මාර්ගය','Start from entry points and follow one journey',
        code('''enterpriseProject/
          apps/web/                  Browser application
          services/gateway/          Public API routing
          services/*-service/        Domain services + assistant
          packages/common/           Shared events and domain types
          infra/kubernetes/          Helm, scripts and migrations
          .github/workflows/         Validation and release automation
          docs/                      Design and learning material''','Repository map')+
        table(['ඔබ සොයන්නේ','පළමුව බලන්න'],[
            ('Browser request එක','<code>apps/web/src/api/endpoints/</code> සහ <code>api/client.ts</code>'),
            ('Request යන service එක','Gateway <code>application.yaml</code> routes.'),
            ('HTTP endpoint එක','අදාළ service එකේ Controller සහ DTO.'),
            ('Business rule එක','Service / domain class සහ එහි focused tests.'),
            ('Database වෙනස','<code>src/main/resources/db/migration/</code> SQL.'),
            ('Runtime සැකසුම','Dockerfile → Helm values → rendered manifest.')])+
        p('README එකෙන් අරමුණ දැනගන්න. ඊළඟට වත්මන් source එකෙන් ක්‍රමය බලන්න. Test එකෙන් යම් branch එකක් පරීක්ෂා වී ඇතිද දැනගන්න. සැබෑ environment එකේ outcome එක වෙනම තහවුරු කරන්න.')+
        box('පැරණි docs පිළිබඳ උදාහරණය','සමහර design notes assistant history in-memory බව කියයි. වත්මන් Kubernetes setup එක SQL history සහ PostgreSQL checkpoints භාවිත කරයි. Configuration සහ source එක සමඟ document දිනයත් සසඳන්න.'),evidence=('R01','R04','R29','R42'))

    add('code-reading',PART,'Java code එක අර්ථයෙන් කියවමු','Controller → service → repository → database',
        code('''HTTP POST /api/v1/parcels
          ParcelController.book(...)
            ParcelService.book(...)
              authorize actor
              check idempotency key and payload hash
              call pricing API
              save parcel + history + outbox
            return ParcelView''','Conceptual call trace · source-derived pseudocode')+
        table(['Code වචනය','DevOps engineer කෙනෙකුට වැදගත් අර්ථය'],[
            ('Controller / DTO','HTTP contract සහ input validation boundary.'),
            ('Service','Business decisions සහ orchestration.'),
            ('Repository / entity','Database access සහ stored record mapping.'),
            ('Client','වෙනත් process එකකට network call එකක්; timeout / credentials අවශ්‍යයි.'),
            ('@Transactional','අදාළ local database work එකේ transaction boundary; remote services ඒකට ස්වයංක්‍රීයව ඇතුළත් නොවේ.'),
            ('@Scheduled / @RabbitListener','Request එකක් එනතෙක් නොසිට background වැඩ / event handling.')])+
        p('Object එකක් constructor එකෙන් ලබාදීම dependency injection ලෙස හඳුන්වයි. ඒ object එක local method call එකක්ද, HTTP client එකක්ද යන්න හඳුනාගන්න. මේ වෙනස latency සහ failure analysis සඳහා වැදගත්ය.')+
        box('Annotations වල සීමාව','Comment එකක “safe” හෝ “atomic” ලෙස තිබීම ප්‍රමාණවත් සාක්ෂියක් නොවේ. Transaction scope, exception behaviour, constraints සහ tests සසඳන්න.'),evidence=('R11','R12','R15'))

    add('runtime',PART,'Process, port සහ filesystem','The operating-system facts behind a healthy application',
        p('Java process එක JAR එක ධාවනය කර TCP port එකක listen කරයි. Python process එක ASGI server හරහා API එක සපයයි. nginx process එක static frontend files සපයයි. Container එකක් මේ processes වල runtime files සහ isolation සපයයි; Kubernetes ඒවා schedule කරයි.')+
        table(['සංකල්පය','Failure එකකදී වැදගත් දේ'],[
            ('CPU','වැඩ කිරීමට කාලය. Throttling නිසා request සහ probe latency වැඩිවිය හැක.'),
            ('Memory','Heap, threads, buffers සහ runtime overhead. Limit ඉක්මවූ විට OOM termination විය හැක.'),
            ('Port / listener','Process port එකේ listen විය යුතුය; EXPOSE ලියා තිබීම පමණක් listener එකක් සෑදීමක් නොවේ.'),
            ('UID / permissions','Non-root process එකට writable path / PVC අයිතිය ගැළපිය යුතුය.'),
            ('Environment / working directory','Missing config හෝ relative path වෙනස startup එක fail කළ හැක.'),
            ('Exit code / signal','Process exit හේතුව සහ graceful shutdown behaviour සොයා බලන්න.')])+
        p('Java apps බොහොමයක් 8080, notification 8086 සහ web 8080 භාවිත කරයි. එකිනෙකාගේ network namespaces වෙනස් නිසා මේවාට එකම internal port භාවිත කළ හැක. Host එකේ එම port එක භාවිත කරන process එකක් තිබීම වෙනම කරුණකි.')+
        box('Container image සහ running process','Image එකේ code එක තිබුණත් credentials, network route සහ storage permissions නොමැති නම් එය නිසි සේවාවක් සපයන්නේ නැත.'),('containers',),('R33','R34','R35'))

    add('http',PART,'HTTP, DNS සහ TLS මූලික සංකල්ප','Read a URL and locate the failing layer',
        code('''https://swiftdrop.example.com/api/v1/parcels?page=0
        |scheme|       host       |       path        |query|

        DNS lookup → TCP connection → TLS → HTTP → application''','Conceptual protocol path · illustrative production URL')+
        table(['කොටස','අර්ථය / failure signal'],[
            ('DNS','Host name එක IP එකකට resolve කිරීම. Name error එක application 500 එකක් නොවේ.'),
            ('TCP','Network connection එක. Refused / timeout නම් listener, route සහ policy බලන්න.'),
            ('TLS','Server identity සහ encrypted transport. Expired certificate / trust / hostname mismatch විය හැක.'),
            ('HTTP method','GET කියවීම; POST / PATCH වැනි requests business state වෙනස් කළ හැක.'),
            ('Status','400 input; 401 auth; 403 permission; 409 conflict; 429 limit; 5xx server / upstream failure.'),
            ('Headers / body','Bearer token, content type, request ID සහ JSON payload වැනි contract තොරතුරු.')])+
        p('HTTP timeout එකක් යනු “server එක කිසිම වෙනසක් කර නැත” යන සහතිකයක් නොවේ. Transaction එක commit වී response එක අතරමඟ නැතිවිය හැක. Booking idempotency අවශ්‍ය වන්නේ මේ නිසාය.')+
        box('Local ලිපිනය','මෙම project හි local Kubernetes URL එක <code>http://localhost:8088</code> ය. ඉහත HTTPS hostname එක production ඉගැන්වීමේ උදාහරණයක් පමණි.'),('http',),('R04','R06'))

    add('network',PART,'Browser network සහ cluster network','Why localhost has a different meaning in each process',
        diagram('network-boundaries',[
            ('b',5,10,190,'Browser on host','localhost:8088','teal'),('i',235,10,190,'Ingress','Published host port','blue'),('g',465,10,190,'gateway:8080','Cluster Service','blue'),
            ('p',465,120,190,'parcel-service:8080','Internal DNS name','teal'),('d',235,120,190,'postgres:5432','Private database Service','gold')],
            [('b','i'),('i','g'),('g','p'),('p','d')],205,'රූපය 04 · Browser එක internal Service DNS නමට සෘජුව නොයයි.')+
        p('<code>localhost</code> යනු request එක කරන process එක සිටින network namespace එකයි. Pod එකක් ඇතුළත localhost භාවිත කළහොත් ඔබේ laptop එක නොව එම Pod එකම අදහස් වේ. Service discovery නමක් වූ <code>parcel-service</code> browser DNS එකට සාමාන්‍යයෙන් නොපෙනේ.')+
        table(['Config එක','කවුරු resolve කළ යුතුද?'],[
            ('Frontend API base URL','User browser එක; එයට reachable public / local host URL අවශ්‍යයි.'),
            ('Gateway downstream URL','Gateway Pod එක; internal Service DNS භාවිත කළ හැක.'),
            ('Signed proof URL','Browser එක; internal object endpoint එක පමණක් ප්‍රමාණවත් නොවේ.'),
            ('Database URL','Owning service / migration Job; private network connectivity අවශ්‍යයි.')])+
        box('CORS ගැන','CORS යනු browser origin policy එකකි. <code>curl</code> / server-to-server call එකක් සාර්ථක වුවත් browser එක block විය හැක. CORS allow කිරීම application authorization එකට ආදේශයක් නොවේ.'),('service','dns'),('R03','R06'))

    add('frontend',PART,'Frontend එක deploy වන හැටි','Build-time assets, runtime config and browser-side state',
        steps(['Node build stage එක <code>npm ci</code> සහ frontend build ධාවනය කර JS / CSS / HTML artifacts සාදයි.',
            'Runtime nginx එක <code>dist</code> files සපයයි. Browser එක JavaScript ධාවනය කරයි.',
            'Container startup script එක <code>/config.js</code> ලියයි. <code>window.__SWIFTDROP__</code> values build-time fallbacks වලට ඉහළ priority ලබයි.',
            'API client එක bearer token, credentials සහ request ID සමඟ gateway වෙත requests යවයි.'])+
        table(['Behaviour එක','Operational ප්‍රතිඵලය'],[
            ('config.js / index.html: no-store','නව runtime URLs / shell පරණ cache එකෙන් නොපෙන්වීමට සැලසුම් කරයි.'),
            ('Hashed assets: long cache','එකම content hash file එක browser තුළ දිගු කාලයක් තබාගත හැක.'),
            ('SPA history fallback','Deep link refresh එකකදී nginx index.html සපයයි.'),
            ('Token in module memory','Page reload එකකදී access token නැතිවිය හැක; refresh flow වැදගත් වේ.')])+
        code('''Invoke-WebRequest -UseBasicParsing `
          -Uri http://localhost:8088/config.js
        Invoke-WebRequest -UseBasicParsing `
          -Uri http://localhost:8088/healthz''')+
        box('Frontend config එක රහස් ගබඩාවක් නොවේ','Browser වෙත යැවෙන JS, config.js සහ VITE_* values පරිශීලකයාට කියවිය හැක. Database passwords හෝ Gemini API key ඒවාට නොදමන්න.'),evidence=('R02','R03','R04','R05','R33'))

    add('auth',PART,'JWT සහ trust boundary','Prove identity, then check the requested action',
        diagram('jwt-boundary',[
            ('c',5,10,190,'Login client','Credentials → identity','teal'),('i',235,10,190,'Identity service','Signs RS256 access JWT','blue'),('g',465,10,190,'Gateway','Validates claims + signature','blue'),
            ('s',235,120,190,'Owning service','Role + resource ownership','gold')],
            [('c','i'),('i','g'),('g','s')],205,'රූපය 05 · Gateway එක පසු කළ පමණින් downstream ownership checks අහෝසි නොවේ.')+
        p('JWT payload එකේ subject, roles, expiry, issuer සහ audience වැනි claims ඇත. RS256 signature එක verify කිරීම සඳහා public key / JWKS භාවිත වේ. Payload decode කිරීම පමණක් signature validate කිරීමක් නොවේ; signed JWT එක සාමාන්‍යයෙන් encrypted secret container එකක්ද නොවේ.')+
        table(['Check එක','අසන ප්‍රශ්නය'],[
            ('Authentication','මේ user / client කවුද? Token එක විශ්වාස කළ හැකිද?'),
            ('Authorization','මේ role එකට මේ action එක අවසරද?'),
            ('Ownership','මේ customer / courier ට මේ parcel එක අයිති හෝ පැවරී තිබේද?'),
            ('Trustworthy headers','Client එවූ X-User-Id වැනි අගයන් verified token එකෙන් replace කරනවාද?')])+
        box('රහස් ආරක්ෂාව','JWT private signing key එක identity service සීමාව තුළ තබන්න. Public JWKS පළ කිරීම සාමාන්‍ය verification ක්‍රියාවලියකි. Access tokens logs හෝ screenshots තුළ නොදමන්න.'),('jwt',),('R07','R08','R09','R12'))
