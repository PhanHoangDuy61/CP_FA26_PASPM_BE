#!/usr/bin/env python3
"""Store management (DEC-20261123-01 draft) edits on the diagram COPIES only.

Usage: python3 paspm-store-diagrams.py <uml.drawio> <erd.drawio>
Edits are string-level insertions/replacements so unrelated bytes stay identical.
"""
import re
import sys
import xml.etree.ElementTree as ET

UML, ERD = sys.argv[1], sys.argv[2]


# ---------------------------------------------------------------- helpers
def page_span(raw, did):
    m = re.search(r'<diagram id="' + re.escape(did) + r'"[^>]*>', raw)
    assert m, did
    end = raw.index('</diagram>', m.end()) + len('</diagram>')
    return m.start(), end


def insert_before_root_end(raw, did, cells):
    s, e = page_span(raw, did)
    i = raw.rindex('</root>', s, e)
    snippet = ''.join('        ' + ET.tostring(c, encoding='unicode') + '\n' for c in cells)
    return raw[:i] + snippet + raw[i:]


def cell_span(raw, did, cid):
    s, e = page_span(raw, did)
    m = re.compile(r'<mxCell id="' + re.escape(cid) + r'"[^>]*?(?:/>|>.*?</mxCell>)', re.S).search(raw, s, e)
    assert m, (did, cid)
    return m.start(), m.end()


def edit_cell(raw, did, cid, fn):
    a, b = cell_span(raw, did, cid)
    el = ET.fromstring(raw[a:b])
    fn(el)
    return raw[:a] + ET.tostring(el, encoding='unicode') + raw[b:]


def insert_after_cell(raw, did, cid, cells):
    a, b = cell_span(raw, did, cid)
    snippet = ''.join('\n        ' + ET.tostring(c, encoding='unicode') for c in cells)
    return raw[:b] + snippet + raw[b:]


def insert_after_page(raw, did, pages):
    _, e = page_span(raw, did)
    snippet = ''.join('\n  ' + ET.tostring(p, encoding='unicode') for p in pages)
    return raw[:e] + snippet + raw[e:]


def vertex(cid, value, style, x, y, w, h, parent='1'):
    c = ET.Element('mxCell', {'id': cid, 'value': value, 'style': style, 'vertex': '1', 'parent': parent})
    ET.SubElement(c, 'mxGeometry', {'x': str(x), 'y': str(y), 'width': str(w), 'height': str(h), 'as': 'geometry'})
    return c


def edge(cid, style, source=None, target=None, points=(), sp=None, tp=None, value='', parent='1', geo_attrs=None, offset=None):
    attrs = {'id': cid, 'value': value, 'style': style, 'edge': '1', 'parent': parent}
    if source:
        attrs['source'] = source
    if target:
        attrs['target'] = target
    c = ET.Element('mxCell', attrs)
    ga = {'relative': '1', 'as': 'geometry'}
    if geo_attrs:
        ga.update(geo_attrs)
    g = ET.SubElement(c, 'mxGeometry', ga)
    if sp:
        ET.SubElement(g, 'mxPoint', {'x': str(sp[0]), 'y': str(sp[1]), 'as': 'sourcePoint'})
    if tp:
        ET.SubElement(g, 'mxPoint', {'x': str(tp[0]), 'y': str(tp[1]), 'as': 'targetPoint'})
    if points:
        a = ET.SubElement(g, 'Array', {'as': 'points'})
        for x, y in points:
            ET.SubElement(a, 'mxPoint', {'x': str(x), 'y': str(y)})
    if offset:
        ET.SubElement(g, 'mxPoint', {'x': str(offset[0]), 'y': str(offset[1]), 'as': 'offset'})
    return c


def page(did, name, model_attrs, cells):
    d = ET.Element('diagram', {'id': did, 'name': name})
    m = ET.SubElement(d, 'mxGraphModel', model_attrs)
    r = ET.SubElement(m, 'root')
    ET.SubElement(r, 'mxCell', {'id': '0'})
    ET.SubElement(r, 'mxCell', {'id': '1', 'parent': '0'})
    for c in cells:
        r.append(c)
    return d


def model(w, h, dx='1600', dy='1000', background=None):
    a = {'dx': dx, 'dy': dy, 'grid': '1', 'gridSize': '10', 'guides': '1', 'tooltips': '1', 'connect': '1',
         'arrows': '1', 'fold': '1', 'page': '1', 'pageScale': '1', 'pageWidth': str(w), 'pageHeight': str(h),
         'math': '0', 'shadow': '0'}
    if background:
        a['background'] = background
    return a


raw = open(UML, encoding='utf-8').read()

# ================================================================ Use Case
UC_STYLE_17 = ('html=0;whiteSpace=wrap;fontFamily=Arial;fontSize=17;fontStyle=0;fontColor=#000000;align=center;'
               'verticalAlign=middle;spacing=0;shadow=0;fillColor=#FFFFFF;strokeColor=#000000;strokeWidth=2;rounded=0;shape=ellipse;')
ASSOC = ('edgeStyle=none;html=0;rounded=0;strokeColor=#000000;strokeWidth=1.4;startArrow=none;exitX=1.0;exitPerimeter=0;'
         'exitDx=0;exitDy=0;entryX={ex};entryY={ey};entryPerimeter=0;entryDx=0;entryDy=0;endArrow=none;exitY={sy};jumpStyle=arc;jumpSize=8;')
uc_cells = [
    vertex('UC34', 'UC34 Manage Stores /\nDeactivate & Reactivate', UC_STYLE_17, 1050, 840, 370, 80),
    vertex('UC35', 'UC35 View / Manage Own\nStore Profile & Images', UC_STYLE_17, 1050, 1485, 370, 94),
    # System Administrator (alias at 140,710) -> UC34 bottom, through the free band under UC33
    edge('assoc-UC34', ASSOC.format(ex='0.5', ey='1.0', sy='0.9'), 'actor-merch-mining-alias', 'UC34',
         [(285, 818), (285, 933), (1235, 933)], (220, 818), (1235, 920)),
    # Store Admin -> UC35 left, through the gap between UC20 and UC21
    edge('assoc-UC35-store', ASSOC.format(ex='0.0', ey='0.5', sy='0.95'), 'actor-store', 'UC35',
         [(506, 1569), (506, 1592), (1030, 1592), (1030, 1532)], (220, 1569), (1050, 1532)),
    # Merchandiser (view only) -> UC35 right, below UC31 and up the right margin
    edge('assoc-UC35-merch', ASSOC.format(ex='1.0', ey='0.5', sy='0.6'), 'actor-merch-f03-alias', 'UC35',
         [(1440, 1842), (1440, 1532)], (220, 1842), (1420, 1532)),
]
raw = insert_before_root_end(raw, 'polished-usecase', uc_cells)

# ================================================================ FR
FR_STYLE = ('html=0;whiteSpace=wrap;fontFamily=Arial;fontColor=#000000;shadow=0;rounded=0;fontSize=17;fontStyle=0;align=left;'
            'verticalAlign=middle;spacing=0;spacingLeft=0;spacingRight=0;spacingTop=0;spacingBottom=0;text=;fillColor=none;strokeColor=none;')
fr_cells = [
    vertex('FR01-function-4', 'FR01.04  Manage Stores (create, edit, slug)', FR_STYLE, 78, 440, 492, 32),
    vertex('FR01-function-5', 'FR01.05  Deactivate / reactivate Stores', FR_STYLE, 78, 474, 492, 32),
    vertex('FR01-function-6', 'FR01.06  Manage own Store profile & images', FR_STYLE, 78, 508, 492, 32),
]
raw = insert_after_cell(raw, 'polished-fr', 'FR01-function-3', fr_cells)

# ================================================================ Screen Flow
SF_NODE = ('html=1;whiteSpace=wrap;overflow=visible;fontFamily=Arial;fontSize=16;fontColor=#28253D;fontSize=14;'
           'fillColor=#ffffff;strokeColor=#28253d;strokeWidth=1.3;spacing=6;rounded=0;')
SF_EDGE = ('html=1;rounded=0;endArrow=open;endFill=0;strokeColor=#333333;strokeWidth=1.4;fontFamily=Arial;fontSize=15;'
           'strokeColor=#000000;endArrow=block;endFill=1;exitX={sx};exitY={sy};exitPerimeter=0;entryX={tx};entryY={ty};entryPerimeter=0;')
sf_cells = [
    vertex('r2-01-node-stores', 'Stores · /admin/stores', SF_NODE, 560.0, 1410.0, 152.0, 66.0),
    vertex('r2-01-node-storeDetail', 'Store Detail &amp; Edit · /admin/stores/:id', SF_NODE, 560.0, 1537.0, 152.0, 87.0),
    vertex('r2-01-node-myStore', 'My Store · /store/profile', SF_NODE, 1404.0, 1537.0, 152.0, 66.0),
    edge('r2-01-edge-41', SF_EDGE.format(sx='0.107', sy='1.0', tx='0.5', ty='0.0'), 'r2-01-node-sa', 'r2-01-node-stores',
         [(660.0, 1142.0), (636.0, 1142.0)], (660.0, 1091.6), (636.0, 1410.0)),
    edge('r2-01-edge-42', SF_EDGE.format(sx='0.5', sy='1.0', tx='0.5', ty='0.0'), 'r2-01-node-stores', 'r2-01-node-storeDetail',
         [], (636.0, 1476.0), (636.0, 1537.0)),
    edge('r2-01-edge-43', SF_EDGE.format(sx='0.928', sy='1.0', tx='0.5', ty='0.0'), 'r2-01-node-sta', 'r2-01-node-myStore',
         [], (1480.0, 1100.0), (1480.0, 1537.0)),
    edge('r2-01-edge-44', SF_EDGE.format(sx='0.106', sy='1.0', tx='1.0', ty='0.5'), 'r2-01-node-merch', 'r2-01-node-myStore',
         [(2575.0, 1122.0), (1680.0, 1122.0), (1680.0, 1570.0)], (2575.0, 1058.0), (1556.0, 1570.0)),
]
raw = insert_before_root_end(raw, 'r2-01-page', sf_cells)

# ================================================================ AD09 (activity)
LANE = 'html=1;whiteSpace=wrap;fontFamily=Helvetica;rounded=0;fillColor={f};strokeColor=#D5DEE6;strokeWidth=1;'
HEAD = 'html=1;whiteSpace=wrap;fontFamily=Helvetica;rounded=0;fillColor=#EAF0F5;strokeColor=#D5DEE6;fontSize=15;fontStyle=1;fontColor=#223247;'
ACT = 'html=1;whiteSpace=wrap;fontFamily=Helvetica;rounded=1;arcSize=14;fillColor=#FFFFFF;strokeColor=#8092A3;strokeWidth=1.2;fontSize=17;fontColor=#223247;spacing=8;'
DEC = 'html=1;whiteSpace=wrap;fontFamily=Helvetica;rhombus;fillColor=#FFFFFF;strokeColor=#245B83;strokeWidth=1.6;fontSize=16;fontColor=#223247;spacing=8;'
MERGE = 'html=1;whiteSpace=wrap;fontFamily=Helvetica;rhombus;fillColor=#FFFFFF;strokeColor=#66788A;strokeWidth=1.6;'
START = 'html=1;whiteSpace=wrap;fontFamily=Helvetica;ellipse;strokeColor=#000000;fillColor=#000000;'
END = 'html=1;whiteSpace=wrap;fontFamily=Helvetica;ellipse;fillColor=#FFFFFF;strokeWidth=1.5;strokeColor=#000000;'
TITLE = 'html=1;whiteSpace=wrap;fontFamily=Helvetica;text;strokeColor=none;fillColor=none;align=left;verticalAlign=middle;fontSize=30;fontColor=#223247;fontStyle=1;spacing=0;'
NOTE = ('shape=note;size=14;html=1;whiteSpace=wrap;fontFamily=Helvetica;align=left;verticalAlign=top;spacing=8;'
        'fillColor=#FFFFFF;strokeColor=#8092A3;strokeWidth=1.2;fontSize=15;fontColor=#223247;')
NOTE_LINK = 'edgeStyle=none;html=1;endArrow=none;dashed=1;dashPattern=4 4;strokeColor=#8092A3;strokeWidth=1.2;'


def aedge(cid, src, tgt, pts=(), value='', exit_=(0.5, 1), entry=(0.5, 0), extra='', geo_attrs=None, offset=None):
    st = ('edgeStyle=none;rounded=1;arcSize=10;html=1;endArrow=open;endFill=0;endSize=9;strokeWidth=1.6;strokeColor=#66788A;'
          f'exitX={exit_[0]};exitY={exit_[1]};exitDx=0;exitDy=0;entryX={entry[0]};entryY={entry[1]};entryDx=0;entryDy=0;'
          'fontFamily=Helvetica;fontSize=14;fontColor=#245B83;labelBackgroundColor=#FFFFFF;' + extra)
    return edge(cid, st, src, tgt, pts, value=value, geo_attrs=geo_attrs, offset=offset)


ad = [
    vertex('lane0', '', LANE.format(f='#FFFFFF'), 48.0, 130, 552.0, 1510),
    vertex('lane0head', 'SYSTEM ADMINISTRATOR', HEAD, 48.0, 130, 552.0, 48),
    vertex('lane1', '', LANE.format(f='#FAFCFD'), 600.0, 130, 552.0, 1510),
    vertex('lane1head', 'PASPM / STORE SERVICE', HEAD, 600.0, 130, 552.0, 48),
    vertex('title', 'Store management', TITLE, 48, 54, 1104, 44),
    vertex('s', '', START, 312.0, 200, 24, 24),
    vertex('open', 'Open the Stores screen', ACT, 174.0, 254, 300, 80),
    vertex('list', 'List Stores with status<br>(default filter: ACTIVE)', ACT, 726.0, 364, 300, 80),
    vertex('action', 'Action?', DEC, 200.0, 474, 248, 108),
    vertex('form', 'Enter name, slug, address,<br>phone; choose icon / banner<br>(PNG or JPEG)', ACT, 174.0, 622, 300, 80),
    vertex('check', 'Check name unique (trim,<br>case-insensitive), slug unique,<br>expectedVersion, image rules', ACT, 726.0, 742, 300, 100),
    vertex('valid', 'Valid?', DEC, 752.0, 872, 248, 108),
    vertex('error', 'Show error (400 / 409)<br>and fix the input', ACT, 174.0, 886, 300, 80),
    vertex('save', 'Save the Store (version + 1);<br>store new image, delete old file', ACT, 726.0, 1010, 300, 80),
    vertex('confirm', 'Confirm deactivate /<br>reactivate', ACT, 174.0, 1124, 300, 80),
    vertex('status', 'Set status ACTIVE &#8644; INACTIVE<br>(PATCH &#8230;/status, expectedVersion)', ACT, 726.0, 1234, 300, 80),
    vertex('effects', 'INACTIVE Store: its users get 403 STORE_INACTIVE (central guard, also at login and '
           'token refresh); schedulers skip it; System Admin cannot create new jobs for it and users cannot '
           'be assigned to it (409 STORE_INACTIVE); in-flight jobs finish; history stays readable; public '
           'images are not served. deactivated_at / deactivated_by are set, and cleared on reactivate.',
           NOTE, 78.0, 1262, 400, 170),
    vertex('merge', '', MERGE, 864.0, 1396, 24, 24),
    vertex('show', 'Show the updated Store', ACT, 726.0, 1450, 300, 80),
    vertex('e', '', END, 862.0, 1570, 28, 28),
    vertex('e-dot', '', START, 868.0, 1576, 16, 16),
    aedge('e1', 's', 'open'),
    aedge('e2', 'open', 'list', [(324.0, 349), (876.0, 349)]),
    aedge('e3', 'list', 'action', [(876.0, 459), (324.0, 459)]),
    aedge('e4', 'action', 'form', value='[create / edit]', extra='spacing=2;', offset=(62, 0)),
    aedge('e5', 'form', 'check', [(324.0, 722), (876.0, 722)]),
    aedge('e6', 'check', 'valid'),
    aedge('e7', 'valid', 'error', value='[no]', exit_=(0, 0.5), entry=(1, 0.5), extra='spacing=2;', offset=(0, -14)),
    aedge('e8', 'error', 'form', [(110, 926), (110, 662)], exit_=(0, 0.5), entry=(0, 0.5)),
    aedge('e9', 'valid', 'save', value='[yes]', extra='spacing=2;', offset=(30, 0)),
    aedge('e10', 'action', 'confirm', [(80, 528), (80, 1164)], value='[deactivate / reactivate]',
          exit_=(0, 0.5), entry=(0, 0.5), extra='spacing=2;', geo_attrs={'x': '-0.86'}, offset=(0, -16)),
    aedge('e11', 'confirm', 'status', [(324.0, 1219), (876.0, 1219)]),
    aedge('e12', 'save', 'merge', [(1100, 1050), (1100, 1408)], exit_=(1, 0.5), entry=(1, 0.5)),
    aedge('e13', 'status', 'merge'),
    aedge('e14', 'merge', 'show'),
    aedge('e15', 'show', 'e'),
    edge('n1', NOTE_LINK, 'effects', 'status', [], value=''),
]
ad09 = page('paspm-unified-ad09', 'AD09 - Store Management', model(1200, 1700, dx='1200', dy='1700', background='#FFFFFF'), ad)
raw = insert_after_page(raw, 'paspm-unified-ad08', [ad09])

# ================================================================ R2-06.07 Store State
S_TITLE = 'html=1;whiteSpace=wrap;overflow=visible;fontFamily=Arial;fontSize=16;fontColor=#28253D;text;strokeColor=none;fillColor=none;align=center;verticalAlign=middle;'
S_INIT = 'html=1;whiteSpace=wrap;overflow=visible;fontFamily=Arial;fontSize=16;fontColor=#28253D;ellipse;aspect=fixed;fillColor=#000000;strokeColor=#000000;strokeWidth=1.5;'
S_STATE = 'html=1;whiteSpace=wrap;overflow=visible;fontFamily=Arial;fontSize=16;fontColor=#28253D;fillColor=#ffffff;strokeColor=#28253d;strokeWidth=1.3;spacing=6;rounded=1;absoluteArcSize=1;arcSize=12;'
S_EDGE = ('html=1;rounded=0;endArrow=open;endFill=0;strokeColor=#333333;strokeWidth=1.4;fontFamily=Arial;fontSize=15;'
          'strokeColor=#28253d;endArrow=open;endFill=0;exitX={sx};exitY={sy};exitPerimeter=0;entryX={tx};entryY={ty};entryPerimeter=0;')
S_LABEL = 'html=1;whiteSpace=wrap;overflow=visible;fontFamily=Arial;fontSize=16;fontColor=#28253D;text;fillColor=#FFFFFF;strokeColor=none;spacing=2;align=center;'
S_NOTE = ('shape=note;size=14;html=1;whiteSpace=wrap;overflow=visible;fontFamily=Arial;fontSize=14;fontColor=#28253D;'
          'fillColor=#ffffff;strokeColor=#28253d;strokeWidth=1.1;align=left;verticalAlign=top;spacing=8;')
S_LINK = 'html=1;rounded=0;endArrow=none;dashed=1;dashPattern=4 4;strokeColor=#28253d;strokeWidth=1.1;'
P = 'r2-08s-'
st = [
    vertex(P + 'title', 'R2-06.07 &#8212; Store State', S_TITLE, 40.0, 20.0, 720.0, 30.0),
    vertex(P + 'node-root_start', '', S_INIT, 192.0, 110.0, 16.0, 16.0),
    vertex(P + 'node-ACTIVE', 'ACTIVE', S_STATE, 120.0, 250.0, 160.0, 48.0),
    vertex(P + 'node-INACTIVE', 'INACTIVE', S_STATE, 520.0, 250.0, 160.0, 48.0),
    edge(P + 'edge-0', S_EDGE.format(sx='0.5', sy='1.0', tx='0.5', ty='0.0'), P + 'node-root_start', P + 'node-ACTIVE',
         [], (200.0, 126.0), (200.0, 250.0)),
    vertex(P + 'edge-label-0', 'Store created by System Admin', S_LABEL, 85.0, 172.0, 230.0, 30.0),
    edge(P + 'edge-1', S_EDGE.format(sx='1.0', sy='0.25', tx='0.0', ty='0.25'), P + 'node-ACTIVE', P + 'node-INACTIVE',
         [], (280.0, 262.0), (520.0, 262.0)),
    vertex(P + 'edge-label-1', 'deactivate [System Admin]', S_LABEL, 290.0, 222.0, 220.0, 30.0),
    edge(P + 'edge-2', S_EDGE.format(sx='0.0', sy='0.75', tx='1.0', ty='0.75'), P + 'node-INACTIVE', P + 'node-ACTIVE',
         [], (520.0, 286.0), (280.0, 286.0)),
    vertex(P + 'edge-label-2', 'reactivate [System Admin]', S_LABEL, 290.0, 296.0, 220.0, 30.0),
    vertex(P + 'note-active', 'ACTIVE: users of the Store sign in and work; schedulers include it; '
           'public branding images are served. On reactivate, deactivated_at / deactivated_by are cleared.',
           S_NOTE, 40.0, 380.0, 320.0, 100.0),
    vertex(P + 'note-inactive', 'INACTIVE: its users get 403 STORE_INACTIVE; schedulers skip it; no new jobs '
           'or user assignments (409 STORE_INACTIVE); in-flight jobs finish; history stays readable; public '
           'images return 404. deactivated_at / deactivated_by are set.',
           S_NOTE, 440.0, 380.0, 320.0, 140.0),
    vertex(P + 'note-rules', 'Each transition checks expectedVersion (stale &#8594; 409). '
           'Stores are never deleted, so there is no final state.', S_NOTE, 40.0, 560.0, 720.0, 50.0),
    edge(P + 'link-active', S_LINK, P + 'note-active', P + 'node-ACTIVE', [], (200.0, 380.0), (200.0, 298.0)),
    edge(P + 'link-inactive', S_LINK, P + 'note-inactive', P + 'node-INACTIVE', [], (600.0, 380.0), (600.0, 298.0)),
]
state_page = page('r2-08s-page', 'R2-06.07 — Store State', model(800, 650), st)
raw = insert_after_page(raw, 'r2-08-page', [state_page])

# ================================================================ R2-08.09 Sequence AD09
Q = 'r2-17-'
parts = [
    ('Admin', 'admin : User', 'actor', '#FDF4FF', '#E879F9'),
    ('UI', 'screen : StoresScreen', 'box', '#F0FDFA', '#2DD4BF'),
    ('Api', 'api : StoreAdministrationController', 'box', '#FFF7ED', '#FB923C'),
    ('ImgApi', 'imagesApi : StoreImageController', 'box', '#FFF7ED', '#FB923C'),
    ('Stores', 'stores : StoreService', 'box', '#F0FDF4', '#4ADE80'),
    ('Images', 'images : StoreImageService', 'box', '#ECFEFF', '#22D3EE'),
    ('Repo', 'repository : StoreRepository', 'box', '#F5F3FF', '#A78BFA'),
    ('Media', 'media : MediaStorage', 'box', '#FDF4FF', '#E879F9'),
    ('StoreUser', 'storeUser : User', 'actor', '#FDF4FF', '#E879F9'),
    ('Auth', 'auth : JwtAuthenticationConverter', 'box', '#FFF7ED', '#FB923C'),
    ('Users', 'users : UserRepository', 'box', '#F5F3FF', '#A78BFA'),
]
CX = {p[0]: 200 + 300 * i for i, p in enumerate(parts)}
W = 200 + 300 * (len(parts) - 1) + 200
script = [
    ('m', 'Admin', 'UI', 'openStores()'),
    ('m', 'UI', 'Api', 'GET /api/system/stores (default status=ACTIVE)'),
    ('m', 'Api', 'Stores', 'list(q, status, page)'),
    ('m', 'Stores', 'Repo', 'findPage(q, status, page)'),
    ('r', 'Repo', 'Stores', 'List&lt;StoreRow&gt;'),
    ('r', 'Stores', 'Api', 'Page&lt;StoreView&gt;'),
    ('r', 'Api', 'UI', '200 Page&lt;StoreView&gt;'),
    ('fs', 'alt', '[Create or edit a Store]'),
    ('m', 'Admin', 'UI', 'submit(name, slug, address, phone)'),
    ('m', 'UI', 'Api', 'POST /api/system/stores | PUT &#8230;/{storeId} (expectedVersion)'),
    ('m', 'Api', 'Stores', 'create(command) | update(storeId, command)'),
    ('m', 'Stores', 'Repo', 'lock; nameTaken(trim, ignore case); slugTaken(slug)'),
    ('m', 'Stores', 'Repo', 'insert(row) | updateWhereVersion(row, expectedVersion)'),
    ('r', 'Repo', 'Stores', 'rows changed (0 &#8594; stale)'),
    ('r', 'Stores', 'Api', 'StoreDetailView'),
    ('r', 'Api', 'UI', '201 / 200 (400 invalid; 409 duplicate or stale)'),
    ('op', '[Upload or replace the icon / banner]'),
    ('m', 'Admin', 'UI', 'chooseImage(file) &#8212; local preview'),
    ('m', 'UI', 'ImgApi', 'PUT /api/stores/{storeId}/images/icon (multipart)'),
    ('m', 'ImgApi', 'Images', 'replace(storeId, ICON, file)'),
    ('m', 'Images', 'Media', 'put(&quot;stores/{storeId}&quot;, bytes, ext) after PNG/JPEG magic-byte, size and dimension checks'),
    ('r', 'Media', 'Images', 'file name (UUID)'),
    ('m', 'Images', 'Repo', 'updateImageKey(storeId, ICON, key)'),
    ('m', 'Images', 'Media', 'delete(old file) after commit; new file deleted on rollback'),
    ('r', 'Images', 'ImgApi', 'StoreView (iconUrl)'),
    ('r', 'ImgApi', 'UI', '200 (400 type or dimensions; 413 size)'),
    ('op', '[Deactivate or reactivate]'),
    ('m', 'Admin', 'UI', 'confirmStatusChange()'),
    ('m', 'UI', 'Api', 'PATCH &#8230;/{storeId}/status {status, expectedVersion}'),
    ('m', 'Api', 'Stores', 'changeStatus(storeId, INACTIVE, expectedVersion)'),
    ('m', 'Stores', 'Repo', 'updateStatus(storeId, INACTIVE, actor, expectedVersion): sets deactivated_at / _by'),
    ('r', 'Repo', 'Stores', '1 row'),
    ('r', 'Stores', 'Api', 'StoreDetailView (INACTIVE)'),
    ('r', 'Api', 'UI', '200 (409 stale version)'),
    ('fe',),
    ('fs', 'opt', '[Store is INACTIVE: a later request by one of its users]'),
    ('m', 'StoreUser', 'Auth', 'any API request (Bearer JWT)'),
    ('m', 'Auth', 'Users', 'findById(userId) &#8212; the row carries the Store status'),
    ('r', 'Users', 'Auth', 'User (storeStatus = INACTIVE)'),
    ('r', 'Auth', 'StoreUser', '403 {&quot;code&quot;: &quot;STORE_INACTIVE&quot;}'),
    ('fe',),
]
MSG = 'html=1;rounded=0;endArrow=open;endFill=0;strokeColor=#333333;strokeWidth=1.4;fontFamily=Arial;fontSize=15;exitX=.5;exitY=.5;entryX=.5;entryY=.5;'
CALL = MSG + 'endArrow=block;endFill=1;'
RET = MSG + 'endArrow=open;endFill=0;dashed=1;dashPattern=5 4;'
ANCHOR = 'html=1;whiteSpace=wrap;overflow=visible;fontFamily=Arial;fontSize=16;fontColor=#28253D;fillColor=none;strokeColor=none;opacity=0;'
MLABEL = 'html=1;whiteSpace=wrap;overflow=visible;fontFamily=Arial;fontSize=16;fontColor=#28253D;text;fillColor=#FFFFFF;strokeColor=none;fontSize=15;align=center;spacing=2;'
FRAME = 'html=1;whiteSpace=wrap;overflow=visible;fontFamily=Arial;fontSize=16;fontColor=#28253D;rounded=0;fillColor=none;strokeColor=#757575;strokeWidth=1.4;'
OPER = 'html=1;whiteSpace=wrap;overflow=visible;fontFamily=Arial;fontSize=16;fontColor=#28253D;fillColor=#F3F3F3;strokeColor=#757575;fontSize=15;align=left;spacingLeft=10;'
GUARD = 'html=1;whiteSpace=wrap;overflow=visible;fontFamily=Arial;fontSize=16;fontColor=#28253D;text;fillColor=#FFFFFF;strokeColor=none;align=left;fontSize=15;'
DIVIDER = 'html=1;rounded=0;endArrow=open;endFill=0;strokeColor=#333333;strokeWidth=1.4;fontFamily=Arial;fontSize=15;endArrow=none;dashed=1;strokeColor=#757575;'
LIFE = 'html=1;rounded=0;endArrow=open;endFill=0;strokeColor=#333333;strokeWidth=1.4;fontFamily=Arial;fontSize=15;endArrow=none;dashed=1;dashPattern=5 5;strokeColor=#777777;'
ACTOR = 'shape=umlActor;html=1;verticalLabelPosition=bottom;verticalAlign=top;align=center;outlineConnect=0;fillColor={f};strokeColor={s};fontFamily=Arial;fontSize=16;whiteSpace=wrap;labelWidth=210;'
PBOX = 'html=1;whiteSpace=wrap;overflow=visible;fontFamily=Arial;fontSize=13;fontColor=#28253D;rounded=1;arcSize=8;fillColor={f};strokeColor={s};spacing={sp};'

seq = [vertex(Q + 'title', 'R2-08.09 &#8212; Sequence AD09', S_TITLE, 40.0, 18, W - 80.0, 32.0)]
frames, msgs = [], []
y, n, fidx = 266, 0, 0
cur = None
for step in script:
    k = step[0]
    if k == 'fs':
        fidx += 1
        top = y - 30
        cur = {'id': fidx, 'op': step[1], 'guard': step[2], 'top': top, 'ops': []}
        y = top + 86
    elif k == 'op':
        div = y - 30
        cur['ops'].append((div, step[1]))
        y = div + 90
    elif k == 'fe':
        cur['bottom'] = y - 30
        frames.append(cur)
        cur = None
        y += 40
    else:
        n += 1
        _, a, b, lab = step
        msgs.append((n, a, b, lab, k == 'r', y))
        y += 62
end_y = y
for f in frames:
    i = f['id']
    seq.append(vertex(Q + f'frame-{i}', '', FRAME, 45.0, f['top'], W - 90.0, f['bottom'] - f['top']))
    seq.append(vertex(Q + f'operator-{i}', f['op'], OPER, 45.0, f['top'], 72.0, 28.0))
    seq.append(vertex(Q + f'guard-{i}', f['guard'], GUARD, 137.0, f['top'] + 7, W - 210.0, 36.0))
    for j, (dy, g) in enumerate(f['ops']):
        seq.append(edge(Q + f'divider-{i}-{j}', DIVIDER, points=(), sp=(45.0, dy), tp=(W - 45.0, dy)))
        seq.append(vertex(Q + f'operand-{i}-{j}', g, GUARD, 137.0, dy + 9, W - 210.0, 32.0))
for pid, label, kind, f, s in parts:
    cx = CX[pid]
    if kind == 'actor':
        seq.append(vertex(Q + 'participant-' + pid, label, ACTOR.format(f=f, s=s), cx - 18.0, 76, 36, 62))
    else:
        seq.append(vertex(Q + 'participant-' + pid, label, PBOX.format(f=f, s=s, sp=10), cx - 105.0, 76, 210.0, 90.0))
    seq.append(edge(Q + 'lifeline-' + pid, LIFE, sp=(cx, 178), tp=(cx, end_y)))
    seq.append(vertex(Q + 'footer-' + pid, label, PBOX.format(f=f, s=s, sp=8), cx - 105.0, end_y + 12, 210.0, 80.0))
for n, a, b, lab, ret, my in msgs:
    xa, xb = CX[a], CX[b]
    seq.append(vertex(Q + f'anchor-s-{n}', '', ANCHOR, xa - 0.5, my - 0.5, 1.0, 1.0))
    seq.append(vertex(Q + f'anchor-t-{n}', '', ANCHOR, xb - 0.5, my - 0.5, 1.0, 1.0))
    seq.append(edge(Q + f'message-{n}', RET if ret else CALL, Q + f'anchor-s-{n}', Q + f'anchor-t-{n}',
                    sp=(xa, my), tp=(xb, my)))
    lo, hi = min(xa, xb), max(xa, xb)
    seq.append(vertex(Q + f'message-label-{n}', f'{n}. {lab}', MLABEL, lo + 15.0, my - 46, hi - lo - 30.0, 40))
seq_h = end_y + 12 + 80 + 60
seq_page = page('r2-17-page', 'R2-08.09 — Sequence AD09', model(float(W), float(seq_h)), seq)

# ================================================================ R2-07.09 Class Diagram Sequence AD09
K = 'r2-17c-page-k-'
CLS = ('html=1;whiteSpace=wrap;overflow=fill;align=left;verticalAlign=top;fontFamily=Arial;fontSize=12;fillColor=#FFFFFF;'
       'strokeColor=#000000;strokeWidth=1.2;rounded=0;spacing=0;spacingLeft=0;')


def cls_value(stereo, name, attrs, ops=None):
    head = '<p style="margin:4px 0 0 0;text-align:center;line-height:16px;">'
    head += (f'<i>&#171;{stereo}&#187;</i><br/>' if stereo else '') + f'<b>{name}</b></p>'
    body = '<hr size="1" style="margin:4px 0;"/><p style="margin:0 0 0 6px;line-height:16px;">' + ('<br/>'.join(attrs) or '&nbsp;') + '</p>'
    if ops is not None:
        body += '<hr size="1" style="margin:4px 0;"/><p style="margin:0 0 0 6px;line-height:16px;">' + '<br/>'.join(ops) + '</p>'
    return head + body


classes = [
    ('SystemAdministrator', 'actor', ['- userId : int', '- role : SYSTEM_ADMIN'], None, 60, 100, 220, 90),
    ('StoresScreen', 'boundary', [], ['+ openStores() : void', '+ submit(form : StoreForm) : void',
                                     '+ chooseImage(kind, file) : void', '+ confirmStatusChange() : void'], 370, 100, 330, 140),
    ('StoreAdministrationController', 'control', ['- stores : StoreService'],
     ['+ list(q, status, page) : Page&lt;StoreView&gt;', '+ create(req) : StoreDetailView',
      '+ get(storeId) : StoreDetailView', '+ update(storeId, req) : StoreDetailView',
      '+ changeStatus(storeId, req) : StoreDetailView'], 800, 100, 450, 180),
    ('StoreImageController', 'control', ['- images : StoreImageService'],
     ['+ put(storeId, kind, file) : StoreView', '+ get(storeId, kind) : image bytes',
      '+ delete(storeId, kind) : StoreView'], 1320, 100, 380, 130),
    ('StoreService', 'control', ['- repository : StoreRepository'],
     ['+ create(cmd) : StoreDetailView', '+ update(storeId, cmd) : StoreDetailView',
      '+ changeStatus(storeId, status, expectedVersion) : StoreDetailView'], 540, 420, 490, 140),
    ('StoreImageService', 'control', ['- repository : StoreRepository', '- media : MediaStorage'],
     ['+ replace(storeId, kind, file) : StoreView', '+ remove(storeId, kind) : StoreView'], 1110, 420, 480, 140),
    ('StoreRepository', 'repository', ['- jdbc : JdbcTemplate'],
     ['+ findPage(q, status, page) : Page&lt;StoreRow&gt;', '+ lockById(storeId : int) : StoreRow',
      '+ nameTaken(name, excludeId) : boolean', '+ slugTaken(slug, excludeId) : boolean',
      '+ insert(row : StoreRow) : int', '+ updateWhereVersion(row, expectedVersion) : int',
      '+ updateStatus(storeId, status, actorId, expectedVersion) : int',
      '+ updateImageKey(storeId, kind, key) : void'], 540, 660, 560, 230),
    ('MediaStorage', 'interface', [], ['+ put(folder, bytes, ext) : String', '+ get(folder, fileName) : StoredMedia',
                                      '+ delete(folder, fileName) : void'], 1200, 660, 390, 120),
    ('LocalMediaStorage', None, ['- root : Path'], ['+ put(folder, bytes, ext) : String', '+ get(folder, fileName) : StoredMedia',
                                                   '+ delete(folder, fileName) : void'], 1200, 880, 390, 120),
    ('Store', 'entity', ['+ storeId : int', '+ name : String', '+ slug : String', '+ status : StoreStatus',
                         '+ address : String', '+ phone : String', '+ iconKey : String', '+ bannerKey : String',
                         '+ version : long', '+ createdAt : Instant', '+ updatedAt : Instant',
                         '+ deactivatedAt : Instant', '+ deactivatedBy : Integer'], None, 60, 660, 400, 270),
    ('StoreStatus', 'enumeration', ['ACTIVE', 'INACTIVE'], None, 760, 1040, 220, 80),
    ('StoreUser', 'actor', ['- userId : int', '- storeId : int'], None, 1780, 100, 220, 90),
    ('JwtAuthenticationConverter', 'control', ['- users : UserRepository', '- guard : StoreStatusGuard'],
     ['+ convert(jwt : Jwt) : Authentication'], 1780, 290, 380, 120),
    ('StoreStatusGuard', 'control', [], ['+ requireActiveStore(user : User) : void'], 2240, 290, 320, 100),
    ('UserRepository', 'repository', ['- jdbc : JdbcTemplate'],
     ['+ findById(userId : int) : User'], 1780, 470, 380, 110),
    ('User', 'entity', ['+ id : int', '+ role : String', '+ storeId : Integer',
                        '+ storeStatus : StoreStatus', '+ version : int'], None, 1780, 660, 380, 140),
]
cl = [vertex('title', 'Class Diagram &#8212; Store Management (Sequence AD09)',
             'text;html=1;whiteSpace=wrap;fontFamily=Arial;fontSize=20;fontStyle=1;align=left;verticalAlign=middle;', 40, 24, 1400, 40)]
for name, stereo, attrs, ops, x, y0, w, h in classes:
    cl.append(vertex(K + name, cls_value(stereo, name, attrs, ops), CLS, x, y0, w, h))
C_BASE = 'edgeStyle=none;rounded=0;html=1;strokeColor=#000000;strokeWidth=1.2;fontFamily=Arial;fontSize=11;'
C_DEP = C_BASE + 'endArrow=open;endFill=0;endSize=9;startArrow=none;dashed=1;dashPattern=6 4;'
C_ASSOC = C_BASE + 'endArrow=open;endFill=0;endSize=9;startArrow=none;'
C_REAL = C_BASE + 'endArrow=block;endFill=0;endSize=12;startArrow=none;dashed=1;dashPattern=6 4;'
C_TXT = 'text;html=1;whiteSpace=nowrap;fontFamily=Arial;fontSize=11;align=center;verticalAlign=middle;strokeColor=none;fillColor=none;spacing=0;'


def cedge(i, style, s, t, ex, en, pts=()):
    st_ = style + (f'exitX={ex[0]:.4f};exitY={ex[1]:.4f};exitDx=0;exitDy=0;exitPerimeter=0;'
                   f'entryX={en[0]:.4f};entryY={en[1]:.4f};entryDx=0;entryDy=0;entryPerimeter=0;')
    return edge(f'r2-17c-page-e{i}', st_, K + s, K + t, pts)


rel = [
    # (style, source, target, exit, entry, points, label, label xy)
    (C_DEP, 'SystemAdministrator', 'StoresScreen', (1, 0.5), (0, 0.3214), (), '&#171;use&#187;', (305, 128)),
    (C_DEP, 'StoresScreen', 'StoreAdministrationController', (1, 0.5), (0, 0.3889), (), '&#171;use&#187;', (730, 154)),
    (C_DEP, 'StoresScreen', 'StoreImageController', (0.5, 0), (0.5, 0), [(535, 75), (1510, 75)], '&#171;use&#187;', (1000, 59)),
    (C_ASSOC, 'StoreAdministrationController', 'StoreService', (0.2222, 1), (0.7347, 0), (), 'stores 1', (930, 395)),
    (C_ASSOC, 'StoreImageController', 'StoreImageService', (0.3421, 1), (0.7083, 0), (), 'images 1', (1460, 395)),
    (C_ASSOC, 'StoreService', 'StoreRepository', (0.5306, 1), (0.4643, 0), (), 'repository 1', (850, 635)),
    (C_ASSOC, 'StoreImageService', 'StoreRepository', (0.1042, 1), (0.9643, 0), [(1160, 610), (1080, 610)], 'repository 1', (1115, 635)),
    (C_ASSOC, 'StoreImageService', 'MediaStorage', (0.6042, 1), (0.4872, 0), (), 'media 1', (1440, 635)),
    (C_REAL, 'LocalMediaStorage', 'MediaStorage', (0.5, 0), (0.5, 1), (), '', None),
    (C_DEP, 'StoreRepository', 'Store', (0, 0.4545), (1, 0.3870), (), '&#171;use&#187;', (500, 748)),
    (C_ASSOC, 'Store', 'StoreStatus', (0.5, 1), (0, 0.5), [(260, 1080)], 'status 1', (690, 1062)),
    (C_DEP, 'StoreUser', 'JwtAuthenticationConverter', (0.5, 1), (0.2895, 0), (), '&#171;use&#187;', (1900, 240)),
    (C_ASSOC, 'JwtAuthenticationConverter', 'StoreStatusGuard', (1, 0.4167), (0, 0.5), (), 'guard 1', (2172, 322)),
    (C_ASSOC, 'JwtAuthenticationConverter', 'UserRepository', (0.5, 1), (0.5, 0), (), 'users 1', (1985, 440)),
    (C_DEP, 'UserRepository', 'User', (0.5, 1), (0.5, 0), (), '&#171;use&#187;', (1980, 625)),
    (C_DEP, 'StoreStatusGuard', 'User', (0.5, 1), (1, 0.5), [(2400, 730)], '&#171;use&#187;', (2410, 560)),
    (C_ASSOC, 'User', 'StoreStatus', (0.5, 1), (1, 0.5), [(1970, 1080)], 'storeStatus 1', (1030, 1066)),
]
for i, (stl, s, t, ex, en, pts, lab, lxy) in enumerate(rel):
    cl.append(cedge(i, stl, s, t, ex, en, pts))
    if lab:
        w_ = 40 if lab.startswith('&#171;') else 9 + 6 * len(lab)
        cl.append(vertex(f'r2-17c-page-t{i}', lab, C_TXT, lxy[0], lxy[1], w_, 14))
class_page = page('r2-17c-page', 'R2-07.09 — Class Diagram Sequence AD09', model(2620, 1200, dx='1519', dy='904'), cl)
raw = insert_after_page(raw, 'r2-16c-page', [seq_page, class_page])

# ================================================================ R2-05 Package Diagram: common.media
PKG = 'Kh7ARh-Gt_CKOKu5ZFXM'
for cid, x in (('SK__com', 170), ('SK__sec', 392), ('SK__per', 614)):
    raw = edit_cell(raw, PKG, cid, lambda el, x=x: (el.find('mxGeometry').set('x', str(x)), el.find('mxGeometry').set('width', '212')))
a, b = cell_span(raw, PKG, 'SK__per')
media = ET.fromstring(raw[a:b])
media.set('id', 'SK__media')
media.set('value', 'common.media\n(MediaStorage: avatars, Store images)')
media.find('mxGeometry').set('x', '836')
raw = insert_after_cell(raw, PKG, 'SK__per', [media])

open(UML, 'w', encoding='utf-8').write(raw)

# ================================================================ ERD
raw = open(ERD, encoding='utf-8').read()
NEW_COLS = [
    ('slug', 'UK slug VARCHAR(50) NOT NULL [V27]'),
    ('status', ' status VARCHAR(10) NOT NULL [V27]'),
    ('address', ' address VARCHAR(255) NULL [V27]'),
    ('phone', ' phone VARCHAR(30) NULL [V27]'),
    ('icon_key', ' icon_key VARCHAR(512) NULL [V27]'),
    ('banner_key', ' banner_key VARCHAR(512) NULL [V27]'),
    ('version', ' version BIGINT NOT NULL [V27]'),
    ('created_at', ' created_at TIMESTAMP NOT NULL [V27]'),
    ('updated_at', ' updated_at TIMESTAMP NOT NULL [V27]'),
    ('deactivated_at', ' deactivated_at TIMESTAMP NULL [V27]'),
    ('deactivated_by', 'FK deactivated_by INT NULL [V27]'),
]
GROW = 20 * len(NEW_COLS)  # 220


def add_rows(raw, did, prefix):
    a, b = cell_span(raw, did, prefix + '-t-store-r-mining_schedule')
    tmpl = ET.fromstring(raw[a:b])
    w = tmpl.find('mxGeometry').get('width')
    rows = []
    for i, (col, text) in enumerate(NEW_COLS):
        r = vertex(prefix + '-t-store-r-' + col, text, tmpl.get('style'), 0, 110 + 20 * i, w, 20,
                   parent=prefix + '-t-store')
        g = r.find('mxGeometry')
        del g.attrib['x']
        rows.append(r)
    return insert_after_cell(raw, did, prefix + '-t-store-r-mining_schedule', rows)


def set_geo(**kw):
    def f(el):
        g = el.find('mxGeometry')
        for k, v in kw.items():
            g.set(k, str(v))
    return f


def set_points(pts):
    def f(el):
        g = el.find('mxGeometry')
        a = g.find('Array')
        for p in list(a):
            a.remove(p)
        for x, y in pts:
            ET.SubElement(a, 'mxPoint', {'x': str(x), 'y': str(y)})
    return f


# ---- Logical ERD: store grows upward (bottom edge stays at 1480) into the band above Identity & Access
OLD_TOP, OLD_H, NEW_TOP, NEW_H = 1360.0, 120.0, 1360.0 - GROW, 120.0 + GROW
raw = add_rows(raw, 'erd-logical', 'erd-logical')
raw = edit_cell(raw, 'erd-logical', 'erd-logical-t-store', set_geo(y=NEW_TOP, height=NEW_H))
raw = edit_cell(raw, 'erd-logical', 'erd-logical-a-IdentityAccess', set_geo(y=1100.0, height=750))
raw = edit_cell(raw, 'erd-logical', 'erd-logical-title',
                lambda el: el.set('value', 'Logical ERD — Flyway V1–V26 (dev 530651b) + V27 planned: store columns [V27]'))
raw = edit_cell(raw, 'erd-logical', 'erd-logical-title', set_geo(width=1500))
# keep the absolute y of every edge end that lands on a side of the store table
s, e = page_span(raw, 'erd-logical')
fixed = []
ids = re.findall(r'<mxCell id="(erd-logical-fk-\d+)"', raw[s:e])
for cid in ids:
    a, b = cell_span(raw, 'erd-logical', cid)
    el = ET.fromstring(raw[a:b])
    if el.get('target') != 'erd-logical-t-store':
        continue
    stl = el.get('style')
    ex = float(re.search(r'entryX=([0-9.]+)', stl).group(1))
    ey = float(re.search(r'entryY=([0-9.]+)', stl).group(1))
    if ex in (0.0, 1.0) and 0.0 < ey < 1.0:  # side entry: keep absolute y
        ny = (OLD_TOP + ey * OLD_H - NEW_TOP) / NEW_H
        new_stl = re.sub(r'entryY=[0-9.]+', f'entryY={ny:.4f}', stl)
        raw = edit_cell(raw, 'erd-logical', cid, lambda el, v=new_stl: el.set('style', v))
        fixed.append((cid, ey, round(ny, 4)))
print('logical side entries re-based:', fixed)
# edges that ran down the old free band or end on the store_id row
raw = edit_cell(raw, 'erd-logical', 'erd-logical-fk-5', set_points([(1280, 220), (1280, NEW_TOP + 30)]))
raw = edit_cell(raw, 'erd-logical', 'erd-logical-fk-6', set_points([(550, 540), (550, 580), (1250, 580), (1250, NEW_TOP + 50)]))
raw = edit_cell(raw, 'erd-logical', 'erd-logical-fk-10', set_points([(2510, 540), (2510, NEW_TOP + 30), (1770, NEW_TOP + 30)]))
raw = edit_cell(raw, 'erd-logical', 'erd-logical-fk-16', set_points(
    [(2490, 220), (2490, 440), (2460, 440), (2460, 480), (1720, 480), (1720, NEW_TOP - 10), (1730, NEW_TOP - 10)]))

# two long product edges used the old free band at y=1180/1190; lift them above the enlarged store table
def lift(y_old, y_new):
    def f(el):
        for p in el.find('mxGeometry').iter('mxPoint'):
            if p.get('y') is not None and float(p.get('y')) == y_old:
                p.set('y', str(y_new))
    return f


raw = edit_cell(raw, 'erd-logical', 'erd-logical-fk-66', lift(1180.0, 1115))
raw = edit_cell(raw, 'erd-logical', 'erd-logical-fk-90', lift(1190.0, 1125))

ERD_FK = ('edgeStyle=none;rounded=0;html=1;strokeColor=#000000;strokeWidth=1.2;startArrow=ERzeroToMany;startSize={sz};startFill=0;'
          'endArrow=ERzeroToOne;endSize={sz};endFill=0;exitX=0.5;exitY=1;exitDx=0;exitDy=0;exitPerimeter=0;'
          'entryX=0.5;entryY=0;entryDx=0;entryDy=0;entryPerimeter=0;')
FKL = 'text;html=0;strokeColor=none;fillColor=none;align=center;verticalAlign=middle;spacing=0;whiteSpace=nowrap;fontFamily=Arial;fontSize={fs};fontStyle=2;'
raw = insert_before_root_end(raw, 'erd-logical', [
    edge('erd-logical-fk-100', ERD_FK.format(sz=10), 'erd-logical-t-store-r-deactivated_by', 'erd-logical-t-app_user'),
    vertex('erd-logical-fkl-100', 'deactivated_by', FKL.format(fs=13), 1532, 1508, 105, 17),
])

# ---- Identity & Access page: store grows downward; everything below it moves down by GROW
DID, PRE = 'erd-identityaccess', 'erd-identityaccess'
raw = add_rows(raw, DID, PRE)
raw = edit_cell(raw, DID, PRE + '-t-store', set_geo(height=120 + GROW))
raw = edit_cell(raw, DID, PRE + '-title', lambda el: el.set('value', 'Identity & Access — store: V27 planned columns marked [V27]'))
s, e = page_span(raw, DID)
ids = re.findall(r'<mxCell id="([^"]+)"', raw[s:e])
LIMIT = 260  # old store bottom is 250
moved = []
for cid in ids:
    a, b = cell_span(raw, DID, cid)
    el = ET.fromstring(raw[a:b])
    if el.get('parent') != '1':
        continue
    g = el.find('mxGeometry')
    if g is None:
        continue
    changed = False
    if el.get('vertex') == '1' and g.get('y') is not None and float(g.get('y')) > LIMIT:
        g.set('y', str(float(g.get('y')) + GROW).rstrip('0').rstrip('.'))
        changed = True
    if el.get('edge') == '1':
        for p in g.iter('mxPoint'):
            if p.get('y') is not None and float(p.get('y')) > LIMIT:
                p.set('y', str(float(p.get('y')) + GROW).rstrip('0').rstrip('.'))
                changed = True
    if changed:
        raw = raw[:a] + ET.tostring(el, encoding='unicode') + raw[b:]
        s, e = page_span(raw, DID)
        moved.append(cid)
print('identity moved:', len(moved), moved)
raw = insert_before_root_end(raw, DID, [
    edge(PRE + '-fk-7', ERD_FK.format(sz=14), PRE + '-t-store-r-deactivated_by', PRE + '-t-app_user'),
    vertex(PRE + '-fkl-7', 'deactivated_by', FKL.format(fs=10), 802, 500, 75, 14),
])
raw = re.sub(r'(<diagram id="erd-identityaccess"[^>]*>\s*<mxGraphModel[^>]*pageHeight=")([0-9.]+)"',
             lambda m: m.group(1) + str(int(float(m.group(2)) + GROW)) + '"', raw)
open(ERD, 'w', encoding='utf-8').write(raw)
print('done')
