#!/usr/bin/env bash
#
# Write the SPDX SBOM for a built APK.
#
#   scripts/sbom.sh <apk> [output.spdx.json]
#
# The document combines two real sources, because neither alone describes the
# release:
#
#   * syft scans the unpacked APK. It reads the Go build info of the bundled
#     Hysteria core, so that component and its module graph are listed once (the
#     three other ABI copies of the same library are excluded: they are
#     byte-identical modulo the ABI and would duplicate every package).
#   * Licensee's report (`app/build/reports/licensee/androidRelease/artifacts.json`,
#     produced by `./gradlew licenseeRelease`) lists the Maven artefacts the
#     release actually links. syft cannot see those: the code is DEX by the time
#     it is packaged and the JARs are gone.
#
# The result is deterministic for a given commit: the namespace is derived from
# the version and commit, `created` is the commit date instead of the clock, and
# packages and relationships are sorted.
#
# syft's version is not pinned here (CI pins it); the document records whichever
# version produced it.
set -euo pipefail

APK="${1:-app/build/outputs/apk/release/app-universal-release.apk}"
OUT="${2:-}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

die() { echo "sbom: $*" >&2; exit 1; }

[ -f "$APK" ] || die "no APK at $APK (build one first, or pass a path)"
command -v syft > /dev/null || die "syft is not on PATH (CI installs a pinned release)"

version="${HY2_VERSION_NAME:-$(bash scripts/version.sh name)}"
commit="$(git rev-parse HEAD)"
created="$(git log -1 --format=%cI)"
[ -n "$OUT" ] || OUT="hy2droid-$version.spdx.json"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "==> unpacking $APK" >&2
unzip -q "$APK" -d "$TMP/apk"

echo "==> syft on the unpacked APK" >&2
syft "dir:$TMP/apk" \
  --exclude './lib/armeabi-v7a/**' \
  --exclude './lib/x86/**' \
  --exclude './lib/x86_64/**' \
  -o spdx-json -q > "$TMP/syft.spdx.json"

echo "==> merging the Maven dependency list" >&2
VERSION="$version" COMMIT="$commit" CREATED="$created" OUT="$OUT" \
  python3 - "$TMP/syft.spdx.json" \
      app/build/reports/licensee/androidRelease/artifacts.json <<'PY'
import json
import os
import re
import sys

syft_path, licensee_path = sys.argv[1], sys.argv[2]
doc = json.load(open(syft_path))

version = os.environ['VERSION']
commit = os.environ['COMMIT']
created = os.environ['CREATED']
out = os.environ['OUT']

# Deterministic identity: same commit, same namespace and document name.
doc['documentNamespace'] = (
    f'https://github.com/noelbennettz/Hy2droid/spdx/{version}-{commit[:12]}')
doc['name'] = f'hy2droid-{version}'
doc['creationInfo']['created'] = created
doc['creationInfo'].setdefault('creators', []).append('Tool: hy2droid scripts/sbom.sh')

# syft describes the unpacked directory; describe the app itself instead and hang
# everything off it.
described = [r for r in doc['relationships'] if r['relationshipType'] == 'DESCRIBES']
if not described:
    raise SystemExit('sbom: syft produced no DESCRIBES relationship')
app_id = 'SPDXRef-Package-hy2droid'
root_id = described[0]['relatedSpdxElement']

packages = doc.setdefault('packages', [])
for package in packages:
    if package['SPDXID'] == root_id:
        package.update({
            'SPDXID': app_id,
            'name': 'hy2droid',
            'versionInfo': version,
            'comment': f'commit {commit}',
            'externalRefs': [{
                'referenceCategory': 'PACKAGE-MANAGER',
                'referenceType': 'purl',
                'referenceLocator': f'pkg:github/noelbennettz/Hy2droid@{commit}',
            }],
        })
        break

# syft derived that id (and the package name) from the temporary extraction
# directory, which would make the document different on every run; rename it
# everywhere it is referenced.
for package in packages:
    for key in ('SPDXID',):
        if package.get(key) == root_id:
            package[key] = app_id
for rel in doc['relationships']:
    if rel.get('spdxElementId') == root_id:
        rel['spdxElementId'] = app_id
    if rel.get('relatedSpdxElement') == root_id:
        rel['relatedSpdxElement'] = app_id
for rel in described:
    rel['comment'] = 'the built APK'
    rel['relatedSpdxElement'] = app_id
    rel['spdxElementId'] = 'SPDXRef-DOCUMENT'


# Maven artefacts the release links. Licensee writes them as
# {"groupId": ..., "artifactId": ..., "version": ..., "spdxLicenses": [...]}.
maven = []
if os.path.exists(licensee_path):
    for artifact in json.load(open(licensee_path)):
        group = artifact.get('groupId') or ''
        name = artifact.get('artifactId') or ''
        ver = artifact.get('version') or 'UNKNOWN'
        if not name:
            continue
        licences = artifact.get('spdxLicenses') or []
        spdx = ', '.join(sorted({l.get('identifier') or l.get('name') or 'NOASSERTION'
                                 for l in licences})) or 'NOASSERTION'
        maven.append((group, name, ver, spdx))
else:
    print(f'sbom: warning: {licensee_path} is missing, so the SBOM lists no Maven '
          f'dependencies (run ./gradlew licenseeRelease first)', file=sys.stderr)

existing_ids = {p['SPDXID'] for p in packages}
added = 0
for group, name, ver, spdx in sorted(maven):
    spdx_id = 'SPDXRef-Package-maven-' + re.sub(
        r'[^A-Za-z0-9.-]', '-', f'{group}-{name}-{ver}')
    if spdx_id in existing_ids:
        continue
    existing_ids.add(spdx_id)
    purl = f'pkg:maven/{group}/{name}@{ver}' if group else f'pkg:maven/{name}@{ver}'
    packages.append({
        'SPDXID': spdx_id,
        'name': name,
        'versionInfo': ver,
        'downloadLocation': 'NOASSERTION',
        'filesAnalyzed': False,
        'licenseConcluded': 'NOASSERTION',
        'licenseDeclared': spdx,
        'copyrightText': 'NOASSERTION',
        'externalRefs': [{
            'referenceCategory': 'PACKAGE-MANAGER',
            'referenceType': 'purl',
            'referenceLocator': purl,
        }],
    })
    doc['relationships'].append({
        'spdxElementId': app_id,
        'relationshipType': 'DEPENDS_ON',
        'relatedSpdxElement': spdx_id,
    })
    added += 1

# Sorted output, so two runs over the same inputs produce the same bytes.
packages.sort(key=lambda p: p['SPDXID'])
doc['relationships'].sort(
    key=lambda r: (r['spdxElementId'], r['relationshipType'], r['relatedSpdxElement']))

with open(out, 'w') as handle:
    json.dump(doc, handle, indent=2, sort_keys=True)
    handle.write('\n')
print(f'sbom: wrote {out}: {len(packages)} packages '
      f'({added} Maven, {len(packages) - added} from syft)', file=sys.stderr)
PY

python3 - "$OUT" <<'PY'
import json
import sys

doc = json.load(open(sys.argv[1]))
for field in ('spdxVersion', 'SPDXID', 'name', 'dataLicense', 'creationInfo',
              'documentNamespace', 'packages', 'relationships'):
    if field not in doc:
        raise SystemExit(f'sbom: the document is missing {field}')
print(f'sbom: ok {doc["spdxVersion"]} {doc["name"]} '
      f'({len(doc["packages"])} packages, {len(doc["relationships"])} relationships)')
PY
