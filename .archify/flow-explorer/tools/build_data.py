"""Combine backend call graph + frontend API usage into the explorer data model."""
import json, os, re, sys

graph_path, repo, out_path = sys.argv[1], sys.argv[2], sys.argv[3]
g = json.load(open(graph_path))
FE = os.path.join(repo, 'frontend')


def norm(path):
    path = path.split('?')[0]
    path = re.sub(r'\$\{[^}]+\}', '{}', path)
    path = re.sub(r'\{[^}]*\}', '{}', path)
    return path.rstrip('/') or '/'


# ---------- backend endpoints ----------
endpoints = []
for cls, c in g.items():
    if c['layer'] != 'controller':
        continue
    for m in c['methods']:
        if m['http']:
            full = (c['mapping'] or '') + (m['path'] or '')
            endpoints.append({'http': m['http'], 'path': full, 'key': m['http'] + ' ' + norm(full),
                              'cls': cls, 'm': m['name'], 'line': m['line'], 'callers': []})
ep_by_key = {e['key']: e for e in endpoints}

# ---------- frontend ----------
CALL = re.compile(r'\bapi\.(get|post|put|patch|delete)\(\s*[`"\']([^`"\']+)')
EXPORT = re.compile(r'export\s+(?:const|async function|function)\s+(\w+)')
ALIAS = re.compile(r'export\s+const\s+(\w+)\s*=\s*(\w+)\s*;')
svc_fns = {}  # name -> {file, line, key}
js_files = []
for base in ('app', 'lib', 'services'):
    for d, _, files in os.walk(os.path.join(FE, base)):
        for fn in files:
            if fn.endswith('.js'):
                js_files.append(os.path.join(d, fn))

for p in js_files:
    rel = os.path.relpath(p, repo)
    lines = open(p, encoding='utf-8').read().split('\n')
    current = None
    for i, line in enumerate(lines):
        em = EXPORT.search(line)
        if em and '/services/' in p:
            current = (em.group(1), i + 1)
        am = ALIAS.search(line)
        if am and '/services/' in p and am.group(2) in svc_fns:
            svc_fns[am.group(1)] = dict(svc_fns[am.group(2)], alias_of=am.group(2))
        for meth, path in CALL.findall(line):
            if not path.startswith('/api'):
                continue
            key = meth.upper() + ' ' + norm(path)
            if '/services/' in p and current:
                svc_fns[current[0]] = {'file': rel, 'line': current[1], 'key': key}
            elif key in ep_by_key:
                ep_by_key[key]['callers'].append({'file': rel, 'line': i + 1, 'via': None})

# EventSource for SSE
for p in js_files:
    txt = open(p, encoding='utf-8').read()
    if 'new EventSource' in txt:
        ln = txt[:txt.index('new EventSource')].count('\n') + 1
        k = 'GET /api/v1/notifications/stream'
        if k in ep_by_key:
            ep_by_key[k]['callers'].append({'file': os.path.relpath(p, repo), 'line': ln, 'via': 'EventSource'})

# usages of service functions in app files
for p in js_files:
    if '/services/' in p:
        continue
    rel = os.path.relpath(p, repo)
    lines = open(p, encoding='utf-8').read().split('\n')
    imported = set()
    for m in re.finditer(r'import\s*\{([^}]*)\}\s*from\s*["\']@/services/', '\n'.join(lines)):
        imported |= {x.strip().split(' as ')[0] for x in m.group(1).split(',') if x.strip()}
    for name in imported:
        if name not in svc_fns:
            continue
        for i, line in enumerate(lines):
            if re.search(r'\b' + name + r'\s*\(', line) and 'import' not in line:
                key = svc_fns[name]['key']
                if key in ep_by_key:
                    ep_by_key[key]['callers'].append({'file': rel, 'line': i + 1,
                                                      'via': name, 'svc': svc_fns[name]})
                break

# ---------- trim backend graph for the page ----------
classes = {}
for cls, c in g.items():
    classes[cls] = {
        'file': c['file'], 'layer': c['layer'],
        'methods': {m['name']: {'line': m['line'], 'end': m['end'], 'calls': m['calls'], 'tx': m['tx'],
                                'cache': m['cacheable'], 'sched': m['scheduled'], 'vis': m['vis']}
                    for m in c['methods']},
    }

json.dump({'endpoints': endpoints, 'classes': classes, 'repo': os.path.abspath(repo)},
          open(out_path, 'w'), ensure_ascii=False)

unmatched = [k for k, v in svc_fns.items() if v['key'] not in ep_by_key]
print('endpoints', len(endpoints), 'with FE callers', sum(1 for e in endpoints if e['callers']))
print('unmatched FE service fns', unmatched)
print('no caller', [e['key'] for e in endpoints if not e['callers']])
