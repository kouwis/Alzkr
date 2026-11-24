import re, glob, json, sys
root = sys.argv[1]
keys = []
def add(k):
    if k not in keys: keys.append(k)
pat = re.compile(r'(?<![A-Za-z0-9_])t\("((?:[^"\\]|\\.)*)"')
for f in sorted(glob.glob(root + "/**/*.kt", recursive=True)):
    s = open(f, encoding="utf-8").read()
    for m in pat.finditer(s):
        add(m.group(1).encode().decode("unicode_escape").encode("latin1").decode("utf-8") if "\\" in m.group(1) else m.group(1))
    # dynamic: names given to t() at run time
    for block in re.findall(r"(?:Deed|Occasion)\(\"[^\"]*\", \"([^\"]+)\"", s): add(block)
    for block in re.findall(r"Occasion\(\"([^\"]+)\"", s): add(block)
    for a, b in re.findall(r"Triple\(Color\(0x[0-9A-F]+\), \"([^\"]+)\", \"([^\"]+)\"\)", s): add(a); add(b)
    for name in re.findall(r"(?:Fajr|Sunrise|Dhuhr|Asr|Maghrib|Isha|Egypt|Makkah|MWL|Karachi|ISNA|Dubai|Kuwait|Qatar|Turkey|Middle|Seventh|Angle|None)\(\"([^\"]+)\"", s): add(name)
    for lst in re.findall(r"private val (?:monthsAr|weekdaysAr|gMonthsAr) = listOf\(([^)]*)\)", s, re.S): [add(x) for x in re.findall(r'"([^"]+)"', lst)]
    for a, b in re.findall(r'^\s+[A-Z]\w*\("[a-z_]+", "([^"]+)", "([^"]+)"', s, re.M): add(a); add(b)
    for a, b in re.findall(r'^\s+[A-Z]\w*\("[a-z_]+", "([^"]+)", "([^"]+)"\)', s, re.M): add(a); add(b)
    for a in re.findall(r'^\s+[A-Z]\w*\("[a-z_]+", "([^"]+)"\),', s, re.M): add(a)
    m = re.search(r"private val Cities = listOf\((.*?)\n\)", s, re.S)
    if m: [add(x) for x in re.findall(r'"([^"]+)" to', m.group(1))]
keys = [k for k in keys if re.search(r"[؀-ۿ]", k)]
json.dump(keys, open(sys.argv[2], "w", encoding="utf-8"), ensure_ascii=False, indent=0)
print(len(keys))
