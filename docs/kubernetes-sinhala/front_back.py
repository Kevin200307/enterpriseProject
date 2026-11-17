from book import add, p, h, code, box, table, steps, diagram, SOURCES

def make_front(main_pages):
    add('cover','','Kubernetes A–Z','',
        '<div class="eyebrow">SWIFTDROP · PRACTICAL ENGINEERING BOOK</div>'+
        '<div class="big">KUBERNETES<br>A–Z</div>'+
        '<h1>මුල සිට ක්‍රියාත්මක<br>පද්ධතියක් දක්වා</h1>'+
        '<div class="description">සිංහලෙන් මූලික සංකල්ප, සම්පූර්ණ deployment ක්‍රියාවලිය, application workflows සහ recovery.</div>'+
        diagram('cover-flow',[
            ('a',5,10,190,'BUILD','Docker / immutable images','teal'),
            ('b',235,10,190,'DEPLOY','Kind / Helm / migrations','blue'),
            ('c',465,10,190,'OPERATE','Verify / observe / recover','gold')],
            [('a','b'),('b','c')],95,'')+
        '<span class="pill">Java + Python + React</span><span class="pill">PowerShell labs</span><span class="pill">Architecture & workflows</span>'+
        '<div class="cover-bottom">SwiftDrop repository එක ඇසුරින් සකස් කළ ප්‍රායෝගික අත්පොත<br>පළමු සංස්කරණය · 2026 නොවැම්බර් 16 · {{TOTAL}} පිටු</div>')

    add('about','මෙම පොත ගැන','භාවිතය, පරාසය සහ සාක්ෂි','Edition 1 · 16 November 2026 · Sinhala with English technical terms',
        p('මෙම පොත Kubernetes ඉගෙනගන්නා සිසුවෙකුට, SwiftDrop project එක පවත්වාගන්නා developer කෙනෙකුට සහ deployment භාරගන්නා operator කෙනෙකුට එකම මාර්ගෝපදේශයක් සපයයි. Sinhala විස්තර සමඟ commands, API paths සහ configuration identifiers මුල් English ආකාරයෙන් තබා ඇත.')+
        table(['ලකුණ / වර්ගය','කියවිය යුතු ආකාරය'],[
            ('Local verified','Implementation record එකේ සඳහන් local tests සාර්ථකයි. දිනය සහ scope බලන්න.'),
            ('Source-based workflow','Controller / service / tool source අනුව පැහැදිලි කළ ගමන; සියලු paths E2E පරීක්ෂා කර නැත.'),
            ('Illustrative / production pending','ඉගැන්වීමේ sample එකක් හෝ අනාගත acceptance gate එකක්; පවතින deployment එකක් බව නොසලකන්න.')])+
        p('ප්‍රධාන repository සාක්ෂි: <code>kubernetesclusterimplementationplan.md</code>, <code>infra/kubernetes/README.md</code>, <code>infra/kubernetes/RECOVERY.md</code>, AI assistant README, Helm templates, PowerShell scripts, workflow files සහ service controllers. පැරණි README ප්‍රකාශයක් source එකට නොගැළපෙන තැන වත්මන් source behaviour එක භාවිත කර ඇත.')+
        box('පරිසර snapshot එක','Recorded local release: Helm revision 7; context <code>kind-swiftdrop</code>; namespace <code>swiftdrop</code>; URL <code>http://localhost:8088</code>. මෙය පොත සෑදීම සඳහා කළ අලුත් cluster audit එකක් නොවේ.')+
        p('Book සෑදීමේදී application code හෝ live clusters වෙනස් කර නැත. Sinhala font එක PDF තුළ embed කර ඇත. Editable HTML, Python sources, SVG diagrams සහ rebuild instructions companion folder එකේ ඇත.')+
        box('Font සහ sources','Noto Sans Sinhala සඳහා SIL Open Font License පිටපත <code>assets/OFL.txt</code> තුළ ඇත. අවසානයේ නිල documentation links ඇත. Version-sensitive production තීරණ ගන්නා විට ඒ documentation නැවත බලන්න.'),('font',))

    for i in range(1,4):
        add(f'contents-{i}','පටුන',f'පටුන · {i} / 3','Clickable contents · printed page numbers', '')

def make_back():
    add('glossary-a-m','අතිරේක A / ශබ්දකෝෂය','A–M: වැදගත් පද','A quick bilingual glossary',
        table(['පදය','සරල අර්ථය'],[
            ('A · Admission','Resource එක cluster එකට පිළිගැනීමට පෙර policies පරීක්ෂා කරන අදියර.'),
            ('B · Bootstrap','Dependencies සූදානම් කර ප්‍රධාන system එක ආරම්භ කිරීමට පදනම සෑදීම.'),
            ('C · CNI / CSI / CRI','Networking / storage / container runtime සඳහා Kubernetes integration interfaces.'),
            ('D · Deployment / Digest','Replicas සහ rollout පාලකය / image content identity.'),
            ('E · EndpointSlice','Service එකකට backing network endpoints විස්තර කරන resource එක.'),
            ('F · Flyway','Versioned Java-service database migration පද්ධතිය.'),
            ('G · Gateway API','GatewayClass, Gateway, HTTPRoute ආදියෙන් traffic routing ප්‍රකාශ කරන APIs.'),
            ('H · Helm / HPA','Chart-based release management / metrics අනුව Pod replicas වෙනස් කරන පාලකය.'),
            ('I · Ingress / Idempotency','HTTP edge routing rules / request retry එකෙන් duplicate business action නොවීම.'),
            ('J · Job / JWT','අවසන් විය යුතු කාර්යයක් / signed application access token ආකෘතිය.'),
            ('K · kubelet / Kind','Node agent එක / Docker containers තුළ local Kubernetes ධාවනය කරන tool එක.'),
            ('L · Liveness','Container එක restart කිරීම අවශ්‍යද යන්න පරීක්ෂා කරන probe වර්ගය.'),
            ('M · Migration','Database schema / controlled state වෙනසක් version අනුව ක්‍රියාත්මක කිරීම.')])+
        box('පද මතක තබාගන්නා ක්‍රමය','එක් පදයක් සඳහා SwiftDrop උදාහරණයක් සහ failure mode එකක් සම්බන්ධ කරන්න. උදාහරණය: EndpointSlice → gateway Service → NotReady Pod එකක් නිසා endpoint නොලැබීම.'))

    add('glossary-n-z','අතිරේක A / ශබ්දකෝෂය','N–Z: වැදගත් පද','A quick bilingual glossary',
        table(['පදය','සරල අර්ථය'],[
            ('N · Namespace / NetworkPolicy','Resources සඳහා logical scope එක / Pod network traffic allow rules.'),
            ('O · Outbox / OIDC','Transaction එකේම තැබෙන event record / short-lived federated identity protocol.'),
            ('P · Pod / PVC / PDB','Execution unit / storage claim / voluntary disruption availability budget.'),
            ('Q · Queue','Consumer කෙනෙකුට process කිරීමට message රැඳෙන broker structure එක.'),
            ('R · Readiness / RBAC','Traffic භාරගත හැකි බව / API permissions role අනුව පාලනය කිරීම.'),
            ('S · Service / Secret / SSE','Stable network discovery / protected config object / server-to-client event stream.'),
            ('T · Taint / Toleration','Node එකක scheduling සීමාව / ඒ සීමාව පිළිගැනීමට Pod අවසරය.'),
            ('U · Upgrade','Release එකේ values / templates / image version නව revision එකකට මාරු කිරීම.'),
            ('V · Volume','Container එකට mount කරන storage abstraction එක.'),
            ('W · Worker / Workload','Pods ධාවනය කරන node / application execution resources කණ්ඩායම.'),
            ('X · X-Request-ID','Request එක logs සහ services අතර හඳුනාගැනීමට correlation header එකක්.'),
            ('Y · YAML','Manifests සහ configuration සඳහා භාවිත වන text data format එක.'),
            ('Z · Zone','Infrastructure failure domain එක හෝ application delivery area එක; context අනුව අර්ථය වෙනස් වේ.')])+
        p('Kubernetes availability zone එක සහ SwiftDrop delivery pricing zone එක වගකීම් දෙකකි.')+
        box('අවසාන අභ්‍යාසය','Booking එකක් user ට සාර්ථක බව පෙන්වා තිබියදී notification තවම නොලැබෙන අවස්ථාවක්, මේ ශබ්දකෝෂයේ පද පහක් භාවිත කර පැහැදිලි කරන්න.'))

    add('repository-guide','අතිරේක B / සම්බන්ධ files','Repository එකේ කොහෙද බලන්නේ?','Use the maintained implementation files for exact defaults',
        table(['File / directory','අරමුණ'],[
            ('<code>kubernetesclusterimplementationplan.md</code>','Implementation roadmap, recorded progress සහ pending gates.'),
            ('<code>infra/kubernetes/README.md</code>','Current deployment commands සහ local limitations.'),
            ('<code>infra/kubernetes/RECOVERY.md</code>','Rollback, restore, credential rotation සහ incidents.'),
            ('<code>infra/kubernetes/helm/swiftdrop/</code>','Values, templates, migrations, probes, policies සහ storage.'),
            ('<code>infra/kubernetes/scripts/</code>','Create, build, load, deploy, verify සහ DNS helpers.'),
            ('<code>infra/kubernetes/tests/</code>','Chart, script, migration සහ ingress smoke checks.'),
            ('<code>.github/workflows/kubernetes-*.yml</code>','Validation සහ release workflows.'),
            ('<code>services/ai-assistant-service/</code>','Runtime runner, role guards, persistence සහ tool components.'),
            ('<code>services/*/src/main/</code>','Controllers, DTO validation, domain rules සහ migration SQL.'),
            ('<code>packages/common/</code>','Shared domain enums සහ event contracts.'),
            ('<code>docs/DESIGN.md</code>','විශාල design context සහ architecture background.')])+
        box('Book sources','මෙම පොතේ editable sources <code>docs/kubernetes-sinhala/</code> යටතේ ඇත. <code>build_pdf.py</code> HTML render කර Sinhala text, page count, internal links සහ overflow checks කරයි. README එකේ නැවත build කරන commands ඇත.')+
        p('පොත නිර්මාණය කරන අවස්ථාවේ repository එකේ කලින් කළ uncommitted implementation changes පවතී. මෙම පොත Git release commit එකක් හෝ production deployment එකක් නිර්මාණය නොකරයි.'))

    sources=list(SOURCES.items())
    chunks=[sources[i:i+12] for i in range(0,len(sources),12)]
    for group,items in enumerate(chunks,1):
        rows=[]
        for key,(label,url) in items:
            n=list(SOURCES).index(key)+1
            rows.append((f'{n:02d}',f'<strong>{label}</strong><br><a href="{url}">{url}</a>'))
        add(f'references-{group}','අතිරේක C / නිල මූලාශ්‍ර',f'මූලාශ්‍ර · {group} / {len(chunks)}','Primary documentation · version-sensitive guidance should be rechecked',
            '<div class="references">'+table(['#','Documentation'],rows)+'</div>'+
            p('මෙම links අදාළ projects හි නිල documentation වෙත යොමු වේ. Sinhala විස්තර සහ SwiftDrop-specific diagrams මෙම පොත සඳහා සකස් කළේය. Repository snapshot එක සහ අනාගත documentation versions වෙනස් විය හැක.'))
