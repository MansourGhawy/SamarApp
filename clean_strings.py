import re

with open('app/build/reports/lint-results-debug.txt', 'r') as f:
    lint_text = f.read()

unused_strings = set()
for match in re.finditer(r'The resource R\.string\.(\w+) appears to be unused', lint_text):
    unused_strings.add(match.group(1))

if not unused_strings:
    print("No unused strings found.")
    exit(0)

with open('app/src/main/res/values/strings.xml', 'r') as f:
    lines = f.readlines()

with open('app/src/main/res/values/strings.xml', 'w') as f:
    skip = False
    for line in lines:
        if '<string ' in line:
            name_match = re.search(r'name="([^"]+)"', line)
            if name_match and name_match.group(1) in unused_strings:
                if '</string>' not in line:
                    skip = True
                continue
        if skip:
            if '</string>' in line:
                skip = False
            continue
        f.write(line)

print(f"Removed {len(unused_strings)} unused strings.")
