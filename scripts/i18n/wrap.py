"""Wraps the Arabic interface strings of the Kotlin sources in t(…), turning templates into {0}-style keys."""
import re, sys, json, glob

AR = re.compile(r"[؀-ۿ]")
SKIP_LINE = re.compile(r"startsWith\(|endsWith\(|contains\(\"|Regex\(|\.replace\(|split\(|SYNONYMS|STOP|BASMALA|== \"|!= \"|DefaultTasbih|\" to \(|\bt\(\"|longDay|digits|charArrayOf|private val (months|weekdays|gMonths)")

def find_literals(line):
    """(start, end, content) of each string literal on the line, with ${…} nesting handled."""
    out, i, n = [], 0, len(line)
    while i < n:
        if line.startswith("//", i):
            break
        if line[i] == "'" and i + 2 < n and line[i + 2] == "'":
            i += 3; continue
        if line[i] == '"':
            if line.startswith('"""', i):
                return out  # raw strings: leave the line alone
            j, depth = i + 1, 0
            while j < n:
                ch = line[j]
                if ch == "\\": j += 2; continue
                if depth == 0 and ch == '"': break
                if line.startswith("${", j): depth += 1; j += 2; continue
                if depth > 0 and ch == "{": depth += 1
                elif depth > 0 and ch == "}": depth -= 1
                elif depth > 0 and ch == '"':
                    # a string inside a template: skip to its end
                    k = j + 1
                    while k < n and line[k] != '"':
                        k += 2 if line[k] == "\\" else 1
                    j = k
                j += 1
            out.append((i, j + 1, line[i + 1:j]))
            i = j + 1
            continue
        i += 1
    return out

def to_key(content):
    """'المواقيت حسب $name ${x.y}' -> ('المواقيت حسب {0} {1}', ['name', 'x.y']) or None if a template holds a string."""
    args, out, i, n = [], [], 0, len(content)
    while i < n:
        if content.startswith("${", i):
            depth, j = 1, i + 2
            while j < n and depth:
                if content[j] == "{": depth += 1
                elif content[j] == "}": depth -= 1
                j += 1
            expr = content[i + 2:j - 1]
            if '"' in expr:
                return None
            out.append("{%d}" % len(args)); args.append(expr); i = j; continue
        m = re.match(r"\$([A-Za-z_][A-Za-z0-9_]*)", content[i:])
        if content[i] == "$" and m:
            out.append("{%d}" % len(args)); args.append(m.group(1)); i += len(m.group(0)); continue
        if content[i] == "\\" and i + 1 < n:
            out.append(content[i:i + 2]); i += 2; continue
        out.append(content[i]); i += 1
    return "".join(out), args

keys, manual = {}, []
for path in sys.argv[1:]:
    lines = open(path, encoding="utf-8").read().split("\n")
    top_list = False
    for li, line in enumerate(lines):
        s = line.strip()
        if re.match(r"^(private |internal )?val [A-Za-z]+ = (listOf|mapOf|setOf)\(", s):
            top_list = not line.startswith(" ")  # top-level list: built once, wrap where it is shown
        if top_list:
            if s.startswith(")"): top_list = False
            continue
        if SKIP_LINE.search(line) or s.startswith("*") or s.startswith("/**") or s.startswith("//"):
            continue
        lits = find_literals(line)
        new, last = [], 0
        for a, b, content in lits:
            if not AR.search(content):
                continue
            before = line[:a].rstrip()
            if re.search(r"(?<![A-Za-z0-9_])t\($", before):
                continue
            k = to_key(content)
            if k is None:
                manual.append(f"{path}:{li+1}: {content}")
                continue
            key, args = k
            # the dictionary key is the string's value at run time (escapes decoded)
            value = key.replace("\\n", "\n").replace('\\"', '"').replace("\\$", "$").replace("\\\\", "\\")
            keys[value] = keys.get(value, 0) + 1
            rep = 't("' + key + '"' + "".join(", " + x for x in args) + ")"
            new.append(line[last:a]); new.append(rep); last = b
        if new:
            new.append(line[last:])
            lines[li] = "".join(new)
    open(path, "w", encoding="utf-8").write("\n".join(lines))
old = set(json.load(open("/home/claude/tools/keys.json"))) if __import__("os").path.exists("/home/claude/tools/keys.json") else set()
json.dump(sorted(set(keys) | old), open("/home/claude/tools/keys.json", "w", encoding="utf-8"), ensure_ascii=False, indent=0)
print(len(keys), "keys;", len(manual), "manual")
for m in manual: print("MANUAL", m)
