from book import add,p,h,code,box,table,steps,diagram

PART='02 / Requests, business rules සහ data ගමන'

def populate():
    add('sessions',PART,'Session, refresh සහ roles','A login response is the beginning of a session lifecycle',
        p('Login එක access token එක body එකෙන් සහ refresh token එක HttpOnly cookie එකෙන් ලබාදෙයි. Frontend access token එක memory තුළ තබයි. API request එකකට 401 ලැබුණහොත් එකවර refresh call එකක් share කර token එක නැවත ලබාගෙන request එක එක් වරක් retry කිරීමට client එක සැලසුම් කර ඇත.')+
        table(['අවස්ථාව','ඔබ නිරීක්ෂණය කළ යුතු දේ'],[
            ('Browser reload','Access token memory අලුත් වේ; refresh cookie browser එකෙන් යැවෙනවාද බලන්න.'),
            ('Cross-origin deployment','Allowed origin, credentials, cookie domain/path සහ SameSite/Secure flags ගැළපේද?'),
            ('Role / status වෙනස','පරණ access token expiry සහ නව token claims අනුව අවසර වෙනස් වන ආකාරය පරීක්ෂා කරන්න.'),
            ('Refresh replay','Source එක rotation සහ replay handling අරමුණු කරයි; concurrent requests සහ failure transaction behaviour tests මඟින් තහවුරු කරන්න.'),
            ('Logout / compromised session','Refresh revocation සහ issued access-token policy වෙන වෙනම බලන්න.')])+
        p('User කෙනෙකුට COURIER role තිබුණත් dispatch courier profile එක අවශ්‍ය විය හැක. Merchant approval, organisation ID සහ token claims එකිනෙකට ගැළපිය යුතුය. Role label එකක් තිබීමෙන් business entity එක ස්වයංක්‍රීයව සෑදෙන්නේ නැත.')+
        box('Diagnosis එකට shortcut එකක්','Login POST එක සාර්ථක වුවත් ඊළඟ request එක fail නම් token storage, refresh cookie සහ downstream claims බලන්න. පළමු diagnosis ලෙස database password එක මාරු නොකරන්න.'),evidence=('R04','R05','R09','R10','R27'))

    add('data-ownership',PART,'කවර data එකට අයිතිය කාටද?','Database-per-service and separate read projections',
        table(['Owner','Authoritative state / භාවිතය'],[
            ('Identity','Users, roles සහ refresh-token records.'),
            ('Parcel','Parcel aggregate, fee snapshot, history, proofs metadata සහ outbox.'),
            ('Pricing','Zones, city mappings සහ effective pricing rules.'),
            ('Dispatch','Courier roster, assignment records, command state සහ queue projection.'),
            ('Merchant','Organisation, API keys, order references සහ webhook deliveries.'),
            ('Notification','Preferences, delivery logs සහ retry status.'),
            ('Tracking','Redis read projection; parcel source state එකේ privacy-reduced පිටපත.'),
            ('Assistant','Configured SQL conversation storage සහ separate checkpoint tables.')])+
        p('Tracking record එක සහ parcel record එක වෙනම තිබීම අහඹු duplication එකක් නොවේ. එක user-friendly read model එකකි; අනෙක business state එකේ source of truth එකයි. Projection එක තාවකාලිකව පසුපසින් තිබිය හැක.')+
        box('Database එකක් share කරන local host එක','Local PostgreSQL instance එක databases කිහිපයක් host කළ හැක. එය service ownership අහෝසි නොකරයි. වෙනත් service එකක table එක සෘජුව edit කිරීමෙන් එහි validation, audit සහ events මඟහැරේ.','warning')+
        p('Production ownership diagram එකට database account, backup owner, retention සහ restore procedure එකත් සම්බන්ධ කරන්න. Service එකකට තිබෙන UUID reference එක cross-database foreign key එකකට සමාන නොවේ.'),evidence=('R12','R20','R22','R24','R27','R29'))

    add('sql-transactions',PART,'Database සහ transaction එක සරලව','Rows, constraints, commits and concurrency',
        p('Table එකක business records rows ලෙස තබයි. Primary key එක row එක හඳුනාගනී. Unique constraint එක duplicate identity වලක්වයි. Index එක කියවීම වේගවත් කළ හැකි නමුත් writes සහ storage සඳහා වියදමක් ඇත. Transaction එක commit වනතෙක් එහි වෙනස්කම් ස්ථිර outcome එකක් ලෙස නොසලකන්න.')+
        code('''BEGIN;
        -- conceptual example: one owning database
        INSERT INTO parcels (...);
        INSERT INTO parcel_history (...);
        INSERT INTO outbox_events (...);
        COMMIT;''','SQL pseudocode · not runnable table definitions')+
        table(['උපකරණය','මෙහි අරමුණ / සීමාව'],[
            ('Transaction','Local database change group එක commit / rollback කිරීම.'),
            ('Unique customer + booking key','එකම user booking identity එක නැවත store නොකිරීම.'),
            ('Optimistic version','එකවර updates අතර conflict හඳුනාගැනීම; parcel entity එකේ @Version ඇත.'),
            ('Pessimistic lock','Outbox publisher වැනි code path එකක එකම records මත competing writers සම්බන්ධීකරණය.'),
            ('Connection pool','Connections සීමිතව නැවත භාවිත කරයි; saturation නිසා app slow විය හැක.')])+
        box('Network call එක transaction එකට ඇතුළත් නොවේ','PostgreSQL transaction එකට RabbitMQ, MinIO හෝ වෙනත් service database එක ස්වයංක්‍රීයව commit නොවේ. Partial failure සඳහා outbox, idempotency සහ reconciliation අවශ්‍යය.'),('transactions',),('R12','R13','R16','R27'))

    add('data-model',PART,'Data model එකේ සම්බන්ධතා','Logical entities inside an owner and UUID references across owners',
        diagram('logical-data-model',[
            ('u',225,0,210,'Identity user','Separate owning database','blue'),('p',225,105,210,'Parcel aggregate','customerId / assigneeId references','teal'),
            ('h',5,225,190,'Status history','Transitions and timestamps','gold'),('f',235,225,190,'Proof metadata','Parcel ID + object reference','gold'),('o',465,225,190,'Outbox event','Event ID + payload + state','gold')],
            [('u','p'),('p','h'),('p','f'),('p','o')],305,'රූපය 21 · Logical relationships පමණි. User → parcel arrow එක cross-database SQL foreign key එකක් නොවේ.')+
        p('Customer ID එක identity service user එකක් හඳුනාගන්නා UUID reference එකකි. Parcel database එකේ එයට අදාළ status history සහ proof metadata වැනි records තිබේ. Outbox record එක business transaction එකෙන් පසුව publish කිරීමට අවශ්‍ය event payload රඳවයි.')+
        table(['ID වර්ගය','භාවිතය'],[
            ('Parcel ID','Business aggregate එකේ stable internal identity.'),
            ('Tracking number','User-facing lookup identity; privacy-safe API එක වෙනමය.'),
            ('Event ID','එක් event instance එක deduplicate / correlate කිරීමට.'),
            ('Proof ID / object key','Relational proof identity සහ binary storage identity; එකම දෙයක් නොවේ.')])+
        box('Schema කියවීමේ අභ්‍යාසය','R13 migration SQL එකේ keys, nullability සහ uniqueness constraints සොයන්න. ඊළඟට R12 තුළ එක action එකකින් save වන records සසඳන්න.'),evidence=('R12','R13','R17','R28'))

    add('booking',PART,'එක booking request එක පසුපස යමු','A source-guided walkthrough from browser to event record',
        diagram('booking-sequence',[
            ('b',5,10,190,'Browser client','POST + JWT + idempotency key','teal'),('g',235,10,190,'Gateway route','/api/v1/parcels','blue'),('c',465,10,190,'ParcelController.book','DTO validation','blue'),
            ('s',465,115,190,'ParcelService.book','Role / key / quote','teal'),('p',235,115,190,'Pricing API','Server-side quote','blue'),('d',465,230,190,'Parcel transaction','Parcel + history + outbox','gold')],
            [('b','g'),('g','c'),('c','s'),('s','p'),('s','d')],310,'රූපය 06 · Response ලැබෙන booking path එක සහ පසුව යන event path එක වෙන්කර බලන්න.')+
        steps(['Frontend endpoint function එක shared API client එක භාවිත කර JSON payload යවයි.',
            'Gateway route එක bearer token validate කර parcel service වෙත request යවයි.',
            'Controller එක pickup/dropoff ඇතුළුව DTO constraints පරීක්ෂා කර <code>book</code> method එකට යයි.',
            'Service එක role, merchant scope, idempotency key සහ request hash පරීක්ෂා කරයි.',
            'Pricing API එකෙන් fee නැවත ගණනය කර parcel, history සහ outbox එක save කරයි.',
            'Client එකට parcel ID / tracking number ලැබේ; consumers පසුව තමාගේ වැඩ කරයි.'])+
        box('ඔබේ code-reading exercise එක','R04 → R06 → R11 → R12 → R13 → R15 යන අනුපිළිවෙළට files විවෘත කරන්න. එක් පියවරකට input, output සහ possible failure එකක් සටහන් කරන්න.'),evidence=('R04','R06','R11','R12','R13','R15'))

    add('idempotency',PART,'Retry එකෙන් duplicate booking නොවීමට','Request identity, payload hash and database uniqueness',
        table(['Request තත්ත්වය','අපේක්ෂිත outcome එක'],[
            ('අලුත් key + valid payload','නව parcel එකක් සෑදේ.'),
            ('එකම customer + key + payload','පවතින parcel එක ආපසු ලැබේ.'),
            ('එකම key + වෙනස් payload','Conflict ලෙස ප්‍රතික්ෂේප විය යුතුය.'),
            ('Response timeout පසු retry','Original key සහ payload භාවිත කර final state reconcile කරන්න.'),
            ('Concurrent duplicate requests','Unique constraint duplicate row වලක්වයි; response semantics සහ concurrent race handling වෙනම test කළ යුතුය.')])+
        p('Request ID එක logs තුළ request එක හඳුනාගනී. Idempotency key එක business action එක හඳුනාගනී. Retry request එකට වෙනස් request ID තිබිය හැකි නමුත් එකම booking action එකට අලුත් idempotency key එකක් අහඹු ලෙස යැවිය යුතු නැත.')+
        code('''POST /api/v1/parcels
        Authorization: Bearer REDACTED
        Idempotency-Key: local-booking-0001

        Same payload + same key → same business operation''','HTTP sketch · no usable token included')+
        box('වත්මන් implementation එක','Service එක request hash සසඳයි; weight scale normalize කරයි. Database එකේ <code>UNIQUE(customer_id, booking_key)</code> constraint ඇත. Sequential duplicate behaviour local test එකෙන් තහවුරු කර තිබේ; concurrency guarantees සඳහා අමතර acceptance test අවශ්‍යයි.'),evidence=('R12','R13','R42'))

    add('pricing',PART,'Pricing එක වෙනම service එකක් ඇයි?','A business-policy boundary with observable consequences',
        p('Pricing එක delivery area, package size, weight සහ express තේරීම අනුව quote එකක් ලබාදෙයි. Strategy classes මඟින් price components එකතු කරන code structure එකක් ඇත. Zone / city mapping සහ effective rule වෙනස්කම් business behaviour මාරු කරයි.')+
        table(['අදහස','Project එකට සම්බන්ධතාව'],[
            ('Strategy composition','Zone band, weight surcharge සහ express surcharge වැනි ගණනයන් වෙන්ව ඇත.'),
            ('Browser preview','User ට පෙන්වන estimate එක; final charge සඳහා client අගය විශ්වාස නොකරයි.'),
            ('Server re-quote','Booking service එක pricing API කැඳවා fee / currency snapshot තබයි.'),
            ('Temporal rules','Effective dates සහ active zones වෙනස් කිරීමෙන් නව quotes වෙනස් විය හැක.'),
            ('Dependency failure','Pricing unavailable නම් නව booking path එකට බලපෑ හැක; static frontend එක තවම load විය හැක.')])+
        h('DevOps විසින් තහවුරු කළ යුතු දේ')+
        steps(['අදාළ city / zone mappings environment එකේ තිබේද?',
            'Clock / timezone සහ pricing effective dates ගැළපේද?',
            'Pricing latency booking timeout budget එකට ගැළපේද?',
            'Rule update එකෙන් පරණ parcel fee snapshot නොවෙනස්ව තිබේද?'])+
        box('Design inference','වෙනම pricing boundary එකෙන් policy වෙනස්කම් වෙන්කර release කළ හැක. මෙය architecture එකෙන් ලැබෙන trade-off විග්‍රහයකි; මුල් කර්තෘගේ තීරණයට ලියාපදිංචි ADR එකක් තිබෙන බවක් නොකියයි.'),evidence=('R12','R18'))

    add('lifecycle',PART,'Parcel state machine එක','Business state and Kubernetes Pod state are different',
        diagram('parcel-lifecycle',[
            ('c',0,10,150,'CREATED','Booked','blue'),('p',170,10,150,'PICKED_UP','Courier collected','teal'),('i',340,10,150,'IN_TRANSIT','Moving','blue'),('o',510,10,150,'OUT_FOR_DELIVERY','Delivery attempt','teal'),
            ('x',0,125,150,'CANCELLED','From CREATED','red'),('d',340,125,150,'DELIVERED','Valid proof required','gold'),('f',510,125,150,'FAILED','Reason; can reattempt','red')],
            [('c','p'),('p','i'),('i','o'),('c','x'),('o','d'),('o','f'),('f','o')],215,'රූපය 07 · FAILED සිට OUT_FOR_DELIVERY වෙත නැවත යා හැක.')+
        p('Status change එකක් string එකක් database එකට ලියන සාමාන්‍ය update එකක් ලෙස නොසලකන්න. Allowed transition, assigned courier, proof ownership සහ failure reason වැනි constraints සම්බන්ධ වේ. Successful change එක history සහ events ද සෑදේ.')+
        table(['Rule එක','Failure එකක අර්ථය'],[
            ('Courier assignment අවශ්‍යයි','Status request fail වීම application down බවට සාක්ෂියක් නොවේ.'),
            ('DELIVERED සඳහා proof','ඒ parcel එකට අයිති proofId එකක් තිබිය යුතුය.'),
            ('FAILED සඳහා reason','Recipient unavailable වැනි business failure එකක් system crash එකක් නොවේ.'),
            ('Cancel from CREATED','Pickup පසු cancellation ප්‍රතික්ෂේප වීම අපේක්ෂිත business behaviour විය හැක.')])+
        box('Incident triage','HTTP 409 / validation error එකක් customer action conflict එකක්ද, source regression එකක්ද යන්න payload, current state සහ domain rules මඟින් වෙන්කරන්න.'),evidence=('R12','R14'))
    integrations()
    specialists()

def integrations():
    add('outbox',PART,'Transactional outbox එක අවශ්‍ය ඇයි?','Bridge a database commit and a broker without pretending they are one transaction',
        diagram('outbox-flow',[
            ('t',5,10,190,'Local DB transaction','Parcel + history + outbox','blue'),('p',235,10,190,'Scheduled publisher','Pending batch + row locks','teal'),('r',465,10,190,'RabbitMQ','Persistent message / confirm','gold'),
            ('m',465,120,190,'Mark published','After ack and no return','blue'),('c',235,120,190,'Consumers','Independent side effects','teal')],
            [('t','p'),('p','r'),('r','m'),('r','c')],205,'රූපය 08 · Publish failure එකකදී pending outbox record එක නැවත උත්සාහ කිරීමට තබයි.')+
        p('Database එක commit වීමෙන් පසු broker එකට event යැවීම fail විය හැක. Event record එකත් එකම database transaction එකේ තැබීමෙන් ඒ වැඩ අමතක නොවේ. Publisher එක pending records ලබාගෙන RabbitMQ confirm එක සාර්ථක වූ පසු published ලෙස සලකුණු කරයි.')+
        table(['Failure point එක','ප්‍රතිඵලය / අවශ්‍ය පරීක්ෂාව'],[
            ('Commit පෙර process crash','Local transaction rollback විය යුතුය.'),
            ('Commit පසු publish failure','Pending outbox record ඉතිරි වී retry විය යුතුය.'),
            ('Broker accepted; marking failed','Event එක නැවත publish විය හැක; consumer dedup අවශ්‍යයි.'),
            ('Broker slow','Confirm wait එක අතර DB transaction / row locks දිගුවිය හැක.')])+
        box('Code එකෙන් දැනගත් දේ','Publisher එක batch එකකට records 20ක් ගනී; එක් confirm wait එකකට තත්පර 5ක සීමාවක් ඇත. Repository එක pessimistic write lock භාවිත කරයි. Backlog age, publish errors සහ DB lock waits එකට බලන්න.'),evidence=('R12','R15','R16'))

    add('rabbitmq',PART,'RabbitMQ: exchange, queue සහ ack','Publishing an event and completing a business side effect are separate',
        diagram('rabbit-fanout',[
            ('p',225,0,210,'Parcel publisher','Event envelope + routing key','blue'),('e',225,90,210,'parcel.events','Topic exchange','gold'),
            ('d',0,200,150,'Dispatch queue','Assignment projection','teal'),('t',170,200,150,'Tracking queue','Public read model','blue'),('n',340,200,150,'Notification queue','Delivery pipeline','teal'),('m',510,200,150,'Merchant queue','Webhook pipeline','blue')],
            [('p','e'),('e','d'),('e','t'),('e','n'),('e','m')],275,'රූපය 09 · Topic bindings අනුව consumers සඳහා වෙන වෙනම queue copies ලැබිය හැක.')+
        table(['පදය','මෙහි අර්ථය'],[
            ('Exchange / routing key','Message එක යැවිය යුතු queues තීරණය කරන routing mechanism එක.'),
            ('Queue / consumer','වැඩ රැඳෙන තැන සහ එය process කරන application listener එක.'),
            ('Publisher confirm','Broker එක publish එක භාරගත් බව; consumer වැඩ අවසන් කළ බව නොවේ.'),
            ('Consumer acknowledgement','Consumer-side handling ගැන broker එකට දැනුම්දීම.'),
            ('Durable / persistent','Restart survival සඳහා අවශ්‍ය configuration කොටස්; backup හෝ end-to-end exactly-once සහතිකයක් නොවේ.')])+
        p('HTTP booking response එක සඳහා සියලු consumers අවසන් වනතෙක් රැඳී නොසිටිය හැක. ඒ නිසා broker accepted, tracking updated සහ email delivered යන checkpoints වෙන වෙනම නිරීක්ෂණය කළ යුතුය.'),('confirms','reliability'),('R15','R17','R19','R22'))

    add('dedup',PART,'Duplicates, ordering සහ dead letters','The practical meaning of at-least-once delivery',
        p('Network reconnect, consumer crash හෝ uncertain publish outcome නිසා message එක දෙවරක් ලැබිය හැක. Consumer එක duplicate එක හඳුනාගෙන business side effect එක නැවත නොකිරීමට සැලසුම් කළ යුතුය. Timestamp එකක් හෝ queue order එකක් පමණක් සම්පූර්ණ safety mechanism එකක් නොවේ.')+
        table(['අවස්ථාව','Design / operational check'],[
            ('Duplicate event ID','Processed marker / domain uniqueness අනුව duplicate effect වලක්වන්න.'),
            ('Update පසු crash','Marker සහ side effect transaction boundary අනුව replay behaviour පරීක්ෂා කරන්න.'),
            ('Out-of-order event','Newer state පැරණි event එකෙන් overwrite නොවනවාද බලන්න.'),
            ('Malformed payload','Bounded retries පසු DLQ; payload schema / converter හේතුව හඳුනාගන්න.'),
            ('Expired dedup marker','TTL ඉක්මවා replay කළ විට semantics වෙනස් විය හැක.'),
            ('Concurrent replicas','Read-then-write marker එකක් atomic claim එකක් බව නොසිතන්න; concurrency tests අවශ්‍යයි.')])+
        p('Tracking listener එක projection update සාර්ථක වූ පසු processed marker තබයි. Failure එකකදී marker ඉවත් කර exception එක නැවත යවයි. මෙය retry සඳහා ප්‍රයෝජනවත් නමුත් සියලු replicas සඳහා end-to-end exactly-once proof එකක් නොවේ.')+
        box('DLQ එක විමර්ශනය කරන හැටි','Event ID, type, version සහ sanitized error එක ගන්න. Consumer fix එක test කර owned messages පමණක් controlled replay කරන්න. Queue එක purge කළ පමණින් අහිමි වූ business effect සම්පූර්ණ නොවේ.'),('reliability',),('R17','R19','R23'))

    add('tracking',PART,'Tracking read model සහ Redis','A fast public view derived from the parcel source of truth',
        diagram('tracking-read',[
            ('b',5,10,190,'Public tracking GET','Tracking number','teal'),('t',235,10,190,'Tracking service','Read projection','blue'),('r',465,10,190,'Redis','Cached public view','gold'),
            ('p',235,120,190,'Parcel snapshot API','Backfill path on cache miss','blue'),('e',465,120,190,'Parcel events','Update the read model','teal')],
            [('b','t'),('t','r'),('t','p'),('e','r')],205,'රූපය 10 · Normal read සහ cache-miss backfill අතර dependency වෙනස වැදගත්ය.')+
        p('Tracking සේවාව read-heavy public view එකක් සපයයි. Street addresses සහ private contact details වෙනුවට privacy-reduced fields යොදාගනී. Code එක Redis lookup සහ parcel snapshot backfill path එකක් සපයයි; සියලු Redis failure modes වල availability වෙනම පරීක්ෂා කළ යුතුය.')+
        table(['ලක්ෂණය','ඔබ සොයන evidence එක'],[
            ('Booking පසු තවම 404','Projection lag / backfill outcome; bounded retry සහ deadline.'),
            ('පරණ status එකක්','Event consumer lag, out-of-order handling සහ cached timestamp.'),
            ('Redis memory pressure','Evictions, TTLs, dataset size සහ backfill load.'),
            ('Cache loss','Source snapshot එකෙන් rebuild කළ හැකි කොටස සහ නැතිවන transient state හඳුනාගන්න.')])+
        box('Redis “cache එකක් පමණයි” කියා ඉවත් නොකරන්න','මෙම system එක Redis tracking projection, gateway limits සහ assistant limits වැනි වැඩ කිහිපයකට භාවිත කරයි. Fail වූ විට එක එක path එකේ fail-open / fail-closed behaviour source සහ tests මඟින් හඳුනාගන්න.'),evidence=('R06','R19','R20','R29'))

    add('realtime',PART,'WebSocket, SSE සහ polling','Live updates need application and proxy support',
        table(['Transport එක','අර්ථය සහ operational අවශ්‍යතාව'],[
            ('WebSocket / STOMP','දිගුකාලීන two-way connection එකක්; upgrade routing සහ reconnect behaviour අවශ්‍යයි.'),
            ('SSE','HTTP හරහා server → client event stream එකක්; buffering සහ idle timeouts බලපායි.'),
            ('Polling','කාල පරාසයකට HTTP GET නැවත යවයි; සරල fallback එකක් නමුත් request load වැඩි කරයි.'),
            ('Frontend ladder','Tracking client එක WebSocket → SSE → polling ලෙස මාරු වීමට සැලසුම් කරයි.'),
            ('Connection state','UI එක live / sse / polling / offline වෙන්කර පෙන්විය හැක.')])+
        p('Proxy එක buffer කළොත් server events ලියමින් තිබුණත් browser එකට ඒවා batch එකක් ලෙස පසුව ලැබිය හැක. Idle connection timeout එකක් හේතුවෙන් connection නැවත නැවත කැඩිය හැක. සාමාන්‍ය REST health check එකෙන් මේ path එක තහවුරු නොවේ.')+
        code('''curl.exe -N --max-time 15 `
          -H "Host: swiftdrop.local" `
          "http://localhost:8088/api/v1/tracking/public/TRACKING_NUMBER/stream"''')+
        box('Sample එක භාවිත කරන ආකාරය','<code>TRACKING_NUMBER</code> වලංගු local parcel tracking number එකකින් මාරු කරන්න. <code>-N</code> curl output buffering අඩු කරයි. Stream open වීම, events ලැබීම සහ client disconnect cleanup වෙන වෙනම පරීක්ෂා කරන්න.')+
        p('Replicas වැඩි කරන විට in-process connection registries සහ event delivery topology review කරන්න. සියලු realtime clients වෙත update යන්නේ කෙසේද යන්න “Service load balances” යන වචනයෙන් පමණක් විසඳෙන්නේ නැත.'),evidence=('R06','R21'))

    add('notifications',PART,'Email සහ SMS pipeline එක','Event → preferences → recipient lookup → channel → delivery log',
        steps(['Parcel status event එක notification listener එකට ලැබේ.',
            'Service එක feature switch සහ user preferences බලයි; identity data අවශ්‍ය නම් lookup කරයි.',
            'Channel router එක email / SMS adapter තෝරයි.',
            'Attempt outcome එක SENT / FAILED / skipped වැනි delivery record එකක් ලෙස තබයි.',
            'Retry scheduler / authorized resend මඟින් failures නැවත උත්සාහ කළ හැක.'])+
        table(['Check එක','සාර්ථකත්වය ලෙස ගත යුතු දේ'],[
            ('Event delivery','අදාළ event ID consumer path එකට ලැබීම.'),
            ('Template / preference','Correct status සහ user preference අනුව channel තෝරාගැනීම.'),
            ('Provider outcome','Exact parcel එකට EMAIL / SENT record; empty list එකක් pass නොවේ.'),
            ('Real-world delivery','SMTP accepted සහ inbox received වෙන වෙනම මැනිය යුතුය.'),
            ('Retries / duplicates','Crash අතර external send සහ local log write වෙන්වන අවස්ථා පරීක්ෂා කරන්න.')])+
        box('සැබෑ local failure එක','Legacy JSON converter එක Instant timestamp decode නොකළ නිසා notifications නැවතුණි. Converter fix සහ typed event regression tests පසු booking email SENT බව තහවුරු කළා. මේ නිසා Pod Ready පමණක් notification health ලෙස ගත නොහැක.')+
        p('PII masking සෑම log path එකකම ඇති බව නොසිතන්න. Error messages සහ exception logging ද review කරන්න. Mailpit local capture සහ production email / SMS provider verification වෙන වෙනම කාර්යයන්ය.'),evidence=('R22','R23','R42'))

    add('merchant',PART,'Merchant order එකේ data consistency','Organisation approval, scoped access and an upstream parcel',
        diagram('merchant-order',[
            ('m',5,10,190,'Approved merchant','Console JWT / scoped API key','teal'),('o',235,10,190,'Order service','merchantRef + payload hash','blue'),('p',465,10,190,'Parcel API','Derived idempotency key','blue'),
            ('d',235,120,190,'Merchant database','Store parcel ID + tracking ref','gold')],
            [('m','o'),('o','p'),('p','d')],205,'රූපය 11 · Merchant සහ parcel databases අතර එකම distributed SQL transaction එකක් නොමැත.')+
        p('Merchant order එක external business reference එක parcel record එකකට බැඳේ. Source එක merchantRef සහ payload hash සසඳයි. Parcel API එකට merchant identity එකෙන් derived idempotency key යවයි. Parcel create වීමෙන් පසු local order save වීම fail වන partial outcome එක සලකා බැලිය යුතුය.')+
        table(['Boundary එක','Acceptance test එක'],[
            ('Approval','Pending / suspended merchant කෙනෙකුගේ order create deny විය යුතුය.'),
            ('API key scopes','අවශ්‍ය resource පමණක්; revoked key fail විය යුතුය.'),
            ('Duplicate merchantRef','එකම payload නැවත read; වෙනස් payload conflict.'),
            ('Partial failure','Upstream parcel තිබියදී local retry duplicate parcel නොසාදන බව තහවුරු කරන්න.')])+
        box('Verification සීමාව','මෙය source-derived workflow එකකි. Current Kubernetes customer smoke pass එකෙන් merchant console/API-key end-to-end integration සියල්ල තහවුරු නොවේ.'),evidence=('R24','R12','R42'))

def specialists():
    add('webhooks',PART,'Webhook යනු පිටතට යන request එකක්','Sign the payload, bound retries and protect outbound access',
        p('Webhook receiver එක merchant කෙනෙකු පවත්වාගෙන යන external HTTPS endpoint එකකි. SwiftDrop event එක JSON body එකක් ලෙස එයට POST කරයි. Source එක timestamp සහ <code>X-SwiftDrop-Signature</code> headers යොදයි. Receiver එක raw body සහ shared secret මත signature verify කළ යුතුය.')+
        table(['කොටස','තේරුම / DevOps ප්‍රශ්නය'],[
            ('HMAC signature','Payload authenticity පරීක්ෂා කරයි; shared secret එක ආරක්ෂා කළ යුතුය.'),
            ('Timestamp / replay window','පරණ signed request එක නැවත යැවීම receiver එක කෙසේ හඳුනාගනීද?'),
            ('Event ID','Retry එක අලුත් business action එකක් ලෙස දෙවරක් process නොකිරීම.'),
            ('Retries','Source එක max attempts 5ක් සහ increasing delay භාවිත කරයි; DEAD state පසුව operations අවශ්‍යයි.'),
            ('Outbound URL','Source එක public HTTPS / resolved IP checks කරයි. Egress controls, redirects, DNS changes සහ actual HTTP timeouts තව review කරන්න.'),
            ('Secret lifecycle','Configure කළ විට නව secret එකක් ලැබේ; sender / receiver rotation එක සම්බන්ධ කළ යුතුය.')])+
        box('SSRF යන අවදානම','User-supplied webhook URL එකක් internal metadata / private endpoint වෙත යොමු කළ හැකි නම් server එකට අයිති access අනිසි ලෙස භාවිත විය හැක. Application URL checks සහ network egress policy දෙකම අවශ්‍යය.')+
        p('Receiver HTTP 200 ලැබීමෙන් එහි downstream business work අවසන් බව සෑමවිටම නොකියයි. Contract එක, queueing semantics සහ duplicate handling දෙපාර්ශවයම දැනගත යුතුය.'),evidence=('R25','R38'))

    add('dispatch',PART,'Dispatch command එක සහ reconciliation','Prepare locally, call upstream, then confirm or reject',
        diagram('dispatch-command',[
            ('d',5,10,190,'Dispatcher request','parcelId + courierId','teal'),('p',235,10,190,'Prepare transaction','Lock / validate / reserve','blue'),('c',465,10,190,'Parcel API call','Outside DB transaction','gold'),
            ('a',465,120,190,'Outcome','Success / conflict / unavailable','blue'),('f',235,120,190,'Confirm or reject','Local command and workload','teal'),('r',5,120,190,'PENDING recovery','Retry and reconcile','gold')],
            [('d','p'),('p','c'),('c','a'),('a','f'),('f','r')],205,'රූපය 12 · Unavailable upstream එකක් command එක PENDING තබාගත හැක.')+
        p('Dispatch service එක parcel source of truth එකේ copy එකක් සහ courier workload එකක් පවත්වාගනී. Assignment එකට reservation සහ durable command record එකක් සෑදේ. Network call එක local transaction එකෙන් පිටත සිදුකර result එක වෙනම transaction එකක confirm කරයි.')+
        table(['පදය','ඔබට වැදගත් අර්ථය'],[
            ('202 / PENDING','Request එක භාරගෙන ඇත; business outcome තව complete නොවිය හැක.'),
            ('Command ID','Final state poll / incident correlation සඳහා හඳුනාගැනීම.'),
            ('Reservation','Upstream call in-flight අතර capacity double allocation වලක්වීමට භාවිත වන state.'),
            ('Reconciliation','Async events සහ uncertain RPC outcome සමඟ local view නැවත ගැළපීම.')])+
        box('අතින් state edit නොකරන්න','Stuck PENDING එකකට command, parcel version සහ reserved courier load එක එකට පරීක්ෂා කරන්න. Database එකේ command state පමණක් වෙනස් කළහොත් workload වැරදිවිය හැක.'),evidence=('R26','R27'))

    add('proofs',PART,'Object storage සහ proof-of-delivery','A database reference and a binary object have separate lifecycles',
        diagram('proof-storage',[
            ('c',5,10,190,'Courier upload','Multipart file + JWT','teal'),('p',235,10,190,'Parcel proof service','Validate / authorize','blue'),('o',465,10,190,'Private object store','MinIO parcel-proofs bucket','gold'),
            ('d',235,120,190,'Parcel database','Metadata + proof ID','gold'),('b',465,120,190,'Authorized download','API bytes or signed URL','teal')],
            [('c','p'),('p','o'),('p','d'),('o','b')],205,'රූපය 13 · Blob සහ relational metadata එකට restore consistency අවශ්‍යයි.')+
        p('Database එක file bytes සඳහා සාමාන්‍ය shared directory එකක් ලෙස භාවිත නොකරයි. Proof service එක object key / metadata database එකේ තබා binary object එක private storage එකේ තබයි. DELIVERED transition එකට ඒ parcel එකට අයිති proofId අවශ්‍ය වේ.')+
        table(['Failure එක','සැලකිය යුතු දේ'],[
            ('Blob exists, DB write failed','Rollback callback එක object delete කිරීමට උත්සාහ කරයි; process crash / cleanup failure සඳහා reconciliation තව සලකා බලන්න.'),
            ('DB exists, object missing','Restore / lifecycle mismatch හෝ storage incident විය හැක.'),
            ('Signed URL browser fail','Public endpoint, TLS, expiry සහ local port-forward බලන්න.'),
            ('Upload rejected','File limits, content validation, role සහ request size පරීක්ෂා කරන්න.')])+
        box('Backup සීමාව','PostgreSQL backup එකෙන් MinIO binary objects නැවත ලැබෙන්නේ නැත. Proof metadata සහ object inventory ගැළපෙන restore point / policy සැලසුම් කරන්න.'),evidence=('R12','R28','R35'))

    add('assistant',PART,'AI assistant එක architecture එකට එක්වන්නේ කෙසේද?','Separate Python runtime, protected APIs and optional model access',
        diagram('assistant-runtime',[
            ('c',5,10,190,'Chat widget','JWT / SSE','teal'),('g',235,10,190,'FastAPI guards','Input / JWT / role / limits','blue'),('r',465,10,190,'LangGraph runner','Select an authorized path','blue'),
            ('j',5,125,190,'Java service clients','Tracking / my parcels','teal'),('m',235,125,190,'Optional Gemini','Model text streaming','gold'),('s',465,125,190,'Persistence','Conversations / checkpoints','gold')],
            [('c','g'),('g','r'),('r','j'),('r','m'),('r','s')],215,'රූපය 14 · Chat runtime එක සහ එහි storage / providers වෙන්කර හඳුනාගන්න.')+
        p('Main application එක configured database URL තිබේ නම් SQL conversation repository එක තෝරයි; නැතිනම් in-memory fallback එකක් ඇත. Checkpoint URL තිබේ නම් PostgreSQL graph saver එකක් සම්බන්ධ වේ. Configuration එක නිසා durability behaviour වෙනස් වේ.')+
        table(['Dependency එක','Failure / operation concern'],[
            ('Identity JWKS','JWT validation සහ key retrieval.'),
            ('Parcel / pricing clients','Caller-authorized downstream access; actual handler path බලන්න.'),
            ('Redis limiter','Shared request budget; missing config fallback policy review කරන්න.'),
            ('Gemini','API key, quota, latency සහ safe fallback.'),
            ('SQL / checkpoints','Schema initialization, retention සහ restart history.')])+
        box('Core delivery isolation','Assistant එක disable කළ විට core parcel APIs ක්‍රියා කළ යුතුය. “AI provider down” incident එකකට database wipe හෝ මුළු delivery platform restart අවශ්‍ය නොවිය යුතුය.'),evidence=('R29','R30','R42'))

    add('assistant-boundaries',PART,'AI feature එකේ සැබෑ සීමා','Read the runtime wiring as well as the README',
        table(['Source එකෙන් පෙනෙන දේ','ඔබ ඒක අර්ථ දක්වන්නේ'],[
            ('Direct tracking / my-parcels handlers','මේ intent paths වල actual service-client execution ඇත.'),
            ('Fee intent asks for details','සෑම chat estimate එකකම live pricing call කළ බව නොසලකන්න.'),
            ('Other roles get tool-name context','Tool registry entry එකක් තිබීම live tool orchestration සම්පූර්ණ බවට proof නොවේ.'),
            ('Write / confirmation components exist','Full chat prepare → confirm → execute wiring සහ E2E acceptance තව තහවුරු කළ යුතුය.'),
            ('InMemoryOperationStore','Pending operations process-local ය; replica switch / restart durability සහ atomic shared claim තව අවශ්‍යයි.'),
            ('Persisted conversation history','Pending confirmation state හෝ complete audit trail එකත් durable බවට ඒකෙන් සාක්ෂියක් නොලැබේ.')])+
        p('Model response එකක “I completed it” කියන වාක්‍යයක් business write එකක් සම්පූර්ණ වූ බවට ප්‍රමාණවත් නොවේ. Authorized downstream result, operation state සහ ownership checks අවශ්‍යය.')+
        box('පරීක්ෂණ ලැයිස්තුව','Wrong role, wrong owner, expired confirmation, duplicate execution, replica switch, downstream timeout සහ provider outage යන තත්ත්ව පරීක්ෂා කරන්න. Live Gemini call එක current local evidence එකෙන් තහවුරු කර නැත.','warning')+
        p('AI විසින් ලියූ feature එකක් audit කරන ආකාරයටම manual feature එකත් audit කරන්න: entry point → runtime binding → side effect → persistence → test evidence.'),evidence=('R29','R30','R31','R42'))
