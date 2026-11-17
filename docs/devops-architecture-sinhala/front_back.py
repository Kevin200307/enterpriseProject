import json
from html import escape
from pathlib import Path
from book import add,p,h,box,table,diagram,SOURCES,PAGES,ROOT

def make_front(main_pages):
    add('cover','','Architecture & DevOps','',
        '<div class="eyebrow">SWIFTDROP · ARCHITECTURE · OPERATIONS</div>'+
        '<div class="big">ARCHITECTURE<br>&amp; DEVOPS</div>'+
        '<h1>පද්ධතිය තේරුම්ගෙන<br>විශ්වාසයෙන් මෙහෙයවමු</h1>'+
        '<div class="description">ඔබ නොලියූ codebase එකක් භාරගන්නා DevOps engineer කෙනෙකුට සිංහලෙන් මාර්ගෝපදේශයක්.</div>'+
        diagram('cover-flow',[
            ('u',5,10,190,'UNDERSTAND','Business / architecture / code','teal'),('t',235,10,190,'TRACE','Requests / data / events','blue'),('o',465,10,190,'OPERATE','Release / observe / recover','gold')],
            [('u','t'),('t','o')],95,'')+
        '<span class="pill">Source-guided walkthroughs</span><span class="pill">Failure scenarios</span><span class="pill">Practical learning plan</span>'+
        '<div class="cover-bottom">SwiftDrop project එකේ සැබෑ source code ඇසුරින්<br>දෙවන පොත · 2026 නොවැම්බර් 16 · {{TOTAL}} පිටු</div>')

    add('about','මෙම පොත භාවිත කරන්න','ඔබට අවශ්‍ය project understanding එක','Beginner-friendly foundations with source-based operational depth',
        p('මේ පොතේ අරමුණ codebase එක පාලනය කළ හැකි mental model එකක් ගොඩනැගීමයි. මුලින් business journey එක, පසුව services සහ data, ඊළඟට build / deployment, අවසානයේ failure diagnosis සහ ownership ඉගෙනගන්න. පෙර Kubernetes පොත installation ක්‍රියාවලිය විස්තර කරයි; මෙම පොත project එකේ අර්ථය සහ ක්‍රියාකාරිත්වය විස්තර කරයි.')+
        table(['ලකුණ / වචනය','මෙහි භාවිතය'],[
            ('Source-derived','වත්මන් code එකේ තිබෙන structure / behaviour එක.'),
            ('Recorded local verification','Implementation plan එකේ ලියාපදිංචි කළ test evidence; අලුත් live audit එකක් නොවේ.'),
            ('Design inference','Code structure එකෙන් කළ trade-off විග්‍රහය; මුල් කර්තෘගේ ලියාපදිංචි motive එකක් නොවේ.'),
            ('Illustrative / pending','Teaching example එකක් හෝ acceptance criteria තව සම්පූර්ණ කළ යුතු කාර්යයක්.')])+
        box('Code references භාවිත කරන්න','පිටුවක R12 වැනි link එකක් මත click කළ විට source index එකට යයි. එහි filename, useful line සහ කියවිය යුතු කරුණ ඇත. Full paths සහ SHA-256 hashes <code>source-map.json</code> තුළ ඇත.')+
        p('මෙම edition එක public source files 42ක් සම්බන්ධ කරයි. Repository එකේ uncommitted implementation changes ඇති නිසා Git HEAD එකට අමතරව file hashes සටහන් කර ඇත. Actual Secrets, .env values හෝ live cluster state මෙම book bundle එකට capture කර නැත.')+
        p('සාම්ප්‍රදායික code සහ AI-assisted code දෙකම entry point → runtime wiring → side effect → evidence ලෙස කියවන්න. අසම්පූර්ණ integration එකක් සම්පූර්ණ feature එකක් ලෙස සලකන්නේ නැතිව පැහැදිලිව ලකුණු කර ඇත.'),('font',),('R42',))

    for i in range(1,4):
        add(f'contents-{i}','පටුන',f'පටුන · {i} / 3','Clickable topics · printed page numbers','')

def short_location(path):
    parts=Path(path).parts
    if '/src/main/java/' in path:
        tail=path.split('/src/main/java/',1)[1].split('/swiftdrop/',1)[-1]
        tail=tail.split('/',1)[1] if '/' in tail else tail
        return f'{parts[1]} / {str(Path(tail).parent).replace(chr(92),"/")}'
    if path.startswith('infra/kubernetes/helm/swiftdrop/'):
        return 'Helm / '+str(Path(path).parent.relative_to('infra/kubernetes/helm/swiftdrop')).replace(chr(92),'/')
    return str(Path(path).parent).replace(chr(92),'/')

def make_back():
    add('glossary-system','අතිරේක A / ශබ්දකෝෂය','Architecture සහ data පද','Read the word, explain the consequence, find a project example',
        table(['පදය','සරල අර්ථය'],[
            ('Service / responsibility','එක් business වගකීමකට API / process boundary එකක්.'),
            ('Controller / DTO','HTTP entry point / request-response data shape සහ validation.'),
            ('Repository / entity','Database access abstraction / stored record mapping.'),
            ('Source of truth','Business state එකේ authoritative owner.'),
            ('Transaction / commit','Local data change group එක / එය සාර්ථකව ස්ථිර කිරීම.'),
            ('Idempotency','එකම business request retry කළත් duplicate effect නොවීම.'),
            ('Outbox','Business change එකේ transaction එකේම තබන pending event record.'),
            ('Exchange / queue / ack','Broker routing / work buffer / receipt-processing acknowledgement.'),
            ('Projection / eventual consistency','Read-friendly copy / async delay පසු owners අතර state ගැළපීම.'),
            ('Reconciliation','Uncertain / partial outcomes සසඳා consistent state වෙත ගෙනයාම.'),
            ('JWT / JWKS / ownership','Signed claims / public verification keys / resource access අයිතිය.'),
            ('Webhook / HMAC','Outbound event HTTP request / shared-secret authenticity check.')])+
        box('Self-test','Parcel record එක සහ tracking projection එක දෙකම තිබෙන්නේ ඇයි? ඒ දෙක අතර lag එකක් ඇතිවුවහොත් බලන්නේ කොහෙද?'))

    add('glossary-ops','අතිරේක A / ශබ්දකෝෂය','DevOps සහ operations පද','Connect every term to a release or failure scenario',
        table(['පදය','සරල අර්ථය'],[
            ('Artifact / image / digest','Build output / packaged runtime / content identity.'),
            ('Configuration / Secret','Environment-specific settings / protected credentials සහ trust material.'),
            ('CI / CD / GitOps','Validation integration / delivery-deployment automation / versioned desired-state reconciliation model.'),
            ('Startup / readiness / liveness','Initialization budget / traffic readiness / restart decision.'),
            ('Timeout / backoff / breaker','Operation budget / retry delay / temporary fail-fast gate.'),
            ('Blast radius','Failure එකකින් බලපාන users, operations සහ data සීමාව.'),
            ('SLI / SLO / error budget','Measured experience / target / allowed error portion.'),
            ('Saturation / pool','Resource එක පිරීම / නැවත භාවිත කරන bounded connections.'),
            ('RPO / RTO','Allowed data loss window / recovery time objective.'),
            ('Rollout / rollback','නව runtime spec වෙත යාම / පරණ compatible spec එකකට ආපසු යාම.'),
            ('IaC / drift','Versioned infrastructure definition / actual state එක desired state එකෙන් වෙනස් වීම.'),
            ('Runbook / postmortem','Incident ක්‍රියාවලිය / හේතුව, බලපෑම සහ prevention ගැන විමර්ශනය.')])+
        p('Deployment success, application success සහ business success වෙන්කර evidence රැස්කරන්න. Availability, correctness සහ durability එකම metric එකකින් සම්පූර්ණව මැනිය නොහැක.'))

    ledger=json.loads((ROOT/'source-map.json').read_text(encoding='utf-8'))
    records=ledger['files']
    chunks=[records[i:i+9] for i in range(0,len(records),9)]
    for n,items in enumerate(chunks,1):
        rows=[]
        for item in items:
            name=Path(item['path']).name
            rows.append((f'<span id="src-{item["id"]}"><strong>{item["id"]}</strong></span><br><code>{escape(name)}:{item["line"]}</code>',
                f'{escape(item["purpose"])}<br><span class="small">{escape(short_location(item["path"]))}</span>'))
        add(f'source-code-{n}','අතිරේක B / Source reading index',f'Code map · {n} / {len(chunks)}','Useful symbols and line numbers for the reviewed working tree',
            table(['Reference / file:line','කියවිය යුතු දේ / location'],rows)+
            p('Line numbers source වෙනස් වූ විට මාරුවිය හැක. Full relative paths, focus symbols සහ SHA-256 hashes companion <code>source-map.json</code> තුළ ඇත. Java location labels service root එකට සාපේක්ෂ කෙටි නම් භාවිත කරයි.')+
            box('Snapshot identity',f'Git HEAD: <code>{ledger["commit"][:12]}</code> · reviewed working-tree hashes · {ledger["date"]}. Source capture එක live integration test එකක් නොවේ.'))

    used={ref for page in PAGES for ref in page.refs}
    sources=[(key,value) for key,value in SOURCES.items() if key in used]
    chunks=[sources[i:i+13] for i in range(0,len(sources),13)]
    for group,items in enumerate(chunks,1):
        rows=[]
        for offset,(key,(label,url)) in enumerate(items,1):
            number=(group-1)*13+offset
            rows.append((f'{number:02d}',f'<strong>{escape(label)}</strong><br><a href="{url}">{escape(url)}</a>'))
        add(f'references-{group}','අතිරේක C / නිල මූලාශ්‍ර',f'මූලාශ්‍ර · {group} / {len(chunks)}','Primary documentation for concepts and operational guidance',
            '<div class="references">'+table(['#','Official documentation'],rows)+'</div>'+
            p('Project-specific facts සඳහා code map එක බලන්න. මේ links concepts සහ version-sensitive operational guidance සඳහාය. Production තීරණයට පෙර වත්මන් support / security documentation නැවත බලන්න.'))
