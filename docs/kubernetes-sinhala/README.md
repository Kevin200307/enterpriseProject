# Kubernetes A–Z — සිංහල පොත

SwiftDrop repository එක ඇසුරින් සකස් කළ සම්පූර්ණ ප්‍රායෝගික අත්පොත.

- [PDF පොත](SwiftDrop-Kubernetes-A-Z-Sinhala.pdf)
- [Browser එකෙන් කියවිය හැකි HTML පිටපත](SwiftDrop-Kubernetes-A-Z-Sinhala.html)
- [පටුන / page index](book-index.json)
- [PDF validation වාර්තාව](validation.json)
- [Copy කළ හැකි command / code උදාහරණ](examples.json)

පදනම් සංකල්ප, architecture diagrams, Windows PowerShell installation, Docker images,
Kind import, Helm bootstrap, migrations, customer/merchant/courier/dispatch/admin workflows,
AI assistant, probes, security, CI/CD, troubleshooting, backup/restore සහ production gates
ඇතුළත් වේ. Local verified behaviour, source-based workflows සහ pending production work
පොත තුළ වෙන්කර හඳුන්වා ඇත. PDF සෑදීමෙන් live clusters වෙනස් නොවේ.

## නැවත PDF එක සෑදීම

Python 3.12 සහ Microsoft Edge / Chrome අවශ්‍ය වේ. Repository root එකෙන්:

```powershell
py -3.12 -m venv F:\EAD\.book-tools
F:\EAD\.book-tools\Scripts\python.exe -m pip install -r docs\kubernetes-sinhala\requirements-book.txt
F:\EAD\.book-tools\Scripts\python.exe docs\kubernetes-sinhala\build_pdf.py
```

වෙනත් Chromium browser path එකක් අවශ්‍ය නම්:

```powershell
F:\EAD\.book-tools\Scripts\python.exe docs\kubernetes-sinhala\build_pdf.py --browser 'C:\path\to\chrome.exe'
```

System browser එකක් නොමැති නම් Playwright Chromium install කර script එක ධාවනය කළ හැක:

```powershell
F:\EAD\.book-tools\Scripts\python.exe -m playwright install chromium
```

`book.py`, `chapters_*.py`, `front_back.py` සහ `book.css` editable sources වේ.
SVG diagrams `assets/` තුළ generate වේ. Source වෙනස් කර නැවත build කළ විට පටුන සහ page
numbers නැවත ගණනය වේ. Overflow තිබේ නම් build එක fail වේ; ඒවා සඟවා PDF නිර්මාණය නොකරයි.

සිංහල shaping browser engine එකෙන් සිදුකර **Noto Sans Sinhala** font එක PDF තුළ embed කරයි.
Font source: <https://github.com/google/fonts/tree/main/ofl/notosanssinhala>.
License: [SIL Open Font License](assets/OFL.txt).

`validation.json` එකේ page count, links, Sinhala character count සහ layout result සටහන් වේ.
මෙය PDF artifact verification එකකි; application integration tests හෝ production certification
එයින් නැවත ක්‍රියාත්මක නොවේ.
