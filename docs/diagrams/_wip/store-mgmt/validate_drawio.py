"""Validate draw.io files: XML parse, unique page ids, unique cell ids per page, edge ends and parents exist."""
import collections
import sys
import xml.etree.ElementTree as ET

ok = True
for f in sys.argv[1:]:
    pages = ET.parse(f).getroot().findall('diagram')
    unique_pages = len({d.get('id') for d in pages}) == len(pages)
    ok &= unique_pages
    print(f, 'pages', len(pages), 'page ids unique', unique_pages)
    for i, d in enumerate(pages):
        cells = list(d.find('mxGraphModel/root'))
        ids = [c.get('id') for c in cells]
        idset = set(ids)
        dup = [k for k, v in collections.Counter(ids).items() if v > 1]
        bad = [c.get('id') for c in cells if c.get('edge') == '1' and any(
            c.get(k) and c.get(k) not in idset for k in ('source', 'target'))]
        orphan = [c.get('id') for c in cells if c.get('parent') and c.get('parent') not in idset]
        if dup or bad or orphan:
            ok = False
            print('  PROBLEM page', i + 1, d.get('name'), 'dup', dup, 'bad edges', bad, 'orphans', orphan)
print('ALL OK' if ok else 'PROBLEMS')
sys.exit(0 if ok else 1)
