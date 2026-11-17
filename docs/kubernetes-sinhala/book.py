"""Sinhala book source and reusable layout helpers. No cluster access."""
from dataclasses import dataclass
from html import escape
from pathlib import Path
from textwrap import dedent

ROOT = Path(__file__).resolve().parent
PAGES = []
SOURCES = {
 'components': ('Kubernetes components', 'https://kubernetes.io/docs/concepts/overview/components/'),
 'objects': ('Kubernetes objects', 'https://kubernetes.io/docs/concepts/overview/working-with-objects/'),
 'deployment': ('Deployments', 'https://kubernetes.io/docs/concepts/workloads/controllers/deployment/'),
 'stateful': ('StatefulSets', 'https://kubernetes.io/docs/concepts/workloads/controllers/statefulset/'),
 'jobs': ('Jobs and bounded execution', 'https://kubernetes.io/docs/concepts/workloads/controllers/job/'),
 'service': ('Services and EndpointSlices', 'https://kubernetes.io/docs/concepts/services-networking/service/'),
 'dns': ('DNS for Services and Pods', 'https://kubernetes.io/docs/concepts/services-networking/dns-pod-service/'),
 'ingress': ('Ingress', 'https://kubernetes.io/docs/concepts/services-networking/ingress/'),
 'retirement': ('Ingress NGINX retirement announcement', 'https://kubernetes.io/blog/2025/11/11/ingress-nginx-retirement/'),
 'gateway': ('Gateway API migration guide', 'https://gateway-api.sigs.k8s.io/guides/getting-started/migrating-from-ingress/'),
 'kind': ('Kind quick start', 'https://kind.sigs.k8s.io/docs/user/quick-start/'),
 'kind-issues': ('Kind image-import known issue', 'https://kind.sigs.k8s.io/docs/user/known-issues/#unable-to-kind-load-docker-images'),
 'save': ('Docker image save / platform selection', 'https://docs.docker.com/reference/cli/docker/image/save/'),
 'helm': ('Helm charts', 'https://helm.sh/docs/topics/charts/'),
 'hooks': ('Helm chart hooks', 'https://helm.sh/docs/topics/charts_hooks/'),
 'rollback': ('Helm rollback', 'https://helm.sh/docs/helm/helm_rollback/'),
 'secret': ('Kubernetes Secrets', 'https://kubernetes.io/docs/concepts/configuration/secret/'),
 'configmap': ('ConfigMaps', 'https://kubernetes.io/docs/concepts/configuration/configmap/'),
 'storage': ('Persistent volumes', 'https://kubernetes.io/docs/concepts/storage/persistent-volumes/'),
 'probes': ('Startup, readiness and liveness probes', 'https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/'),
 'resources': ('Resource management', 'https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/'),
 'hpa': ('Horizontal Pod Autoscaling', 'https://kubernetes.io/docs/concepts/workloads/autoscaling/horizontal-pod-autoscale/'),
 'rbac': ('Kubernetes RBAC', 'https://kubernetes.io/docs/reference/access-authn-authz/rbac/'),
 'security': ('Pod Security Standards', 'https://kubernetes.io/docs/concepts/security/pod-security-standards/'),
 'network': ('NetworkPolicy semantics', 'https://kubernetes.io/docs/concepts/services-networking/network-policies/'),
 'drain': ('Safely drain a node', 'https://kubernetes.io/docs/tasks/administer-cluster/safely-drain-node/'),
 'pdb': ('Disruption budgets', 'https://kubernetes.io/docs/concepts/workloads/pods/disruptions/'),
 'logs': ('Kubernetes logging architecture', 'https://kubernetes.io/docs/concepts/cluster-administration/logging/'),
 'rabbit': ('RabbitMQ monitoring and health checks', 'https://www.rabbitmq.com/docs/monitoring'),
 'boot': ('Spring Boot health groups and Kubernetes probes', 'https://docs.spring.io/spring-boot/reference/actuator/endpoints.html'),
 'amqp': ('Spring AMQP message converters', 'https://docs.spring.io/spring-amqp/reference/amqp/message-converters.html'),
 'flyway': ('Flyway migrations', 'https://documentation.red-gate.com/flyway/flyway-concepts/migrations'),
 'alembic': ('Alembic tutorial', 'https://alembic.sqlalchemy.org/en/latest/tutorial.html'),
 'postgres': ('PostgreSQL backup and restore', 'https://www.postgresql.org/docs/17/backup.html'),
 'oidc': ('GitHub Actions OIDC', 'https://docs.github.com/en/actions/concepts/security/openid-connect'),
 'font': ('Noto Sans Sinhala / SIL Open Font License', 'https://github.com/google/fonts/tree/main/ofl/notosanssinhala'),
}

@dataclass
class Page:
    slug: str
    part: str
    title: str
    subtitle: str
    body: str
    refs: tuple = ()

def add(slug, part, title, subtitle, body, refs=()):
    PAGES.append(Page(slug, part, title, subtitle, body, tuple(refs)))

def p(text): return f'<p>{text}</p>'
def h(text): return f'<h2>{text}</h2>'
def code(text, label='PowerShell'):
    # Chapter literals put their first line inline; continuations have an
    # eight-space Python source margin, in addition to real code indentation.
    normalized=dedent('        '+text).strip()
    return f'<div class="code-label">{escape(label)}</div><pre><code>{escape(normalized)}</code></pre>'
def box(title, text, kind='note'):
    return f'<aside class="{kind}"><strong>{title}</strong><p>{text}</p></aside>'
def table(headers, rows):
    return '<table><thead><tr>' + ''.join(f'<th>{x}</th>' for x in headers) + '</tr></thead><tbody>' + ''.join('<tr>'+''.join(f'<td>{x}</td>' for x in row)+'</tr>' for row in rows) + '</tbody></table>'
def steps(items): return '<ol>'+''.join(f'<li>{x}</li>' for x in items)+'</ol>'
def ul(items): return '<ul>'+''.join(f'<li>{x}</li>' for x in items)+'</ul>'
def diagram(name, nodes, edges, height=240, caption=''):
    """Nodes: (id, x, y, width, title, subtitle, colour); edges: (from, to)."""
    colors={'blue':('#eaf0fa','#26466c'),'teal':('#e4f5f1','#087f78'),'gold':('#fff1d5','#9c6500'),'red':('#ffebe8','#a63d32')}
    node_map={n[0]:n for n in nodes}
    pieces=['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 660 '+str(height)+'" role="img" aria-label="'+escape(caption,quote=True)+'">', '<defs><marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse"><path d="M 0 0 L 10 5 L 0 10 z" fill="#668092"/></marker></defs>']
    for a,b in edges:
        na,nb=node_map[a],node_map[b]
        ax,ay,aw=na[1:4]; bx,by,bw=nb[1:4]
        if abs(ay-by)<60:
            x1,x2=(ax+aw,bx) if bx>ax else (ax,bx+bw)
            path=f'M {x1} {ay+29} L {x2} {by+29}'
        else:
            x1,x2=ax+aw/2,bx+bw/2
            y1,y2=(ay+58,by) if by>ay else (ay,by+58)
            mid=(y1+y2)/2
            path=f'M {x1} {y1} L {x1} {mid} L {x2} {mid} L {x2} {y2}'
        pieces.append(f'<path d="{path}" fill="none" stroke="#668092" stroke-width="1.5" marker-end="url(#arrow)"/>')
    for _,x,y,w,title,sub,color in nodes:
        fill,stroke=colors[color]
        pieces.append(f'<rect x="{x}" y="{y}" width="{w}" height="58" rx="9" fill="{fill}" stroke="{stroke}" stroke-width="1.2"/>')
        pieces.append(f'<text x="{x+w/2}" y="{y+24}" text-anchor="middle" font-family="Arial,sans-serif" font-size="12" font-weight="bold" fill="{stroke}">{escape(title)}</text>')
        pieces.append(f'<text x="{x+w/2}" y="{y+43}" text-anchor="middle" font-family="Arial,sans-serif" font-size="10" fill="#40556b">{escape(sub)}</text>')
    pieces.append('</svg>')
    svg=''.join(pieces)
    (ROOT/'assets'/f'{name}.svg').write_text(svg,encoding='utf-8')
    return '<figure>'+svg+f'<figcaption>{caption}</figcaption></figure>'

def render():
    PAGES.clear()
    import chapters_basics, chapters_deploy, chapters_workflows, chapters_operations
    for module in (chapters_basics,chapters_deploy,chapters_workflows,chapters_operations):
        module.populate()
    from front_back import make_front, make_back
    main_pages=PAGES[:]
    PAGES.clear()
    make_front(main_pages)
    PAGES.extend(main_pages)
    make_back()
    total=len(PAGES)
    entries=[(i,x) for i,x in enumerate(PAGES,1) if x.slug not in ('cover','about','contents-1','contents-2','contents-3')]
    chunk=(len(entries)+2)//3
    for group in range(3):
        body=''
        for sequence,(i,x) in enumerate(entries[group*chunk:(group+1)*chunk],group*chunk+1):
            body+=f'<a class="toc-row" href="#{x.slug}"><span class="n">{sequence:02d}</span><span>{x.title}</span><span class="page-no">{i}</span></a>'
        next(x for x in PAGES if x.slug==f'contents-{group+1}').body=body
    sections=[]
    for index,page in enumerate(PAGES,1):
        refs=' · '.join(f'<a href="{escape(SOURCES[r][1],quote=True)}">{escape(SOURCES[r][0])}</a>' for r in page.refs)
        cover=page.slug=='cover'
        heading='' if cover else f'<div class="kicker">{page.part}</div><h1>{page.title}</h1><div class="subtitle">{page.subtitle}</div>'
        sections.append(f'<section class="page {"cover" if cover else ""}" id="{page.slug}"><header><span>SWIFTDROP / KUBERNETES</span><span>සිංහල අත්පොත · 2026</span></header><main>{heading}<div class="content">{page.body.replace("{{TOTAL}}",str(total))}</div>{f"<div class=source>මූලාශ්‍ර: {refs}</div>" if refs else ""}</main><footer><span>Kubernetes A–Z · SwiftDrop</span><span>{index:02d} / {total:02d}</span></footer></section>')
    html='<!doctype html><html lang="si"><head><meta charset="utf-8"><meta name="author" content="SwiftDrop project documentation"><title>Kubernetes A–Z — සිංහල අත්පොත | SwiftDrop</title><link rel="stylesheet" href="book.css"></head><body>'+''.join(sections)+'</body></html>'
    (ROOT/'SwiftDrop-Kubernetes-A-Z-Sinhala.html').write_text(html,encoding='utf-8')
    import json
    (ROOT/'book-index.json').write_text(json.dumps([{'page':i,'id':x.slug,'title':x.title,'part':x.part} for i,x in enumerate(PAGES,1)],ensure_ascii=False,indent=2),encoding='utf-8')
    return PAGES

if __name__=='__main__':
    import book
    print(f'Created HTML: {len(book.render())} designed pages')
