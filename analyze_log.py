from pathlib import Path
import re
raw = Path('/home/ubuntu/lotus-forge-mod/latest.log').read_bytes()
for enc in ('utf-16', 'utf-16-le', 'cp1251', 'utf-8'):
    try:
        text = raw.decode(enc, errors='replace')
        if 'Render thread' in text or 'ModLauncher' in text:
            break
    except Exception:
        continue
lines = text.splitlines()
pat = re.compile(r'error|exception|crash|caused by|failed|fatal|shutdown', re.I)
for i, line in enumerate(lines):
    if pat.search(line):
        print(f'--- lines {max(0, i-3)}-{min(len(lines), i+5)} ---')
        print('\n'.join(lines[max(0, i-3):min(len(lines), i+5)]))
