# Documentation Validation Tools

Run from repository root, with Python3 standard library only:

```sh
python3 docs/audits/tools/validate_documentation.py --output docs/audits/evidence/docs_reorganization/VALIDATION.json
```

Checks actual local Markdown/image paths and heading anchors, OpenAPI/JSON local pointers, relocation hashes, frozen evidence bytes, duplicate preservation and protected source/tooling hashes. Uses the recorded baseline commit/path map; no backend/database/network call, package installation or external URL validation. Recorded old paths inside immutable manifests resolve via the path map; they are not edited into newer evidence.

Missing F03 final source remains a review issue; navigation points to the explicit requirements gap rather than a fake specification. Unknown external/local-machine targets are counted as unverified, without logging private paths.
