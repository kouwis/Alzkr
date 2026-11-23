import json, re, sys
new = json.load(open(__import__("os").path.join(__import__("os").path.dirname(__file__), "keys.json")))
langs = ["en", "fr", "tr", "de", "es", "id", "ms"]
out = {l: {} for l in langs}
errs = 0
import glob
for f in sorted(glob.glob(__import__("os").path.join(__import__("os").path.dirname(__file__), "tr*.tsv"))):
    for line in open(f, encoding="utf-8").read().split("\n"):
        if not line.strip(): continue
        cols = line.split("\t")
        key = cols[0].replace("⏎", "\n")
        i = cols[0]
        if len(cols) != 8: print("COLS", f, i, len(cols)); errs += 1; continue
        lead = re.match(r"^[\s\n]*", key).group(0); trail = re.search(r"[\s\n]*$", key).group(0)
        for l, v in zip(langs, cols[1:]):
            v = v.replace("⏎", "\n")
            inner = v.strip(" ")
            v = lead + inner.strip("\n").strip(" ") + trail if not ("\n" in lead or "\n" in trail) else v
            if sorted(re.findall(r"\{\d\}", v)) != sorted(re.findall(r"\{\d\}", key)):
                print("PH", l, i, repr(key), repr(v)); errs += 1
            if v.count("\n") != key.count("\n"): print("NL", l, i, repr(v)); errs += 1
            out[l][key] = v
EXTRA = {"مكية": ["Meccan","Mecquoise","Mekki","Mekkanisch","Mecana","Makkiyah","Makkiyyah"],
         "مدنية": ["Medinan","Médinoise","Medeni","Medinensisch","Medinense","Madaniyah","Madaniyyah"]}
for k, vs in EXTRA.items():
    for l, v in zip(langs, vs): out[l][k] = v
missing = [k for k in new if k not in out["en"]]
print("missing", len(missing), missing[:10], "errs", errs)
dst = sys.argv[1]
for l in langs:
    json.dump(out[l], open(f"{dst}/{l}.json", "w", encoding="utf-8"), ensure_ascii=False, indent=1, sort_keys=True)
