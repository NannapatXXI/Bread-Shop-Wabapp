"""Extract controller endpoints and method call graph from the Spring Boot backend."""
import json, os, re, sys

ROOT = sys.argv[1]  # .../backend/src/main/java/com/breadShop/XXI
REPO = sys.argv[2]  # repo root, for relative paths

METHOD_RE = re.compile(
    r'^\s*(?:public|private|protected)\s+(?:static\s+)?(?:final\s+)?(?:synchronized\s+)?'
    r'(?:<[^>]+>\s+)?([\w<>\[\],.? ]+?)\s+(\w+)\s*\(')
FIELD_RE = re.compile(r'^\s*private\s+(?:final\s+)?([\w<>, ]+?)\s+(\w+)\s*(?:=[^;]*)?;')
MAPPING_RE = re.compile(r'@(Get|Post|Put|Patch|Delete|Request)Mapping\s*(?:\((.*)\))?')
CALL_RE = re.compile(r'\b([a-z]\w*)\s*\.\s*(\w+)\s*\(')
LOCAL_CALL_RE = re.compile(r'(?<![\w.])(\w+)\s*\(')
SCHED_RE = re.compile(r'@Scheduled\s*\((.*)\)')
KEYWORDS = {'if', 'for', 'while', 'switch', 'catch', 'return', 'new', 'synchronized', 'super', 'this'}


def mapping_path(args):
    if not args:
        return ''
    m = re.search(r'"([^"]*)"', args)
    return m.group(1) if m else ''


def parse_file(path):
    with open(path, encoding='utf-8') as f:
        lines = f.read().split('\n')
    cls = os.path.splitext(os.path.basename(path))[0]
    fields = {}
    methods = []
    class_mapping = ''
    pending = []
    depth = 0
    i = 0
    while i < len(lines):
        line = lines[i]
        stripped = line.strip()
        if depth == 1 or (depth == 0 and stripped.startswith('@')):
            if stripped.startswith('@'):
                pending.append((i, stripped))
            fm = FIELD_RE.match(line)
            if depth == 1 and fm and '(' not in line.split('=')[0]:
                fields[fm.group(2)] = fm.group(1).strip()
            mm = METHOD_RE.match(line) if depth == 1 else None
            if mm and not stripped.startswith('return') and mm.group(2) != cls or (mm and mm.group(2) == cls):
                # collect signature until '{' or ';'
                j = i
                sig = line
                while '{' not in sig and ';' not in sig and j + 1 < len(lines):
                    j += 1
                    sig += ' ' + lines[j].strip()
                if '{' in sig and not sig.strip().endswith(';'):
                    # body: count braces from line i
                    d = 0
                    k = i
                    started = False
                    while k < len(lines):
                        for ch in lines[k]:
                            if ch == '{':
                                d += 1; started = True
                            elif ch == '}':
                                d -= 1
                        if started and d == 0:
                            break
                        k += 1
                    body = '\n'.join(lines[i:k + 1])
                    annos = [a for (_, a) in pending]
                    http, mpath = None, None
                    sched = None
                    for a in annos:
                        mp = MAPPING_RE.match(a)
                        if mp and mp.group(1) != 'Request':
                            http, mpath = mp.group(1).upper(), mapping_path(mp.group(2))
                        sm = SCHED_RE.match(a)
                        if sm:
                            sched = sm.group(1)
                    vis = stripped.split()[0]
                    methods.append({
                        'name': mm.group(2), 'line': i + 1, 'end': k + 1, 'vis': vis,
                        'http': http, 'path': mpath, 'scheduled': sched,
                        'tx': any(a.startswith('@Transactional') for a in annos),
                        'cacheable': next((a for a in annos if a.startswith('@Cacheable') or a.startswith('@CacheEvict')), None),
                        'body': body,
                    })
                    pending = []
                    # update depth for the consumed lines
                    for kk in range(i, k + 1):
                        pass
                    i = k + 1
                    continue
            if not stripped.startswith(('@', '/', '*')) and stripped and depth == 1:
                pending = []
        if depth == 0 and stripped.startswith('@RequestMapping'):
            class_mapping = mapping_path(MAPPING_RE.match(stripped).group(2))
        depth += line.count('{') - line.count('}')
        if depth == 0 and not stripped.startswith('@'):
            pending = [] if 'class ' in stripped else pending
        i += 1
    return cls, class_mapping, fields, methods


def main():
    classes = {}
    for d, _, files in os.walk(ROOT):
        for fn in files:
            if fn.endswith('.java'):
                p = os.path.join(d, fn)
                cls, cmap, fields, methods = parse_file(p)
                classes[cls] = {
                    'file': os.path.relpath(p, REPO), 'mapping': cmap,
                    'fields': fields, 'methods': methods,
                    'layer': os.path.basename(d),
                }
    known = set(classes)
    out = {}
    for cls, c in classes.items():
        names = {m['name'] for m in c['methods']}
        ms = []
        for m in c['methods']:
            calls = []
            seen = set()
            body = m['body'].split('\n', 1)[1] if '\n' in m['body'] else ''
            for recv, meth in CALL_RE.findall(body):
                t = c['fields'].get(recv)
                t = re.sub(r'<.*', '', t).split()[-1] if t else None
                if t and (t in classes or t.endswith('Repository') or t in ('RestTemplate', 'JavaMailSender', 'PasswordEncoder', 'AuthenticationManager')):
                    key = (t, meth)
                    if key not in seen:
                        seen.add(key); calls.append({'cls': t, 'm': meth})
            for meth in re.findall(r'this::(\w+)', body) + LOCAL_CALL_RE.findall(body):
                if meth in names and meth != m['name'] and meth not in KEYWORDS:
                    key = (cls, meth)
                    if key not in seen:
                        seen.add(key); calls.append({'cls': cls, 'm': meth})
            ms.append({k: v for k, v in m.items() if k != 'body'} | {'calls': calls})
        out[cls] = {k: v for k, v in c.items() if k not in ('methods', 'fields')} | {
            'methods': ms,
            'deps': {n: re.sub(r'<.*', '', t) for n, t in c['fields'].items()
                     if re.sub(r'<.*', '', t) in known or t.endswith('Repository') or t in ('RestTemplate', 'JavaMailSender', 'SseEmitter', 'PasswordEncoder')},
        }
    json.dump(out, sys.stdout, ensure_ascii=False, indent=1)


main()
