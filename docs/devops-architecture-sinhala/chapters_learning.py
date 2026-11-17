from book import add,p,h,code,box,table,steps,diagram

PART='05 / තනිවම පද්ධතිය භාරගැනීමට පුහුණුව'

def populate():
    add('first-day',PART,'පළමු දවසේ code walkthrough එක','A three-hour learning session with concrete outputs',
        table(['කාලය','කරන දේ / අවසාන සටහන'],[
            ('විනාඩි 0–30','Business journey සහ architecture diagram කියවන්න. Roles, services සහ data owners අතින් ලියන්න.'),
            ('විනාඩි 30–60','R04 → R06 → R11 → R12 හරහා booking path කියවන්න. Network calls සහ local method calls වෙන්කරන්න.'),
            ('විනාඩි 60–90','R13 → R15 → R17 → R19 / R22 හරහා database සහ event path කියවන්න. Commit සහ side-effect checkpoints ලියන්න.'),
            ('විනාඩි 90–120','R33 → R35 → R36 → R37 හරහා code runtime එකට යන ආකාරය අඳින්න.'),
            ('විනාඩි 120–150','Read-only lab එක කරන්න. Context, resources සහ logs request path එකට සම්බන්ධ කරන්න.'),
            ('විනාඩි 150–180','තම වචනවලින් booking success / no-email incident එක පැහැදිලි කර unresolved questions ලියන්න.')])+
        code('''rg -n 'routes:|Path=/api/v1/parcels' `
          services/gateway/src/main/resources/application.yaml
        rg -n 'public ParcelView book|recordChange|pricing.estimate' `
          services/parcel-service/src/main/java''')+
        box('Output එක','Session එක අවසානයේ system map එකක්, request sequence එකක්, data ownership table එකක් සහ ප්‍රශ්න ලැයිස්තුවක් තිබිය යුතුය. Code lines සියල්ල මතක තබාගැනීම අවශ්‍ය නැත.'),evidence=('R04','R06','R11','R12','R15','R35','R37'))

    add('readonly-lab',PART,'Read-only lab: map එක runtime එකට සම්බන්ධ කරන්න','Inspect the intended local environment without changing data',
        code('''Set-Location F:\\EAD\\enterpriseProject
        kubectl config get-contexts
        kubectl --context kind-swiftdrop get nodes
        kubectl --context kind-swiftdrop -n swiftdrop get deploy,sts
        kubectl --context kind-swiftdrop -n swiftdrop get pods -o wide
        kubectl --context kind-swiftdrop -n swiftdrop get svc,ingress
        kubectl --context kind-swiftdrop -n swiftdrop get endpointslices
        kubectl --context kind-swiftdrop -n swiftdrop get jobs,pvc
        helm --kube-context kind-swiftdrop status swiftdrop -n swiftdrop''')+
        table(['Observation එක','ඔබ පැහැදිලි කළ යුතු දේ'],[
            ('Deployments දහය සහ Mailpit','Application workloads සහ local helper වෙන්කරන්න.'),
            ('StatefulSets හතර','Postgres, Redis, RabbitMQ සහ MinIO වල state කුමක්ද?'),
            ('Service / EndpointSlice','Stable name එක සහ Ready backing Pods අතර සම්බන්ධතාව.'),
            ('PVC / Jobs','Storage lifetime සහ bounded startup work අතර වෙනස.'),
            ('Helm revision','Current rollout identity; app-bearing සහ bootstrap revision වෙන්කරන්න.')])+
        box('රහස් output අවශ්‍ය නැත','මෙම lab එකට Secret values, kubeconfig credentials හෝ .env contents print කිරීම අවශ්‍ය නොවේ. Logs කියවන විටත් sensitive fields තබාගැනීමෙන් වළකින්න.')+
        p('Cluster එක නොමැති නම් source / rendered manifest lab එකෙන් පටන්ගන්න. Architecture එක තේරුම් ගැනීමට live environment එකට writes කිරීම අනිවාර්ය නොවේ.'),evidence=('R35','R36','R37','R42'))

    add('workflow-lab',PART,'Guided lab: එක journey එක තහවුරු කරන්න','Synthetic local writes, clear checkpoints and honest conclusions',
        code('''# First inspect the local environment.
        .\\infra\\kubernetes\\scripts\\verify-cluster.ps1
        .\\infra\\kubernetes\\tests\\smoke-tests.ps1
        # Creates a synthetic local customer and parcel.
        .\\infra\\kubernetes\\tests\\smoke-tests.ps1 -Workflow''')+
        table(['Checkpoint එක','සටහන් කළ යුතු දේ'],[
            ('Login / quote','Successful auth සහ positive LKR quote; credentials නොතබන්න.'),
            ('Booking / duplicate','එකම parcel ID; original idempotency identity සුරැකේ.'),
            ('Tracking / email','Exact tracking number සහ EMAIL / SENT outcome.'),
            ('Cancel','Business state CANCELLED ලෙස නැවත කියවිය හැක.'),
            ('Assistant','Authenticated SSE done සහ conversation history; live model call බව අනුමාන නොකරන්න.')])+
        p('Script එක synthetic writes loopback SwiftDrop host එකට සීමා කරයි. මේ පොතේ construction එකේදී මෙම lab නැවත run කර නැත; commands ඔබේ prepared local environment එකේ භාවිත කිරීමටයි.')+
        box('Notebook පිටුවකට උදාහරණය','Source/image identity, environment, test time, pass වූ checkpoints, failed checkpoint සහ untested roles ලියන්න. Token හෝ password වෙනුවට request / parcel / conversation IDs වැනි අවශ්‍ය correlation identifiers පමණක් තබන්න.')+
        p('Manual customer API payloads සහ සම්පූර්ණ local installation steps පෙර Kubernetes A–Z පොතේ ඇත. මෙහි lab එකෙන් ඒ පියවර architecture map එකට සම්බන්ධ කරන්න.'),evidence=('R12','R22','R30','R37','R42'))

    add('design-exercises',PART,'Design exercises සහ පිළිතුරු','Use failure scenarios to test your understanding',
        table(['ප්‍රශ්නය','පිළිතුරේ තිබිය යුතු අදහස'],[
            ('Booking 201, tracking මුලින් 404 ඇයි?','Async projection / backfill timing. Deadline සහ bounded polling; source booking state වෙනම බලන්න.'),
            ('Broker down නම් booking නැතිවෙයිද?','Local transaction සහ outbox policy බලන්න. Pending publish තැබිය හැක; async effects ප්‍රමාද වේ.'),
            ('Gateway restart කර Redis recover වෙයිද?','External dependency problem එක process restart එකෙන් සම්පූර්ණයෙන් විසඳෙන්නේ නැත; cascading restarts විය හැක.'),
            ('Assistant history durable නම් confirmationsත් durable ද?','වෙනස් stores දෙකකි. InMemoryOperationStore shared durable history එකේ කොටසක් නොවේ.'),
            ('Helm rollback පසු schema පරණ වේද?','Workload spec rollback; database changes වෙනමයි. Backward compatibility / restore plan අවශ්‍යයි.'),
            ('Monitoring template එක තියෙන නිසා alerts එයිද?','Endpoint format, operator, selectors, scrape, rule සහ delivery chain තහවුරු කළ යුතුය.')])+
        h('තමන්ගේ diagram එක අඳින්න')+
        p('Merchant order එකක් සෑදී parcel service එක commit කර ඇති නමුත් merchant database save එක fail වූ scenario එක අඳින්න. Request identity, upstream state සහ retry පියවර දක්වන්න. එකම idempotency key සහ payload hash වැදගත් වන්නේ කොතැනද යන්න පැහැදිලි කරන්න.')+
        box('Review කරන ක්‍රමය','ඔබේ පිළිතුරට code reference එකක්, possible failure එකක් සහ එය තහවුරු කරන test එකක් එකතු කරන්න. ඉගෙනගැනීමේ gap එකක් තිබේ නම් නිවැරදි source එකට නැවත යන්න.'),evidence=('R15','R24','R31','R39'))

    add('learning-plan',PART,'දින 30ක ඉගෙනීමේ මාර්ගය','Small practical outputs that build toward ownership',
        table(['දින','විෂය සහ සම්පූර්ණ කළ යුතු output'],[
            ('1–5','Business / architecture / repository. Output: service catalog සහ එක booking sequence diagram එකක්.'),
            ('6–10','HTTP, DNS, TLS, Linux processes සහ container runtime. Output: browser-to-database network map සහ failure examples.'),
            ('11–15','SQL transactions, outbox, queues, dedup සහ projections. Output: duplicate / lag / DLQ diagnosis note.'),
            ('16–20','Build artifacts, Helm, migrations, CI/CD සහ rollback. Output: reviewed release checklist සහ compatibility plan.'),
            ('21–25','Logs / metrics / SLOs, capacity සහ incidents. Output: dashboard signal list, one SLO proposal සහ runbook.'),
            ('26–30','Security negatives, restore planning, role workflows සහ teach-back. Output: acceptance evidence + gap register + handover pack.')])+
        p('දිනකට එක නව tool එකක් ඉගෙනගැනීමට වඩා එක real system question එකකට evidence සමඟ පිළිතුරු දෙන්න. උදාහරණයට “Tracking stale නම් මොන records සහ logs බලන්නේද?” යන්නට system map එකෙන් පිළිතුරු සොයන්න.')+
        box('Weekly teach-back','සතිය අවසානයේ කෙනෙකුට diagrams නොබලා එක flow එක විස්තර කරන්න. පසුව source map එකෙන් ඔබේ explanation එක සසඳන්න. “මම දන්නේ නැහැ” ලෙස සටහන් කළ ප්‍රශ්නයකට owner සහ next check එකක් දාන්න.')+
        p('Fault injection සහ restore labs සඳහා isolated stage / test environment භාවිත කරන්න. Shared local clusters සහ real customer data අහඹුව වෙනස් කිරීම ඉගෙනීමේ අභ්‍යාසයක් නොකරන්න.'))

    add('handover',PART,'Project එක භාරගැනීමට අවශ්‍ය pack එක','Make understanding visible and maintainable',
        table(['Document / artifact එක','අඩංගු විය යුතු දේ'],[
            ('System context + service catalog','Actors, public boundaries, responsibilities, ports සහ owners.'),
            ('Dependency / data map','HTTP calls, events, authoritative stores, replicas සහ providers.'),
            ('Release contract','Artifact identity, config, schema order, gates සහ rollback limits.'),
            ('Operational runbooks','HTTP failure, stuck events, login, restore සහ credential rotation.'),
            ('Observability plan','SLIs / SLOs, signals, alert owners සහ tested delivery path.'),
            ('Security / data plan','Trust boundaries, retention, backup / restore, least privilege සහ negative tests.'),
            ('Evidence + open risks','Verified behaviour, dated results, remaining gaps සහ acceptance criteria.')])+
        p('DevOps ownership යනු operational facts නැවත නැවත තහවුරු කළ හැකි ආකාරයකින් පවත්වාගැනීමයි. Code වෙනස් වූ විට architecture diagrams, source references සහ runbooks වෙනස් විය යුතුය.')+
        box('තමන්ට දිය හැකි අවසාන පරීක්ෂණය','Booking එක fail වූ විට මම බලන්නේ කොහෙද? Data අයිති කාටද? Duplicate effect එකක් වළක්වන්නේ කෙසේද? Release එක recover කරන්නේ කෙසේද? තව නොදන්නා දේ සහ එය තහවුරු කරන next check එක මොකක්ද?')+
        p('මෙම පොත editable sources, source hashes සහ build validation සමඟ ලබාදී ඇත. ඔබේ සැබෑ environment evidence එක එකතු කර එය තමන්ගේ maintained project handbook එකක් ලෙස දිගටම භාවිත කරන්න.'))
