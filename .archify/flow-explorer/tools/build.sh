#!/bin/sh
# Regenerate breadshop-flow-explorer.html from the current code. Run from repo root:
#   sh .archify/flow-explorer/tools/build.sh
set -e
T=.archify/flow-explorer/tools
python3 $T/extract.py backend/src/main/java/com/breadShop/XXI . > $T/graph.json
python3 $T/build_data.py $T/graph.json . $T/data.json
python3 - "$T" <<'PY'
import json, sys, datetime
T = sys.argv[1]
t = open(T + '/template.html').read()
t = t.replace('__DATA__', open(T + '/data.json').read(), 1)
t = t.replace('__NOTES__', open(T + '/notes.json').read(), 1)
t = t.replace('__GENERATED__', json.dumps(datetime.datetime.now().strftime('%Y-%m-%d %H:%M')), 1)
open('.archify/flow-explorer/breadshop-flow-explorer.html', 'w').write(t)
print('built .archify/flow-explorer/breadshop-flow-explorer.html')
PY
