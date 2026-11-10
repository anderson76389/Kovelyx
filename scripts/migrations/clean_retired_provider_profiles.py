#!/usr/bin/env python3
"""Remove obsolete account SDK optimization hints; preserve unrelated entries."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
for path in (ROOT/'apps').rglob('*'):
    if not path.is_file():
        continue
    if path.name in ('baseline-prof.txt', 'startup-prof.txt'):
        before = path.read_text()
        after = ''.join(line for line in before.splitlines(True) if not re.search(r'(?i)supabase|postgrest', line))
    elif path.name == 'proguard-rules.pro':
        before = path.read_text()
        after = ''.join(line.replace('Supabase / ', '').replace(' / Supabase', '')
                        for line in before.splitlines(True)
                        if not re.search(r'(?i)(?:^-keep.*supabase|^-dontwarn.*supabase)', line))
    else:
        continue
    if before != after:
        path.write_text(after)
        print(path.relative_to(ROOT), len(before.splitlines()) - len(after.splitlines()), 'obsolete lines removed')
