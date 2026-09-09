from pathlib import Path

p = Path(r"../android\app\src\main\res\values\strings.xml")
text = p.read_text(encoding="utf-8")
removals = [
    '    <string name="today_context_hint">РџРѕРіРѕРґР° Рё РґР°РЅРЅС‹Рµ Health Connect</string>\n',
    '    <string name="settings_nav_integrations">РСЃС‚РѕС‡РЅРёРєРё</string>\n',
]
for old in removals:
    if old in text:
        text = text.replace(old, "", 1)
        print("removed:", old.strip()[:50])
    else:
        print("not found:", old.strip()[:50])
p.write_text(text, encoding="utf-8")
print("done")
