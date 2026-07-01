import re
import os

with open('app/build/reports/lint-results-debug.txt', 'r') as f:
    lint_text = f.read()

unused_drawables = set()
unused_colors = set()
unused_dimens = set()
for match in re.finditer(r'The resource R\.drawable\.(\w+) appears to be unused', lint_text):
    unused_drawables.add(match.group(1))

for drawable in unused_drawables:
    for ext in ['xml', 'png', 'webp', 'jpg']:
        path = f"app/src/main/res/drawable/{drawable}.{ext}"
        if os.path.exists(path):
            os.remove(path)
            print(f"Deleted {path}")

