from book import add, p, h, code, box, table, steps, ul, diagram

PART='03 / සම්පූර්ණ application workflows'

def populate():
    add('auth',PART,'Login සිට authorized request එක දක්වා','Application roles and Kubernetes permissions are separate',
        diagram('auth-flow',[
            ('c',5,10,190,'Client','Register / login','teal'),
            ('i',235,10,190,'Identity','RS256 JWT + refresh flow','blue'),
            ('g',465,10,190,'Gateway','Validate JWT / route','blue'),
            ('s',235,115,190,'Owning service','Role + ownership checks','gold')],
            [('c','i'),('i','g'),('g','s')],190,'රූපය 09 · Identity token එක downstream authorization සඳහාත් වැදගත්ය.')+
        table(['Role','ප්‍රධාන බලතල'],[
            ('Guest','Public tracking; protected assistant history සඳහා අවසර නැත.'),
            ('CUSTOMER','තමාගේ bookings, cancellation, receipts සහ notification preferences.'),
            ('MERCHANT','තමාගේ organisation, orders, API keys සහ webhooks.'),
            ('COURIER','තමාට පැවරූ parcels සහ අවසර ඇති status transitions.'),
            ('DISPATCHER','Queue, workload, roster සහ assignment commands.'),
            ('ADMIN','Users, roles, merchant approval, pricing සහ operational views.')])+
        p('JWT validation සඳහා issuer, audience, expiry සහ signature සැලකිය යුතුය. Identity public JWKS endpoint එක key verification සඳහා භාවිත වේ. UI button එකක් සැඟවීම authorization එකක් නොවේ; service එකත් ownership තහවුරු කළ යුතුය.')+
        box('Role ලබාගැනීම','Self-registration මඟින් administrator / courier බලතල ලැබෙන බව නොසිතන්න. Approved administrative flow එකෙන් test accounts සකස් කර role-specific tests කරන්න. Application ADMIN role එක Kubernetes cluster-admin permission එකට සමාන නොවේ.'))

    add('customer',PART,'Customer booking එකේ සම්පූර්ණ ගමන','Quote → booking → events → tracking → notification',
        diagram('customer-workflow',[
            ('c',5,10,190,'Customer','Authenticated request','teal'),
            ('q',235,10,190,'Pricing quote','Cities / size / weight','blue'),
            ('p',465,10,190,'Parcel booking','Re-quote + idempotency','blue'),
            ('t',465,115,190,'Transaction committed','Parcel + history + outbox','gold'),
            ('e',235,115,190,'RabbitMQ event','Independent consumers','gold'),
            ('v',5,115,190,'User-visible results','Tracking / email / receipt','teal')],
            [('c','q'),('q','p'),('p','t'),('t','e'),('e','v')],200,'රූපය 10 · Booking success එක සහ asynchronous side effects එකම මොහොතේ සම්පූර්ණ නොවිය හැක.')+
        steps(['Customer register / login කර bearer token ලබාගනී.',
            'From/to cities, size, weight සහ express තෝරා pricing estimate එක බලයි.',
            'Pickup / dropoff address තුළ zone ඇතුළුව අවශ්‍ය fields සපයයි; unique <code>Idempotency-Key</code> යවයි.',
            'Parcel service එක server-side fee නැවත ගණනය කර parcel එක සහ outbox event එක එකම transaction එකේ තබයි.',
            'Tracking projection හා email delivery bounded polling මඟින් තහවුරු කරයි.',
            'Cancel කිරීමට නම් parcel එක තවම <code>CREATED</code> විය යුතුය.'])+
        box('Idempotence','Network timeout එකක් නිසා එකම booking request එක retry කරන විට <strong>එකම key සහ එකම payload</strong> යවන්න. නව key එකක් යැවීමෙන් නව parcel එකක් සෑදිය හැක.')+
        p('මෙම customer workflow එක local Ingress හරහා පරීක්ෂා කර ඇත. Courier / merchant / dispatcher සම්පූර්ණ workflows සඳහා පහත පිටුවල source-based ක්‍රමය දක්වන අතර ඒවායේ අමතර end-to-end verification තව අවශ්‍යයි.'))

    add('customer-api-login',PART,'Customer API lab: account සහ quote','Run only against the local loopback environment',
        p('මෙම සහ ඊළඟ පිටුව එකම PowerShell session එකේ භාවිත කරන්න. Synthetic local account එකක් සාදයි; token සහ password print නොකර variables තුළ තබයි. සම්පූර්ණ automated check සඳහා repository smoke script එක ප්‍රමුඛ තේරීමයි.')+
        code('''$base = 'http://localhost:8088'
        $run = [guid]::NewGuid().ToString('N')
        $mail = "book-$run@swiftdrop.local"
        $pass = [guid]::NewGuid().ToString('N') + 'Aa1!'
        function Local-Api($method, $path, $body, $extra = @{}) {
          $head = @{Host='swiftdrop.local'}
          foreach ($k in $extra.Keys) { $head[$k] = $extra[$k] }
          $request = @{Method=$method; Uri="$base$path";
            Headers=$head; TimeoutSec=30}
          if ($null -ne $body) {
            $request.ContentType = 'application/json'
            $request.Body = $body | ConvertTo-Json -Depth 10
          }
          Invoke-RestMethod @request
        }
        Local-Api POST '/api/v1/auth/register' @{
          email=$mail; password=$pass; fullName='Book Lab'
          phone='+94770000000'
        } | Out-Null
        $login = Local-Api POST '/api/v1/auth/login' @{
          email=$mail; password=$pass
        }
        $auth = @{Authorization="Bearer $($login.accessToken)"}
        $quote = Local-Api POST '/api/v1/pricing/estimate' @{
          fromCity='Colombo'; toCity='Colombo'; size='SMALL'
          weightKg=1.0; express=$false
        } $auth''')+
        box('අපේක්ෂිත ප්‍රතිඵලය','<code>$login.accessToken</code> හි අගයක් තිබිය යුතුය. Quote එකේ total ධනාත්මක විය යුතු අතර currency <code>LKR</code> විය යුතුය. මෙහි source code එකේ endpoint paths භාවිත කර ඇත.'))

    add('customer-api-booking',PART,'Customer API lab: booking සහ cancellation','Keep the same idempotency key when retrying',
        code('''$address = @{
          contactName='Book Lab'; phone='+94770000000'
          line1='1 Test Road'; city='Colombo'
          postalCode='00100'; zone=$quote.zoneFrom
        }
        $booking = @{
          pickup=$address; dropoff=$address; size='SMALL'
          weightKg=1.0; express=$false
        }
        $auth['Idempotency-Key'] = "book-$run"
        $parcel = Local-Api POST '/api/v1/parcels' $booking $auth
        $again = Local-Api POST '/api/v1/parcels' $booking $auth
        if ($again.id -ne $parcel.id) { throw 'Duplicate booking' }
        # A projection may initially return 404; poll with a deadline.
        $trackingPath = "/api/v1/tracking/$($parcel.trackingNumber)"
        $cancelPath = "/api/v1/parcels/$($parcel.id)/cancel"
        $cancelled = Local-Api POST $cancelPath $null $auth
        if ($cancelled.status -ne 'CANCELLED') {
          throw 'Cancellation was not confirmed'
        }''')+
        p('මෙහි pickup සහ dropoff එකම Colombo test address එකයි. සැබෑ UI flow එකේ zones දෙක quote එකේ origin / destination අනුව වෙනස් විය හැක. <code>zone</code> field එක අත්හැරියහොත් validation error ලැබේ.')+
        table(['තහවුරු කිරීම','Pass ලෙස ගත යුතු සාක්ෂිය'],[
            ('Booking retry','Response දෙකෙහි එකම parcel ID.'),
            ('Tracking','Created tracking number එකට public response; eventual consistency සඳහා සීමිත retries.'),
            ('Notification','එම tracking number එකට EMAIL / SENT record එක.'),
            ('Cancellation','Response සහ පසුව parcel GET එකේ <code>CANCELLED</code>.')])+
        box('සම්පූර්ණ පරීක්ෂණය','<code>.\\infra\\kubernetes\\tests\\smoke-tests.ps1 -Workflow</code> මේ checks සමඟ SSE සහ persisted history ද පරීක්ෂා කරයි. එහි synthetic writes loopback host එකට සීමා කර ඇත.'))

    add('events',PART,'Event-driven workflow එක ඇතුළත','Transactional outbox and at-least-once delivery',
        diagram('event-outbox',[
            ('p',225,0,210,'Parcel transaction','Aggregate + outbox committed','blue'),
            ('o',225,90,210,'Outbox publisher','Retry publication','teal'),
            ('r',225,180,210,'parcel.events','RabbitMQ topic exchange','gold'),
            ('d',0,280,150,'Dispatch','Queue / workload','blue'),
            ('t',170,280,150,'Tracking','Redis projection','teal'),
            ('n',340,280,150,'Notification','Email / SMS','blue'),
            ('m',510,280,150,'Merchant','Signed webhooks','teal')],
            [('p','o'),('o','r'),('r','d'),('r','t'),('r','n'),('r','m')],350,'රූපය 11 · Consumer එකක් අසාර්ථක වුවත් අනෙක් consumers ස්වාධීනව වැඩ කළ හැක.')+
        p('Business transaction එක commit වී broker publish එක fail වුවහොත් event එක නැතිවීම වැළැක්වීමට outbox record එක database එකේ තබයි. Publisher එක නැවත උත්සාහ කරයි. Publish වූ පසු bookkeeping fail වුවහොත් එකම event එක නැවත පැමිණිය හැකි නිසා consumer deduplication අවශ්‍යයි.')+
        table(['අවදානම','නිවැරදි සැලැස්ම'],[
            ('Duplicate event','Event ID මත deduplicate කර side effect එක නැවත නොකරන්න.'),
            ('Late / reordered event','Version / timestamp සහ current state සලකා projection update කරන්න.'),
            ('Poison message','Bounded retry සහ DLQ; හේතුව නිවැරදි කළ පසු controlled replay.')])+
        box('Distributed transaction සීමාව','මෙම flow එකෙන් “exactly once සෑම තැනම” සහතිකයක් නොලැබේ. Booking API idempotency සහ consumer event deduplication වෙන වෙනම යාන්ත්‍රණ දෙකකි.'))

    add('tracking-notifications',PART,'Tracking සහ notification තහවුරු කිරීම','Observe asynchronous completion instead of assuming it',
        table(['Flow එක','Source සහ success criteria'],[
            ('Public tracking','<code>GET /api/v1/tracking/{trackingNumber}</code>; privacy-reduced response.'),
            ('Private tracking','<code>GET /api/v1/tracking/{trackingNumber}/full</code>; token සහ ownership / role validation.'),
            ('SSE tracking','<code>GET /api/v1/tracking/{trackingNumber}/stream</code>; heartbeat සහ updates.'),
            ('My notifications','<code>GET /api/v1/notifications/me</code>; අදාල tracking number / EMAIL / SENT.'),
            ('Preferences','<code>GET / PUT /api/v1/notifications/preferences</code>; user-owned settings.')])+
        p('Parcel commit වහාම tracking projection එක නොතිබිය හැක. පළමු 404 එකෙන් booking fail වී ඇතැයි නිගමනය නොකරන්න. Deadline එකක් තුළ retry කරන්න; අවසානයේ fail නම් outbox, consumer සහ Redis path එක පරීක්ෂා කරන්න.')+
        h('Local notification failure එකෙන් ලැබුණු පාඩම')+
        p('Legacy Jackson converter එක event timestamp වන <code>Instant</code> decode නොකළ නිසා event එක DLQ වෙත ගියේය. Spring AMQP <code>JacksonJsonMessageConverter</code> සහ Boot JsonMapper භාවිතයට මාරු කර headerless typed event regression test එකක් එක් කළේය. ඒ පසු local email <code>SENT</code> බව තහවුරු විය.')+
        box('වැරදි pass criteria','Notification list එක empty වුවත් HTTP 200 ලැබිය හැක. Correct test එක exact parcel tracking number එකට සාර්ථක delivery record එකක් හමු විය යුතු බව පරීක්ෂා කරයි.','warning')+
        p('Mailpit වෙත SENT වීම local pipeline verification එකකි. Production SMTP delivery, recipient inbox placement, SMS provider සහ retries තව වෙනම පරීක්ෂා කළ යුතුය.'),('amqp',))

    add('merchant',PART,'Merchant onboarding සිට webhook දක්වා','Source-based workflow; additional role E2E tests remain',
        steps(['Authenticated user කෙනෙක් <code>POST /api/v1/merchants/register</code> මඟින් organisation එක ලියාපදිංචි කරයි.',
            'Admin merchant application එක පරීක්ෂා කර <code>POST /api/v1/merchants/{id}/approve</code> හෝ reject කරයි.',
            'Merchant තම profile එක <code>GET /api/v1/merchants/me</code> මඟින් බලයි; අවශ්‍ය scopes සමඟ API key සාදයි.',
            'Console හෝ authorized REST client මඟින් order එක සාදයි; ලැයිස්තුවෙන් එහි outcome සහ tracking link පරීක්ෂා කරයි.',
            'Webhook URL සකස් කර signed event deliveries ලබයි; failure එකක් තිබේ නම් හේතුව හඳුනාගෙන owned delivery එක replay කරයි.'])+
        table(['කාර්යය','Endpoint'],[
            ('API keys','<code>POST / GET /api/v1/merchants/me/api-keys</code>'),
            ('Revoke key','<code>DELETE /api/v1/merchants/me/api-keys/{keyId}</code>'),
            ('Orders','<code>POST / GET /api/v1/merchants/orders</code>'),
            ('Order detail','<code>GET /api/v1/merchants/orders/{orderId}</code>'),
            ('Webhook setup','<code>PUT /api/v1/merchants/me/webhook</code>'),
            ('History / replay','<code>GET .../me/webhook/deliveries</code><br><code>POST .../me/webhook/deliveries/{deliveryId}/replay</code>')])+
        p('Webhook receiver එක signature, event identity සහ duplicate handling පරීක්ෂා කළ යුතුය. Receiver timeout එකක් නිසා delivery නැවත පැමිණිය හැක. Replay එකක් අලුත් business action එකක් ලෙස දෙවරක් නොසලකන්න.')+
        box('Production acceptance','Approval role propagation, API key scopes/revocation, order ownership, webhook TLS/signature, receiver downtime සහ controlled replay සම්පූර්ණ end-to-end පරීක්ෂා කරන්න. මේවා customer smoke pass එකෙන් ආවරණය නොවේ.','warning'))

    add('dispatch',PART,'Dispatcher assignment workflow','Queue → workload → suggestion → command → reconciliation',
        diagram('dispatch-workflow',[
            ('q',5,10,190,'Dispatch queue','Unassigned parcels','blue'),
            ('s',235,10,190,'Select courier','Roster + workload + suggest','teal'),
            ('c',465,10,190,'Assignment command','May return 202 / PENDING','gold'),
            ('p',465,115,190,'Parcel owner','Authorized internal update','blue'),
            ('v',235,115,190,'Observe command','Read command status','teal'),
            ('r',5,115,190,'Reconcile views','Manifest / queue / workload','blue')],
            [('q','s'),('s','c'),('c','p'),('p','v'),('v','r')],200,'රූපය 12 · Command accepted වීම සහ assignment complete වීම වෙන්කර බලන්න.')+
        table(['Endpoint prefix: /api/v1','භාවිතය'],[
            ('<code>GET /dispatch/queue</code>','Unassigned queue; optional zone filter.'),
            ('<code>GET /dispatch/workload</code>','Courier load බලන්න.'),
            ('<code>GET /dispatch/suggest?parcelId=...</code>','Recommendation එකක්; final assignment තීරණය වෙනමය.'),
            ('<code>POST /dispatch/assignments</code>','Body: parcelId, courierId; reassign query flag අවශ්‍ය නම් පමණි.'),
            ('<code>GET /dispatch/commands/{id}</code>','202 / PENDING command එකේ final result නිරීක්ෂණය.'),
            ('<code>DELETE /dispatch/assignments/{id}</code>','Reason සහිත unassignment command.')])+
        p('Dispatcher / admin token එකක් අවශ්‍යය. Parcel ownership service එකට internal request එක යන්නේ නිසි service credential scope සමඟය. Browser එකෙන් internal endpoints සෘජුව භාවිත නොකරන්න.')+
        box('පරීක්ෂණය','Conflict, reassignment, inactive courier, duplicate request ID, downstream timeout සහ pending command recovery තහවුරු කරන්න. Current local customer smoke එක assignments පරීක්ෂා කර නැත.'))

    add('courier',PART,'Courier delivery lifecycle','Assigned work and guarded state transitions',
        diagram('parcel-state',[
            ('c',0,10,150,'CREATED','Booking exists','blue'),
            ('p',170,10,150,'PICKED_UP','Collected','teal'),
            ('i',340,10,150,'IN_TRANSIT','Moving','blue'),
            ('o',510,10,150,'OUT_FOR_DELIVERY','Delivery attempt','teal'),
            ('x',0,125,150,'CANCELLED','Only from CREATED','red'),
            ('d',340,125,150,'DELIVERED','Valid proof required','gold'),
            ('f',510,125,150,'FAILED','Reason; reattempt allowed','red')],
            [('c','p'),('p','i'),('i','o'),('c','x'),('o','d'),('o','f'),('f','o')],210,'රූපය 13 · FAILED තත්ත්වයෙන් නැවත OUT_FOR_DELIVERY වෙත යා හැක.')+
        steps(['Courier token එකෙන් <code>GET /api/v1/couriers/me/assignments</code> කියවන්න.',
            'Date එකට manifest එක බලන්න: <code>GET /api/v1/couriers/{id}/manifest?date=YYYY-MM-DD</code>. Ownership පරීක්ෂා වේ.',
            '<code>PATCH /api/v1/parcels/{id}/status</code> මඟින් අවසර ඇති ඊළඟ තත්ත්වයට යන්න.',
            'Delivery සාර්ථක නම් proof upload කර ලැබුණු proofId එක සමඟ DELIVERED කරන්න.',
            'අසාර්ථක නම් failedReason සමඟ FAILED කරන්න; පසුව reattempt එක කළ හැක.'])+
        code('''{"status":"FAILED","failedReason":"Recipient unavailable"}
        {"status":"DELIVERED","proofId":"PARCEL_OWNED_PROOF_UUID"}''','JSON · separate status-request examples')+
        box('Business rules server එකේ','වෙනත් courier කෙනෙකුගේ parcel එකක්, අනුපිළිවෙළට නොගැළපෙන transition එකක් හෝ වෙනත් parcel එකකට අයිති proof එකක් ප්‍රතික්ෂේප විය යුතුය. මේ negative tests role workflow acceptance එකට ඇතුළත් කරන්න.'))

    add('proof-receipt',PART,'Delivery proof සහ PDF receipt','Private object storage with application ownership checks',
        table(['කාර්යය','Parcel API path'],[
            ('Proof upload','<code>POST /api/v1/parcels/{id}/proofs</code><br>multipart field: <code>file</code>'),
            ('Authorized download','<code>GET /api/v1/parcels/{id}/proofs/{proofId}</code>'),
            ('Signed URL','<code>GET /api/v1/parcels/{id}/proofs/{proofId}/url</code>'),
            ('Receipt download','<code>GET /api/v1/parcels/{id}/receipt</code><br>Response: <code>application/pdf</code>')])+
        p('Proof metadata එක parcel ownership සමඟ බැඳී ඇති අතර actual object එක private bucket එකේ තබයි. URL එක දන්නා පමණින් සදාකාලික access ලැබෙන ආකාරයට public bucket එකක් යෙදීම අවශ්‍ය නොවේ. Signed URL එකේ expiry සහ client access path පරීක්ෂා කරන්න.')+
        steps(['Courier authorized session එකෙන් image එක upload කර response proof ID සටහන් කරන්න.',
            'DELIVERED transition එකට ඒ proof ID එකම භාවිත කරන්න.',
            'Owner / permitted role එකෙන් download සාර්ථකද, වෙනත් customer කෙනෙකුට deny වෙනවාද පරීක්ෂා කරන්න.',
            'Receipt එක download කර tracking number, fee snapshot සහ access control පරීක්ෂා කරන්න.'])+
        box('Local browser URL','අවශ්‍ය නම් <code>service/minio</code> සඳහා port-forward 9000:9000 ධාවනය කරන්න. Production හි browser-reachable object endpoint, TLS සහ private bucket policy වෙනම සකස් කළ යුතුය.')+
        p('Image content type සහ file size constraints වත්මන් source configuration අනුව බලන්න. Production upload acceptance එකට malicious / oversized files, expired signed URL සහ object metadata mismatch තත්ත්වද එකතු කරන්න. මෙම සියලු checks local customer smoke එකෙන් සිදුකර නොමැත.'))

    add('admin',PART,'Administrator workflow','Provision roles, approve merchants and manage pricing',
        table(['පියවර','API / තහවුරු කිරීම'],[
            ('User review','<code>GET /api/v1/users</code>; filters සහ paging මඟින් අදාල user හඳුනාගන්න.'),
            ('Status / roles','<code>PATCH /api/v1/users/{id}/status</code> හෝ <code>.../{id}/roles</code>; administrative authorization අවශ්‍යයි.'),
            ('Merchant decision','<code>POST /api/v1/merchants/{id}/approve</code>, reject හෝ suspend.'),
            ('Pricing setup','<code>/api/v1/pricing/zones</code> සහ <code>/api/v1/pricing/rules</code> APIs; active rules සහ effective dates බලන්න.'),
            ('Notification operations','<code>GET /api/v1/notifications</code> සහ <code>POST /api/v1/notifications/{id}/resend</code>.'),
            ('Dispatch supervision','Queue, courier workload සහ daily statistics නිරීක්ෂණය.')])+
        p('Pricing rule එක මාරු කිරීමෙන් පසු නව quote එකට ඇති බලපෑම සහ පරණ parcel එකේ fee snapshot නොවෙනස්ව පවතින බව පරීක්ෂා කරන්න. Role වෙනසකට පසු පවතින token / session බලතල කොතෙක් කාලයක් පවතින්නේද යන්න acceptance test එකකට ඇතුළත් කරන්න.')+
        steps(['Change එකට හේතුව, target ID සහ අපේක්ෂිත ප්‍රතිඵලය පැහැදිලි කරන්න.',
            'Least-privilege administrator session එකෙන් approved request එක කරන්න.',
            'GET එකකින් final state නැවත කියවන්න; audit / request ID සටහන් කරන්න.',
            'Permission අහිමි විය යුතු user සහ වෙනත් owner කෙනෙකුගෙන් negative tests කරන්න.'])+
        box('Application admin සහ cluster operator','Application users manage කිරීම සඳහා Kubernetes Secret data හෝ database tables අතින් වෙනස් කිරීම අවශ්‍ය නොවිය යුතුය. Cluster infrastructure permissions වෙනම RBAC මඟින් පාලනය කරන්න.'))

    add('assistant',PART,'AI assistant: request එකේ සැබෑ ගමන','FastAPI + LangGraph + optional Gemini + persistent history',
        diagram('assistant-flow',[
            ('c',5,10,190,'Chat client','JWT + SSE','teal'),
            ('g',235,10,190,'Guards','JWT / input / role / limits','blue'),
            ('r',465,10,190,'LangGraph router','Select authorized agent','blue'),
            ('t',5,120,190,'Deterministic handlers','Tracking / my parcels','teal'),
            ('m',235,120,190,'Optional model','Role-scoped text response','gold'),
            ('p',465,120,190,'Persistence','History / audit / checkpoints','gold')],
            [('c','g'),('g','r'),('r','t'),('r','m'),('r','p')],205,'රූපය 14 · Tool implementation සහ chat runtime integration වෙන වෙනම පරීක්ෂා කළ යුතුය.')+
        p('Assistant HTTP endpoint එක <code>POST /api/v1/assistant/chat/stream</code> වේ. Token, tool, error සහ අවසාන <code>done</code> events ලැබිය හැක. <code>done</code> හි conversationId භාවිත කර <code>GET /api/v1/assistant/conversations/{id}</code> කියවන්න.')+
        box('වත්මන් source එකේ වැදගත් සීමාව','Runner එකේ tracking සහ “my parcels” සඳහා direct handlers ඇත. Fee intent එක විස්තර ඉල්ලන response එකක් සපයයි. අනෙක් role paths බොහොමයක model වෙත tool names ලබාදෙන නමුත් සම්පූර්ණ tool execution / confirmation chat flow එක wire වී ඇති බව source එකෙන් තහවුරු නොවේ. Tool classes තිබීම live chat workflow pass වීමක් නොවේ.','warning')+
        p('Gemini key නොමැතිව deterministic / fallback response ලබාගත හැක. Local test එක SSE completion සහ durable conversation history තහවුරු කරයි; සජීවී Gemini provider call හෝ සියලු role tools එයින් තහවුරු නොවේ.')+
        p('Assistant failure එක core delivery services අක්‍රිය කළ යුතු නැත. Provider incident එකකදී <code>config.assistantEnabled=false</code> යොදා feature එක නවත්වා core API smoke test නැවත කරන්න.'))

    add('confirmation',PART,'AI write operations සඳහා confirmation','Implemented guard components and pending production integration',
        diagram('confirmation-flow',[
            ('p',5,10,190,'Prepare','Owner / role / payload hash','blue'),
            ('c',235,10,190,'Explicit confirmation','TTL + ownership validated','gold'),
            ('e',465,10,190,'Claim execution','One state transition','teal'),
            ('d',465,115,190,'Downstream API','Caller JWT + idempotency','blue'),
            ('a',235,115,190,'Record outcome','Completed / failed / expired','teal')],
            [('p','c'),('c','e'),('e','d'),('d','a')],200,'රූපය 15 · මෙය confirmation components හි intended workflow එකයි; live chat E2E තහවුරු කිරීම තව අවශ්‍යය.')+
        p('Customer booking / cancellation, dispatch changes සහ admin changes සඳහා prepare / confirmation / execution components source තුළ ඇත. Payload hash වෙනස් වීම, වෙනත් owner කෙනෙකුගේ confirmation එක හෝ expired operation එක ප්‍රතික්ෂේප කරන checks ඇත.')+
        box('Scale කිරීමට පෙර විසඳිය යුතු gap එක','Pending operations සඳහා <code>InMemoryOperationStore</code> සහ process-local lock භාවිත වේ. Conversation persistence තිබුණත් pending confirmations replica එකක් අතර බෙදා නොගනී. Restart / multi-replica flow එකකට durable shared store, atomic claim සහ E2E wiring තහවුරු කළ යුතුය.','warning')+
        table(['අවස්ථාව','Test expectation'],[
            ('Owner mismatch / role changed','Operation execute නොවිය යුතුය.'),
            ('Duplicate confirmation / concurrent execute','එකම business write නැවත සිදු නොවිය යුතුය.'),
            ('Restart / second replica','Pending operation policy පැහැදිලි විය යුතුය; silent success කියන්න එපා.'),
            ('Downstream timeout','Final state නැවත කියවා reconcile කරන්න; අලුත් payload එකක් අන්ධව retry නොකරන්න.')])+
        p('Model එක කියූ “done” වචනයක් පමණක් business action සම්පූර්ණ වීමේ සාක්ෂියක් නොවේ. Downstream result, operation state සහ audit record සමඟ තහවුරු කරන්න.'))
