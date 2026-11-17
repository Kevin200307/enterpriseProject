from book import add,p,h,code,box,table,steps,diagram

PART='04 / DevOps මෙහෙයුම් සහ failures තේරුම් ගැනීම'

def populate():
    add('failure-map',PART,'Failure එකක බලපෑම කොතැනටද?','Dependency impact is more useful than a list of Pods',
        diagram('failure-impact',[
            ('i',5,10,190,'Identity','New login / token operations','blue'),('p',235,10,190,'Pricing','New booking quote','blue'),('d',465,10,190,'Parcel DB','Booking / parcel state','gold'),
            ('b',235,120,190,'Customer booking','Depends on critical path','teal'),('r',5,235,190,'RabbitMQ','Async effects can lag','gold'),('a',235,235,190,'Assistant provider','Optional chat degradation','gold'),('t',465,235,190,'Redis','Tracking / limits affected','gold')],
            [('i','b'),('p','b'),('d','b'),('b','r'),('b','t')],315,'රූපය 19 · මෙය impact reasoning diagram එකකි; සෑම request එකක්ම identity API එකට සෘජුව යන බව නොකියයි.')+
        p('JWT validation එක cached public keys භාවිත කළ හැකි නිසා identity unavailable වූ පළමු තත්පරයේ සියලු authenticated requests අසාර්ථක විය යුතු නැත. නමුත් අලුත් login, refresh සහ key fetch paths බලපායි. Dependency impact එක operation අනුව ලියන්න.')+
        table(['Dependency fail නම්','මුලින් බලන user symptom'],[
            ('Pricing / parcel DB','නව booking errors / latency.'),('RabbitMQ','Outbox backlog, tracking / notification delay.'),
            ('Redis','Read projection / rate-limit behaviour; path එක අනුව වෙනස් වේ.'),('Gemini','Model response fail / fallback; core API වෙනම තහවුරු කරන්න.')])+
        box('Blast radius','එක් failure එකකින් බලපාන users, journeys සහ data ප්‍රමාණයයි. මේ සීමාව දැනගැනීමෙන් mitigation එක නිවැරදි workload එකට යොමු කළ හැක.'),evidence=('R06','R12','R15','R29','R30'))

    add('probe-semantics',PART,'Healthy, Ready සහ වැඩ කරනවා යන්න','Probe design should match the action Kubernetes will take',
        table(['Probe එක','අසන ප්‍රශ්නය / ප්‍රතිචාරය'],[
            ('Startup','Process එක තවම initialize වෙනවාද? සාර්ථක වනතෙක් අනෙක් probes නවතයි.'),
            ('Readiness','දැන් සාමාන්‍ය requests භාරගත හැකිද? Fail නම් Service traffic වලින් ඉවත් වේ.'),
            ('Liveness','Process එක restart කිරීම ප්‍රයෝජනවත්ද? Repeated fail නම් container restart වේ.'),
            ('Business smoke','සැබෑ user journey එක අවසන් වෙනවාද? Database, events සහ providers ද සම්බන්ධ විය හැක.')])+
        p('Gateway aggregate health එක downstream services පරීක්ෂා කරයි. එය liveness ලෙස යොදාගත් විට slow dependency එකක් healthy gateway එක restart කිරීමට හේතු විය හැක. Current chart එක dedicated readiness / liveness groups භාවිත කරයි.')+
        box('Local timing පාඩම','Shared CPU මත Java cold starts නිසා local startup budget එක විනාඩි 10ක් දක්වා ඉඩ දෙයි. Production default එක කෙටි වේ. Blind timeout increase වෙනුවට measured startup time සහ resource pressure බලන්න.')+
        p('Broker / database probes සඳහාත් restart කිරීමෙන් recovery වේද යන්න අසන්න. RabbitMQ local strategy එක AMQP TCP startup/readiness සහ process-exit restart handling භාවිත කරයි. Healthy broker එකට නිරන්තර diagnostic process launch කිරීමෙන් බර වැඩිවිය හැක.')+
        box('හොඳ health report එක','“Deployment Ready” සහ “booking + email success” වෙන වෙනම report කරන්න. Assistant UP route එකෙන් සජීවී database connectivity තහවුරු නොවන බවත් සලකන්න.'),('probes','boot','rabbit'),('R08','R32','R35','R42'))

    add('timeouts',PART,'Timeout, retry සහ circuit breaker','Keep failures bounded and avoid amplifying them',
        table(['Mechanism එක','කරන දේ / වැරදි භාවිතය'],[
            ('Connect timeout','Connection පිහිටුවීමට ඉඩදෙන කාලය.'),
            ('Response timeout','Response / operation සඳහා budget එක. Streaming සහ uploads සඳහා වෙනම policy අවශ්‍ය විය හැක.'),
            ('Retry','තාවකාලික failure එකකට නැවත උත්සාහ කිරීම. Writes සඳහා idempotency සහ retryable error policy අවශ්‍යයි.'),
            ('Backoff / jitter','එකවර clients සියල්ල retry කර load spike එකක් සෑදීම අඩු කරයි.'),
            ('Circuit breaker','Repeated failure පසු upstream calls තාවකාලිකව නවතා recovery probes කරයි.'),
            ('Fallback','User ට actionable failure / degraded result ලබාදෙයි; fabricated success එකක් නොවිය යුතුය.')])+
        p('Gateway source එකේ සාමාන්‍ය connect timeout තත්පර 2ක් සහ response / time limiter defaults තත්පර 10ක් ඇත. Pricing / upload වැනි routes වල වෙනම settings ඇත. සමස්ත budget එකට edge, gateway, downstream HTTP client සහ database waits එකට සලකා බලන්න.')+
        box('Retry amplification උදාහරණය','Layers තුනක retries තුන බැගින් තිබේ නම් එක user action එකෙන් අවස්ථා අනුව downstream attempts 27ක් දක්වා විය හැක. මෙය ඉගැන්වීමේ ගණනයකි; actual project retry counts ලෙස නොසලකන්න.')+
        p('SSE එක දිගටම විවෘතව තබාගැනීම සඳහා Ingress timeout පමණක් වැඩි කිරීම ප්‍රමාණවත් නොවිය හැක. Gateway route සහ breaker / time limiter behaviour ද දිගු stream test එකකින් තහවුරු කරන්න.'),evidence=('R06','R15','R25','R26'))

    add('capacity',PART,'Capacity ගණනය කරන්නේ කෙසේද?','Start with concurrency, connection pools and queue drain rates',
        table(['උදාහරණය','ගණනය / තේරුම'],[
            ('Service replicas 3 × pool 10','එම service DB එකට app connections උපරිම 30ක් විය හැක; jobs / admin connections ද reserve කරන්න.'),
            ('40 requests/s × average 0.2 s','Steady-state average in-flight requests ≈ 8. මෙය rough concurrency estimate එකක්; exact DB pool sizing නොවේ.'),
            ('Queue 10,000; process 200/s; arrive 150/s','Net drain 50/s නම් catch-up ≈ 200 s; rates ස්ථාවර බව උපකල්පනය කරයි.'),
            ('Memory limit','Java heap එකට අමතර native memory, threads සහ buffers සඳහා ඉඩ අවශ්‍යයි.'),
            ('HPA scale out','CPU capacity වැඩිවුණත් DB pools, provider quotas සහ process-local state constraints බලපායි.')])+
        p('Requests-per-second එක පමණක් capacity metric එකක් නොවේ. Slow provider එකක් වැඩි in-flight requests තබා memory / threads / pools පිරවිය හැක. Mean latency සමඟ p95 / p99 සහ saturation මැන බලන්න.')+
        code('''# Requires a functioning metrics API.
        kubectl --context kind-swiftdrop top nodes
        kubectl --context kind-swiftdrop -n swiftdrop top pods
        kubectl --context kind-swiftdrop -n swiftdrop get hpa''')+
        box('Cost trade-off','Replicas වැඩි කිරීම compute cost පමණක් නොව database connections, external requests සහ logs / metric volume ද වැඩි කළ හැක. මුලින් bottleneck එක මැන බලන්න.'),('resources','hpa'),('R06','R35'))

    add('observability',PART,'Logs, metrics සහ traces එකට භාවිත කරන්න','Each signal answers a different operational question',
        table(['Signal එක','කියන දේ / උදාහරණය'],[
            ('Logs','විශේෂ සිදුවීමක විස්තර: request ID, event ID, status, error class.'),
            ('Metrics','කාලය තුළ හැසිරීම: request rate, latency histogram, queue depth, pool usage.'),
            ('Trace','එක් request එක dependencies හරහා ගත කළ කාලය සහ parent/child spans.'),
            ('Events / audit','Kubernetes scheduling event හෝ authorized business mutation ගැන සටහන.'),
            ('Business probe','Synthetic booking → tracking → notification වැනි user-visible evidence.')])+
        p('Request ID තිබීම distributed tracing stack එකක් තිබීමකට සමාන නොවේ. Logs correlate කළ හැකි වීම වාසියකි; complete spans, collector, sampling සහ backend යෙදීම වෙනම කාර්යයකි. Outbound event එකට request ID නොතිබුණත් event ID / parcel ID හරහා සම්බන්ධ කළ හැක.')+
        code('''kubectl --context kind-swiftdrop -n swiftdrop logs `
          deployment/gateway --tail=100
        kubectl --context kind-swiftdrop -n swiftdrop get events `
          --sort-by=.metadata.creationTimestamp''')+
        box('Label cardinality','User ID, parcel ID හෝ raw URL query එක සෑම metric label එකකටම දැමීමෙන් time series ගණන විශාල විය හැක. High-cardinality detail logs / traces සඳහා තබා bounded labels metrics සඳහා භාවිත කරන්න.')+
        p('Log storage retention එක Pod lifetime එකෙන් වෙන්විය යුතුය. PII, tokens සහ raw chat secrets ඉවත් කර useful correlation fields තබන්න.'),('otel','logs'),('R04','R19','R22','R39'))

    add('metrics-reality',PART,'Monitoring YAML එක තිබුණත් වැඩ කරනවාද?','Compare the scrape contract with the application implementation',
        table(['Source evidence එක','මෙහි ඉගෙනගන්නා දේ'],[
            ('Chart assistant scrape path: /metrics','ServiceMonitor එක එම HTTP path එකට Prometheus-compatible response බලාපොරොත්තු වේ.'),
            ('Assistant route: /actuator/metrics','වත්මන් health router එක JSON counter snapshot සපයයි; එය /metrics exporter එකක් ලෙස නොසලකන්න.'),
            ('Java path: /actuator/prometheus','Micrometer registry, HTTP exposure සහ authorization එක ගැළපේද තහවුරු කරන්න. Gateway baseline exposure health පමණි.'),
            ('ServiceMonitor / PrometheusRule','CRDs, operator, selectors, namespaces සහ scrape permission අවශ්‍යය.'),
            ('Assistant readiness returns UP','එම route එකේ runtime-enabled metadata පමණක් ඇත; live DB / Redis connectivity test එකක් නොවේ.')])+
        p('මේවා වත්මන් source අතර පෙනෙන integration checks වේ. පොත සෑදීමේදී live monitoring install / fix කර නැත. Endpoint එකේ HTTP status, content type, metric format සහ actual scraped series හරහා acceptance තහවුරු කරන්න.')+
        steps(['Expected metric එකක් සහ එය expose කරන owner එක තෝරන්න.',
            'Internal endpoint එක authorized diagnostic path එකකින් කියවන්න.',
            'Prometheus target UP ද සහ series update වෙනවාද බලන්න.',
            'Test alert එක firing → notification → recovery ලෙස සම්පූර්ණද බලන්න.'])+
        box('Diagram සහ reality','Chart template එකක් තිබීම operational capability එකක් සම්පූර්ණ වී ඇති බවට සාක්ෂියක් නොවේ. Runtime wiring සහ tested outcome දෙකම අවශ්‍යය.','warning'),('prometheus',),('R06','R32','R39'))

    add('slo',PART,'SLI, SLO සහ error budget','Measure the experience you intend to protect',
        table(['පදය','අර්ථය / ඉගැන්වීමේ උදාහරණය'],[
            ('SLI','මැනෙන indicator එක: valid booking requests අතර සාර්ථක ප්‍රතිශතය.'),
            ('SLO','ඒ indicator එකට target එක සහ window එක: උදාහරණයට 30 දින තුළ 99.9%.'),
            ('Error budget','ඉලක්කයට ගැළපෙන අසාර්ථක කොටස: requests 1,000,000 නම් 0.1% = 1,000.'),
            ('Latency objective','Target threshold එකකට අඩුවෙන් අවසන් වන requests ප්‍රතිශතය.'),
            ('Freshness objective','Parcel change පසු tracking / notification පෙනීමට ගත වන කාලය.'),
            ('Burn rate','Budget එක සාමාන්‍යයට වඩා කෙතරම් වේගයෙන් වැයවෙනවාද යන්න.')])+
        p('මෙහි සංඛ්‍යා project එකේ agreed SLA එකක් නොවේ. මුලින් valid request කියන්නේ මොකක්ද, user input errors exclude කරන්නේ කෙසේද, window එක සහ missing telemetry සලකන්නේ කෙසේද යන්න කණ්ඩායමෙන් තීරණය කරන්න.')+
        box('Alert එකකට user impact සම්බන්ධ කරන්න','Pod එකක් restart වූ පමණින් page කිරීමත්, booking failure rate දිගටම ඉහළ ගියත් alert නොයැවීමත් දෙකම ගැටලුය. Significant budget consumption සහ actionable owner / runbook එකට alert යොමු කරන්න.')+
        p('Async system එකකට HTTP availability පමණක් ප්‍රමාණවත් නැත. Broker backlog නිසා email පැය ගණනක් ප්‍රමාද වුවත් booking endpoint එක 200 / 201 ලබාදිය හැක.'),('slo','alerts'))

    add('incident-http',PART,'Incident 01: UI load වේ, API fail වේ','Follow the request path and identify the first failing boundary',
        steps(['Affected URL, method, approximate time සහ request ID සටහන් කරන්න; bearer token copy නොකරන්න.',
            'Browser Network panel එකේ response status සහ CORS / network error වෙන්කර බලන්න.',
            'Ingress host/path සහ gateway Service endpoints පරීක්ෂා කරන්න.',
            'Gateway log එකෙන් route / upstream outcome හඳුනාගන්න.',
            'Owning service logs, probe state සහ downstream dependency එක සසඳන්න.',
            'මූල හේතුවට සීමිත fix / rollback පසු එම business action එක නැවත තහවුරු කරන්න.'])+
        table(['ලක්ෂණය','ඊළඟ පරීක්ෂණය'],[
            ('DNS / TLS error','Client සිට edge දක්වා name / trust chain.'),
            ('404','Host / path mismatch ද නැත්නම් application resource-not-found ද?'),
            ('502 / 503','Gateway fallback, upstream listener, Service port, readiness.'),
            ('401 / 403','Auth / role / ownership; network restart එකක් ලෙස නොසලකන්න.'),
            ('UI only stale','config.js / asset caching සහ browser origin.')])+
        code('''kubectl --context kind-swiftdrop -n swiftdrop get svc,endpointslices
        kubectl --context kind-swiftdrop -n swiftdrop get pods -o wide
        kubectl --context kind-swiftdrop -n swiftdrop logs `
          deployment/gateway --tail=100''')+
        box('රූපය මත diagnosis කරන්න','Network view එකේ client → edge → gateway → domain → DB arrows එකින් එක තහවුරු කරන්න. ඒකෙන් “සියල්ල restart කරන්න” යන අනුමානයට වඩා සීමිත evidence ලැබේ.'),evidence=('R03','R04','R06','R08'))
    recovery_and_review()

def recovery_and_review():
    add('incident-events',PART,'Incident 02: booking ඇත, email නැත','Trace one parcel and one event across asynchronous checkpoints',
        code('''Parcel committed?
          → Outbox record pending or published?
            → Broker accepted and routed?
              → Consumer deserialized and processed?
                → Preference allowed the channel?
                  → Provider accepted? Delivery log updated?''','Diagnostic decision path · read-only investigation first')+
        table(['Checkpoint එක','සෙවිය යුතු සාක්ෂිය'],[
            ('Parcel / outbox','Exact parcel ID, event ID සහ outbox age.'),
            ('Broker','Queue depth, unacked, bindings, DLQ සහ consumer availability.'),
            ('Consumer','Event type/version, JSON timestamp parsing සහ sanitized exception.'),
            ('Business policy','Notification disabled ද, preferences අනුව skipped ද?'),
            ('External send','SENT / FAILED status, retry count සහ SMTP / SMS response.')])+
        p('Queue depth 0 වීමෙන් email ගියා බව තහවුරු නොවේ. Message එක DLQ වෙත ගොස් තිබිය හැක, channel skipped විය හැක, නැත්නම් application log එක FAILED විය හැක. Exact parcel එකට business outcome නැවත කියවන්න.')+
        box('Replay කිරීමට පෙර','Malformed event fix එක regression test එකකින් තහවුරු කර target delivery පමණක් replay කරන්න. Unrelated messages purge කිරීම හෝ මූල booking එක අලුත් key එකකින් නැවත යැවීමෙන් data ගැටලු වැඩිවිය හැක.','warning'),evidence=('R12','R15','R17','R22','R23'))

    add('incident-auth',PART,'Incident 03: login / refresh අසාර්ථකයි','Separate identity, browser session and authorization failures',
        table(['Observation එක','Hypothesis / ඊළඟ check එක'],[
            ('Login POST 401','Credentials / user status / identity business rule; password logs නොගන්න.'),
            ('Login 200, refresh 401','Set-Cookie accepted ද? Cookie domain/path, SameSite, Secure සහ credentials mode බලන්න.'),
            ('සියලු APIs token reject','Issuer / audience / signing key / JWKS network සහ clock skew බලන්න.'),
            ('එක් endpoint එකක් 403','Role / scope / ownership mismatch විය හැක.'),
            ('UI page visible, action denied','Client route guard සහ server authorization වෙන්ව ක්‍රියා කරයි.'),
            ('Key rotation පසු failures','Overlapping public keys, kid, cache refresh සහ issued token lifetimes බලන්න.')])+
        p('JWT payload එක public decoder website එකකට paste නොකරන්න. Token string එක local diagnostic tools තුළ පමණක් සීමා කර claims validate කරන්න. Signature verification සහ payload decode වෙන වෙනම දේවල්ය.')+
        steps(['Failing request එකේ origin සහ response code සටහන් කරන්න.',
            'Sensitive values නැතිව expected issuer / audience configuration සසඳන්න.',
            'Browser cookie rejection reason සහ server-side request ID logs බලන්න.',
            'Negative authorization test එකක් සහ authorized happy path එකක් පසු fix accept කරන්න.'])+
        box('Change එකේ අවදානම','Frontend API URL එක external hostname එකකට මාරු කිරීමෙන් JWT issuer setting එකත් එලෙසම මාරු විය යුතු බව අනුමාන නොකරන්න. Token issuer contract එක සහ signing service configuration එක සසඳන්න.'),('jwt',),('R03','R04','R05','R08','R09','R10'))

    add('backup',PART,'Backup, restore සහ disaster recovery','Protect the data needed to explain and complete a delivery',
        table(['Data කණ්ඩායම','Recovery සැලැස්මේ ප්‍රශ්නය'],[
            ('Service databases','Point-in-time restore, schema history, ownership සහ credentials කෙසේ ගැළපේද?'),
            ('Proof objects','Metadata reference කරන object bytes එම recovery point එකේ තිබේද?'),
            ('Outbox / commands','In-flight events සහ pending assignments restore පසු නැවත reconcile කරන්නේ කෙසේද?'),
            ('Redis state','Rebuild කළ හැකි projection සහ limits / transient state වෙනම හඳුනාගන්න.'),
            ('Identity / secrets','Restore target එකට signing keys / credential access නිසි ලෙස තිබේද?'),
            ('Assistant history / checkpoints','Retention සහ conversation/checkpoint consistency policy එක කුමක්ද?')])+
        p('RPO යනු අහිමි වීමට ඉඩදෙන data කාල පරාසයයි. RTO යනු නැවත සේවාව ලබාදීමට ඉඩදෙන කාලයයි. ඒවා backup schedule එකක් තෝරාගැනීමෙන් පමණක් සනාථ නොවේ; restore exercise එකකින් මැනිය යුතුය.')+
        steps(['Isolated target එකකට backup restore කරන්න.',
            'Owning schema / artifact compatibility සනාථ කරන්න.',
            'Sample parcel, proof, tracking සහ notification consistency පරීක්ෂා කරන්න.',
            'In-flight work replay / reconciliation කළ පසු measured results සටහන් කරන්න.'])+
        box('Current evidence','Assistant restart පසු history රැඳුණු local check එක තිබේ. Full off-cluster restore සහ disaster recovery drill තව අවශ්‍යයි. PVC එක backup එකක් නොවේ.'),('postgres','storage'),('R12','R15','R27','R28','R29','R42'))

    add('security',PART,'Security boundaries පහක් අඳින්න','Identity, API, process, network and data ownership',
        table(['Boundary එක','Threat එක / control එක'],[
            ('Browser → API','Stolen token, unauthorized action; JWT checks, ownership, rate limits සහ safe session handling.'),
            ('Gateway → service','Spoofed identity headers; JWT validation සහ trusted header replacement.'),
            ('Workload → Kubernetes API','Unneeded control-plane access; dedicated ServiceAccounts සහ token automount disable.'),
            ('Service → network','Lateral access / SSRF; enforcing CNI, strict allow rules සහ bounded outbound clients.'),
            ('Data / logs / backups','PII / key exposure; distinct credentials, encryption, access controls සහ retention.')])+
        p('Current chart එක non-root, dropped capabilities සහ RuntimeDefault seccomp යොදයි. NetworkPolicy objects ඇතත් default Kind CNI එක enforce නොකරයි. Release-wide internal allow rule තිබෙන නිසා strict per-service isolation තව harden කළ යුතුය.')+
        box('Negative test එකක් අවශ්‍යයි','Allowed request එකක් pass වීම පමණක් security proof නොවේ. වෙනත් customer parcel, revoked API key, wrong role සහ unexpected internal connection deny වෙනවාද තහවුරු කරන්න.')+
        p('Vulnerability scan එක code-level authorization හෝ prompt/tool safety සියල්ල ආවරණය නොකරයි. Source review, dependency policy, runtime isolation සහ business permission tests එකට අවශ්‍ය වේ.'),('network','security','rbac'),('R07','R08','R12','R25','R38'))

    add('production-gaps',PART,'Production පෙර පැහැදිලි කළ යුතු දේ','A source-based gap register with acceptance criteria',
        table(['Area එක','Gap / acceptance criterion'],[
            ('Edge controller','Community ingress-nginx maintenance 2026 මාර්තුවෙන් අවසන්. Maintained replacement සහ REST/SSE/WS/upload regression tests.'),
            ('Network isolation','Enforcing CNI + narrow service matrix; allowed සහ denied probes pass.'),
            ('Monitoring','Exporter path / format / exposure / selectors ගැළපීම; real scrape සහ delivered alert.'),
            ('AI writes / scale','Runtime wiring, durable pending operations සහ atomic shared claim; role E2E.'),
            ('Business journeys','Merchant / dispatcher / courier / admin lifecycle tests සහ failure reconciliation.'),
            ('Data / release','Restore exercise, secret sync, managed connectivity සහ digest / signature policy.'),
            ('Capacity / reliability','Defined SLOs, load tests, provider outage සහ node-failure recovery.')])+
        p('මෙම ලැයිස්තුව production භාරගැනීමට අවශ්‍ය evidence එක පැහැදිලි කරයි. GitHub workflow හෝ example values තිබීමෙන් external services provision වී හෝ release certification ලැබී ඇතැයි නොසලකන්න.')+
        box('Current book scope','මෙම පොත සෑදීමේදී code / cluster gaps fix කර නැත. එම source differences හඳුනාගන්නා ආකාරය සහ acceptance criteria ලියා ඇත.'),('retirement',),('R30','R31','R32','R38','R39','R41','R42'))

    add('change-audit',PART,'AI-assisted code change එකක් audit කරමු','Turn an unfamiliar implementation into a reviewable change',
        steps(['User-visible behaviour එක sentence එකකින් ලියන්න: “කවුරු, කුමන action එක, කුමන result එකද?”',
            'Entry point එකෙන් actual runtime call graph එක සොයන්න. Registry / class එකක් තිබුණත් එය wire වී තිබේද බලන්න.',
            'Data ownership, transaction boundary සහ external side effects සටහන් කරන්න.',
            'Failure, duplicate, concurrent request සහ authorization-negative paths හඳුනාගන්න.',
            'Config names, image contents, migration order සහ rollout compatibility review කරන්න.',
            'Focused tests පසු real-stack acceptance කරන්න; observed result සමඟ docs update කරන්න.'])+
        table(['ප්‍රකාශය','ඉල්ලිය යුතු evidence එක'],[
            ('“Feature එක තියෙනවා”','Reachable endpoint / UI → executing handler → result.'),
            ('“Persistent”','Configured durable backend + restart / restore scope.'),
            ('“Secure”','Trust boundary + denial tests + least privilege.'),
            ('“Scales”','Shared state correctness + measured load + dependency budgets.'),
            ('“Production ready”','Operational ownership, monitoring, restore සහ approved release gates.')])+
        box('හොඳ review note එක','“Tool class exists, but the chat runner does not execute this path; end-to-end wiring remains” යන්න “AI වැඩ කරන්නේ නැහැ” කියන පොදු ප්‍රකාශයට වඩා නිවැරදි සහ කළ හැකි කාර්යයක් දක්වයි.'),evidence=('R29','R30','R31','R39'))

    add('test-confidence',PART,'Test එකක් කියන්නේ කුමන සාක්ෂියක්ද?','Match each test to the risk it can actually cover',
        table(['Test වර්ගය','ඔප්පු කළ හැකි දේ / සීමාව'],[
            ('Unit','State machine, payload hash හෝ converter branch එකක්; network topology නොවේ.'),
            ('Contract','DTO / event schema / endpoint expectations ගැළපීම.'),
            ('Integration','Database / broker / client adapters සමඟ සැබෑ interaction එකක්.'),
            ('Manifest / script','Rendered resource structure සහ orchestration branches; live connectivity නොවේ.'),
            ('End-to-end','Entry point එකෙන් business outcome එක දක්වා selected journey එක.'),
            ('Fault / load / restore','Failure correctness, capacity සහ recoverability; happy-path pass එකට අමතරයි.')])+
        p('Recorded local customer flow එක register/login → pricing → duplicate booking → tracking → EMAIL SENT → cancellation → SSE/history තහවුරු කරයි. ඒකෙන් merchant API key හෝ courier proof lifecycle සියල්ල pass වී ඇතැයි කියන්න බැහැ.')+
        box('Failing tests සඟවන්න එපා','පෙර full assistant suite එකේ passes 89ක්, live-provider skip එකක් සහ existing courier-tool registry expectation failure එකක් තිබුණි. Focused checks pass වීම full suite clean බවට ආදේශයක් නොවේ.')+
        p('Evidence record එකට source / image identity, test environment, exact command, outcome සහ untested cases දාන්න. පොතේ source hashes current working tree එක හඳුනාගනී; runtime evidence එක dated implementation record එකෙන් වෙන්කර ඇත.'),evidence=('R40','R42'))
