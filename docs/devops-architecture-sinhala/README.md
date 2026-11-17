# SwiftDrop Architecture & DevOps — සිංහල අත්පොත

Codebase එක මුලින් නොලියූ DevOps engineer කෙනෙකුට project එක තේරුම්ගෙන භාරගැනීමට
සකස් කළ දෙවන පොත. පෙර [Kubernetes A–Z පොත](../kubernetes-sinhala/) සමඟ භාවිත කළ හැක.

- [PDF පොත](SwiftDrop-Architecture-and-DevOps-Sinhala.pdf)
- [PDF, HTML සහ editable sources — complete ZIP](SwiftDrop-Architecture-and-DevOps-Sinhala-complete.zip)
- [Offline browser HTML](SwiftDrop-Architecture-and-DevOps-Sinhala.html)
- [Page index](book-index.json)
- [Source reading map — files 42](source-map.json)
- [Copy කළ හැකි code / command examples](examples.json)
- [PDF validation](validation.json)

Business → architecture → source code → requests / transactions / events → deployment →
operations යන ගමන අනුගමනය කරයි. HTTP / DNS / TLS, frontend, JWT / sessions, data ownership,
outbox, RabbitMQ, tracking, notifications, merchant / dispatch / courier / AI flows, artifacts,
CI/CD, cloud / IaC concepts, observability, SLOs, capacity, incidents සහ restore ඇතුළත් වේ.
Guided labs, self-tests, දින 30ක learning plan එකක් සහ source references ඇත.

Source-derived behaviour, recorded local verification, design inference සහ pending production
capabilities පොත තුළ පැහැදිලිව වෙන්කර ඇත. Book construction එක live cluster හෝ application
code වෙනස් නොකරයි. Actual credentials / .env files source ledger එකට capture නොකරයි.

## නැවත සෑදීම

Repository root එකෙන්, පෙර පොත සඳහා සකස් කළ Python 3.12 tools environment එක භාවිත කරන්න:

```powershell
F:\EAD\.book-tools\Scripts\python.exe -m pip install -r docs\devops-architecture-sinhala\requirements-book.txt
F:\EAD\.book-tools\Scripts\python.exe docs\devops-architecture-sinhala\build_pdf.py
```

Tools environment එක නැති නම් කලින් එය සාදන්න:

```powershell
py -3.12 -m venv F:\EAD\.book-tools
```

System Edge / Chrome හඳුනාගනී. වෙනත් browser path එකකට `--browser 'C:\path\chrome.exe'`
යොදාගත හැක. Browser එකක් නැති නම් tools Python හරහා `-m playwright install chromium` ධාවනය කරන්න.

`book.py`, `chapters_*.py`, `front_back.py`, `evidence.py` සහ `book.css` editable sources වේ.
`evidence.py` original repository හි public source paths, useful lines සහ SHA-256 hashes
නැවත capture කරන නිසා PDF rebuild එක original project tree තුළ සිදුකළ යුතුය. Generated
HTML එක assets folder එක සමඟ offline කියවිය හැක; එයට live cluster එකක් අවශ්‍ය නැත.

Noto Sans Sinhala font එක PDF තුළ self-contained glyph resources ලෙස embed වේ.
License: [SIL Open Font License](assets/OFL.txt).
Layout overflow, missing glyphs, page count සහ internal navigation validate කරයි.
