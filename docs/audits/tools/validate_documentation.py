"""Validate docs reorganization without network, backend or database access."""
import sys
sys.dont_write_bytecode = True
import argparse
import collections
from datetime import datetime
from zoneinfo import ZoneInfo
import hashlib
import json
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET
import zipfile
from doc_links import check, links


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def normalize_markdown(text):
    text = re.sub(r'<!-- docs-reorganization:notice -->.*?<!-- /docs-reorganization:notice -->\n\n', '', text, flags=re.S)
    for link in reversed(links(text)):
        text = text[:link['start']] + 'LINK_TARGET' + text[link['end']:]
    return text


def strings(value):
    if isinstance(value, str):
        yield value
    elif isinstance(value, dict):
        for item in value.values():
            yield from strings(item)
    elif isinstance(value, list):
        for item in value:
            yield from strings(item)


def refs(value):
    if isinstance(value, dict):
        for key, item in value.items():
            if key == '$ref' and isinstance(item, str):
                yield item
            else:
                yield from refs(item)
    elif isinstance(value, list):
        for item in value:
            yield from refs(item)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    repo = Path(__file__).resolve().parents[3]
    evidence = repo / 'docs/audits/evidence/docs_reorganization'
    mapping = json.loads((evidence / 'PATH_MAP.json').read_text())
    failures = []
    paths = sorted(p.relative_to(repo).as_posix() for p in (repo / 'docs').rglob('*') if p.is_file() and p.name != '.DS_Store' and '__pycache__' not in p.parts)
    paths.append('README.md')
    markdown_links = check(repo, paths)
    internal = [r for r in markdown_links if r['kind'] == 'internal']
    broken = [r for r in internal if not r['valid']]
    failures.extend({'check': 'markdown_link', **r} for r in broken)
    counts = collections.Counter()
    for record in mapping['records']:
        old, new = record['path'], record['finalPath']
        path = repo / new
        if not path.is_file():
            failures.append({'check': 'missing_original', 'path': new})
            continue
        counts['originalFilesPresent'] += 1
        identical = sha(path) == record['sha256']
        frozen = record['extension'] != '.md' or '/evidence/' in old
        if record['extension'] != '.md':
            counts['nonMarkdownChecked'] += 1
        if '/evidence/' in old:
            counts['evidenceFilesChecked'] += 1
        if frozen and not identical:
            failures.append({'check': 'frozen_bytes_changed', 'path': new})
        if identical:
            counts['originalFilesByteIdentical'] += 1
        elif record['extension'] == '.md':
            original = subprocess.check_output(['git', 'show', mapping['baselineCommit'] + ':' + old], cwd=repo).decode()
            counts['changedMarkdownChecked'] += 1
            if normalize_markdown(original) != normalize_markdown(path.read_text()):
                failures.append({'check': 'markdown_content_changed_beyond_links_or_notice', 'path': new})
    for name, expected in mapping['protectedNonDocs'].items():
        counts['protectedSourceAndToolingFiles'] += 1
        if not (repo / name).is_file() or sha(repo / name) != expected:
            failures.append({'check': 'protected_file_changed', 'path': name})
    root = mapping['rootReadme']
    root_preserved = sha(repo / root['archived']) == root['sha256'].strip()
    if not root_preserved:
        failures.append({'check': 'original_root_readme_changed'})
    authority = json.loads((evidence / 'AUTHORITY_REVIEW.json').read_text())
    for group in authority['duplicates']:
        counts['duplicateGroupsChecked'] += 1
        for old in group['paths']:
            name = mapping['pathMap'].get(old, old)
            if sha(repo / name) != group['sha256']:
                failures.append({'check': 'duplicate_changed', 'path': name})
    json_docs = {}
    for name in paths:
        if name.endswith('.json') and name != 'docs/audits/evidence/docs_reorganization/VALIDATION.json':
            try:
                json_docs[name] = json.loads((repo / name).read_text())
            except (ValueError, OSError):
                failures.append({'check': 'invalid_json', 'path': name})
    recorded_paths = []
    for name, value in json_docs.items():
        for ref in refs(value):
            if not ref.startswith('#/'):
                continue
            counts['localJsonPointersChecked'] += 1
            try:
                target = value
                for part in ref[2:].split('/'):
                    part = part.replace('~1', '/').replace('~0', '~')
                    target = target[int(part)] if isinstance(target, list) else target[part]
            except (KeyError, IndexError, TypeError, ValueError):
                failures.append({'check': 'invalid_json_pointer', 'path': name, 'reference': ref})
        # Immutable manifests retain original paths. Resolve only exact repository path strings.
        if any(r['finalPath'] == name for r in mapping['records']):
            for value_string in strings(value):
                if re.fullmatch(r'docs/[A-Za-z0-9_./ -]+\.[A-Za-z0-9]+', value_string):
                    target = mapping['pathMap'].get(value_string, value_string)
                    if not (repo / target).exists():
                        failures.append({'check': 'missing_recorded_json_path', 'path': name, 'target': target})
                    recorded_paths.append({'file': name, 'recorded': value_string, 'resolved': target, 'exists': (repo / target).exists()})
    for name in paths:
        path = repo / name
        if path.suffix == '.drawio':
            try:
                ET.parse(path)
                counts['drawioXmlChecked'] += 1
            except ET.ParseError:
                failures.append({'check': 'invalid_drawio_xml', 'path': name})
        if path.suffix in ('.zip', '.docx'):
            try:
                with zipfile.ZipFile(path) as archive:
                    bad = archive.testzip()
                if bad:
                    failures.append({'check': 'archive_crc', 'path': name})
                counts['archiveCrcChecked'] += 1
            except zipfile.BadZipFile:
                failures.append({'check': 'invalid_archive', 'path': name})
    api = json_docs.get('docs/api/openapi.json', {})
    operations = [(method, path) for path, methods in api.get('paths', {}).items() for method in methods if method.lower() in {'get','post','put','patch','delete','head','options','trace'}]
    counts['openApiOperations'] = len(operations)
    for feature in range(1, 7):
        if not (repo / f'docs/features/F{feature:02}/README.md').is_file():
            failures.append({'check': 'missing_feature_index', 'feature': feature})
    # New documents must not copy private workstation paths. Historic records are frozen.
    originals = {r['finalPath'] for r in mapping['records']} | {root['archived']}
    for name in paths:
        if name not in originals and name.endswith(('.md', '.json')):
            if re.search(r'(?:/Users/[^\s"\']+|/private/tmp/[^\s"\']+)', (repo / name).read_text()):
                failures.append({'check': 'private_workstation_path_in_new_file', 'path': name})
    result = {
        'executedAt': datetime.now(ZoneInfo('Asia/Ho_Chi_Minh')).isoformat(timespec='seconds'),
        'timezone': 'Asia/Ho_Chi_Minh',
        'baselineCommit': mapping['baselineCommit'],
        'scope': 'Documentation structure only; no backend, database or external HTTP calls',
        'status': 'PASS' if not failures else 'FAIL',
        'counts': dict(counts),
        'markdown': {'files': sum(p.endswith('.md') for p in paths), 'internalLinks': len(internal), 'brokenInternalLinks': len(broken), 'externalLinksNotChecked': sum(r['kind'] == 'external' for r in markdown_links), 'privateMachineLinksNotChecked': sum(r['kind'] == 'private-local' for r in markdown_links)},
        'jsonFilesParsed': len(json_docs),
        'recordedJsonPaths': {'checked': len(recorded_paths), 'resolvedByMapOrExistingPath': sum(r['exists'] for r in recorded_paths), 'historicalMissingTargets': [r for r in recorded_paths if not r['exists']]},
        'originalRootReadmePreserved': root_preserved,
        'knownDocumentationReviewGroups': mapping['knownReviewGroups'],
        'missingTrackedSources': mapping['missingTrackedSources'],
        'externalUrls': 'NOT_RUN', 'backendMavenSwaggerTests': 'NOT_RUN (documentation-only scope)',
        'failures': failures,
    }
    output = repo / args.output
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({k: result[k] for k in ('status', 'counts', 'markdown', 'jsonFilesParsed', 'failures')}, ensure_ascii=False, indent=2))
    return 0 if not failures else 1


if __name__ == '__main__':
    sys.exit(main())
