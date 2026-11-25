"""Documentation-only local link analysis; no external HTTP requests or database access."""
from pathlib import Path
import re,posixpath,urllib.parse,html,json

INLINE=re.compile(r'!?\[[^\]\n]*\]\(\s*(?P<target><[^>\n]+>|[^\s)]+)(?:\s+["\'][^\n]*?["\'])?\s*\)')
REFERENCE=re.compile(r'^\s{0,3}\[[^\]\n]+\]:\s*(?P<target><[^>\n]+>|[^\s]+)',re.M)
HTML=re.compile(r'(?:href|src)\s*=\s*["\'](?P<target>[^"\']+)["\']',re.I)

def visible_markdown(s):
 out=[];fence=None
 for line in s.splitlines(keepends=True):
  m=re.match(r'^\s*(`{3,}|~{3,})',line)
  if m:
   mark=m.group(1)[0]
   if fence is None:fence=mark
   elif fence==mark:fence=None
   out.append(''.join('\n' if c=='\n' else ' ' for c in line));continue
  if fence:out.append(''.join('\n' if c=='\n' else ' ' for c in line))
  else:out.append(line)
 s=''.join(out)
 return re.sub(r'(`+)(.+?)\1',lambda m:' '*len(m.group()),s)

def links(text):
 s=visible_markdown(text);found=[]
 for pattern in [INLINE,REFERENCE,HTML]:
  for m in pattern.finditer(s):
   a,b=m.span('target');value=text[a:b]
   found.append({'target':value.strip('<>'),'start':a,'end':b,'angle':value.startswith('<'),'line':text[:a].count('\n')+1})
 return sorted(found,key=lambda x:x['start'])

def resolve(doc,target):
 target=html.unescape(target)
 if target.startswith(('/Users/','/private/','/tmp/')):return {'kind':'private-local','target':'LOCAL_PATH'}
 if re.match(r'^[A-Za-z][A-Za-z0-9+.-]*:',target) or target.startswith('//'):return {'kind':'external','target':target}
 part=urllib.parse.urlsplit(target);path=urllib.parse.unquote(part.path)
 absolute=posixpath.normpath(path.lstrip('/') if path.startswith('/') else posixpath.join(posixpath.dirname(doc),path)) if path else doc
 return {'kind':'internal','path':absolute,'fragment':urllib.parse.unquote(part.fragment),'query':part.query}

def anchors(text):
 names=set();counter={}
 for line in visible_markdown(text).splitlines():
  m=re.match(r'^\s{0,3}#{1,6}\s+(.+?)\s*#*\s*$',line)
  if not m:continue
  h=re.sub(r'<[^>]+>','',m.group(1));h=re.sub(r'!?\[([^]]+)\]\([^)]*\)',r'\1',h);h=h.replace('`','').lower()
  h=re.sub(r'[^\w\-\s]','',h);h=re.sub(r'\s','-',h)
  n=counter.get(h,0);counter[h]=n+1;names.add(h+(f'-{n}' if n else ''))
 names.update(re.findall(r'(?:id|name)=["\']([^"\']+)["\']',text))
 return names

def check(repo,paths):
 records=[]
 for name in paths:
  p=repo/name
  if p.suffix.lower()!='.md' or not p.exists():continue
  text=p.read_text()
  for link in links(text):
   r=resolve(name,link['target']);item={'file':name,'line':link['line'],'target':('LOCAL_PATH' if r['kind']=='private-local' else link['target']),'kind':r['kind']}
   if r['kind']=='internal':
    item['resolved']=r['path'];target=repo/r['path']
    item['valid']=target.exists();item['reason']='OK' if item['valid'] else 'MISSING_TARGET'
    if item['valid'] and r['fragment'] and target.suffix=='.md':
     item['valid']=r['fragment'] in anchors(target.read_text());item['reason']='OK' if item['valid'] else 'MISSING_ANCHOR'
   records.append(item)
 return records
