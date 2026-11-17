"""Render and validate the book using Chromium's Sinhala text shaping."""
import argparse
import json
import os
from pathlib import Path
from playwright.sync_api import sync_playwright
from book import ROOT, render

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--browser',help='Chromium/Chrome/Edge executable path')
    parser.add_argument('--screenshots',action='store_true')
    args=parser.parse_args()
    pages=render()
    candidates=[args.browser, os.environ.get('BOOK_BROWSER'), r'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe',r'C:\Program Files\Google\Chrome\Application\chrome.exe']
    browser_path=next((x for x in candidates if x and Path(x).is_file()),None)
    with sync_playwright() as playwright:
        browser=playwright.chromium.launch(headless=True,**({'executable_path':browser_path} if browser_path else {}))
        tab=browser.new_page(viewport={'width':1100,'height':1250},device_scale_factor=1)
        tab.goto((ROOT/'SwiftDrop-Architecture-and-DevOps-Sinhala.html').as_uri(),wait_until='networkidle')
        tab.evaluate('document.fonts.ready')
        tab.emulate_media(media='print')
        issues=tab.evaluate('''() => [...document.querySelectorAll('.page')].flatMap((p,i)=>{const m=p.querySelector('main');let out=[];if(m.scrollHeight>m.clientHeight+2)out.push({page:i+1,title:p.id,overflow:m.scrollHeight-m.clientHeight});for(const el of p.querySelectorAll('pre,td,svg'))if(el.scrollWidth>el.clientWidth+2)out.push({page:i+1,title:p.id,horizontal:el.scrollWidth-el.clientWidth});return out;})''')
        if issues:
            (ROOT/'layout-issues.json').write_text(json.dumps(issues,indent=2),encoding='utf-8')
            raise SystemExit('Layout overflow: '+json.dumps(issues))
        (ROOT/'layout-issues.json').unlink(missing_ok=True)
        examples=tab.evaluate('''() => [...document.querySelectorAll('pre')].map((el,i)=>({number:i+1,section:el.closest('.page').id,language:el.previousElementSibling.textContent,code:el.textContent}))''')
        (ROOT/'examples.json').write_text(json.dumps(examples,ensure_ascii=False,indent=2),encoding='utf-8')
        pdf=ROOT/'SwiftDrop-Architecture-and-DevOps-Sinhala.pdf'
        tab.pdf(path=str(pdf),print_background=True,prefer_css_page_size=True,display_header_footer=False,tagged=True,outline=True)
        if args.screenshots:
            for slug in ['cover','architecture','booking','outbox','failure-map']:
                target=tab.locator('#'+slug)
                if target.count():target.screenshot(path=str(ROOT/'assets'/f'preview-{slug}.png'))
        browser.close()
    import pymupdf
    doc=pymupdf.open(pdf)
    destinations={p.slug:i for i,p in enumerate(pages)}
    import re
    for i,page in enumerate(pages):
        for anchor in re.findall(r'id="([^"]+)"',page.body):destinations[anchor]=i
    # Use explicit page destinations for consistent navigation across PDF readers.
    for page in doc:
        for link in page.get_links():
            name=link.get('nameddest')
            if name:
                if name not in destinations:raise SystemExit(f'Unknown PDF destination: {name}')
                page.update_link({'kind':pymupdf.LINK_GOTO,'xref':link['xref'],'from':link['from'],'page':destinations[name],'to':pymupdf.Point(0,0),'zoom':0})
    metadata=doc.metadata
    metadata.update({'author':'SwiftDrop project documentation','subject':'Understanding SwiftDrop architecture and DevOps operations in Sinhala','keywords':'Architecture, DevOps, Sinhala, SwiftDrop, source code, distributed systems, operations'})
    doc.set_metadata(metadata)
    optimized=ROOT/'book-render.tmp.pdf'
    doc.save(optimized,garbage=4,deflate=True)
    doc.close()
    optimized.replace(pdf)
    doc=pymupdf.open(pdf)
    if len(doc)!=len(pages):raise SystemExit(f'PDF page mismatch: {len(doc)} / {len(pages)}')
    empty=[i+1 for i,p in enumerate(doc) if len(p.get_text().strip())<40]
    if empty:raise SystemExit(f'Unexpected empty pages: {empty}')
    sinhala=sum(sum(0x0D80<=ord(c)<=0x0DFF for c in page.get_text()) for page in doc)
    if sinhala<5000:raise SystemExit(f'Unexpected Sinhala text count: {sinhala}')
    from fontTools.ttLib import TTFont
    font=TTFont(ROOT/'assets/NotoSansSinhala.ttf')
    cmap=font.getBestCmap()
    source=(ROOT/'SwiftDrop-Architecture-and-DevOps-Sinhala.html').read_text(encoding='utf-8')
    missing=sorted({ord(c) for c in source if 0x0D80<=ord(c)<=0x0DFF and ord(c) not in cmap})
    if missing:raise SystemExit(f'Missing Sinhala font glyphs: {missing}')
    fonts={f[0]:f for p in doc for f in p.get_fonts()}
    embedded=all(('/CharProcs' in doc.xref_object(xref)) if f[2]=='Type3' else bool(doc.extract_font(xref)[-1]) for xref,f in fonts.items())
    if not embedded:raise SystemExit('An external font dependency remains')
    links=[link for page in doc for link in page.get_links()]
    if any(l['kind']==pymupdf.LINK_GOTO and not 0<=l['page']<len(doc) for l in links):raise SystemExit('Invalid internal page link')
    if any('\ufffd' in page.get_text() for page in doc):raise SystemExit('Unicode replacement character in PDF text')
    report={'pdf':pdf.name,'pages':len(doc),'sinhala_characters':sinhala,'links':len(links),'internal_page_links':sum(l['kind']==pymupdf.LINK_GOTO for l in links),'external_links':sum(l['kind']==pymupdf.LINK_URI for l in links),'outline_items':len(doc.get_toc()),'code_examples':len(examples),'diagrams':len(list((ROOT/'assets').glob('*.svg'))),'bytes':pdf.stat().st_size,'layout_overflow':0,'missing_sinhala_glyphs':missing,'fonts_self_contained':embedded,'font':'Noto Sans Sinhala (SIL OFL; embedded glyph resources)','reference_review':'2026-11-16','runtime_evidence':'Repository implementation record; no cluster changes performed for this book.'}
    ledger=json.loads((ROOT/'source-map.json').read_text(encoding='utf-8'))
    report['source_files']=len(ledger['files'])
    report['source_commit']=ledger['commit']
    report['source_working_tree_dirty']=ledger['working_tree_dirty']
    (ROOT/'validation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    for i in [0,4,12,min(29,len(doc)-1),len(doc)-1]:
        doc[i].get_pixmap(matrix=pymupdf.Matrix(1.2,1.2)).save(str(ROOT/'assets'/f'pdf-check-{i+1:02d}.png'))
    print(json.dumps(report,ensure_ascii=False))

if __name__=='__main__':main()
