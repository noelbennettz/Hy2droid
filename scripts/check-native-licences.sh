#!/usr/bin/env bash
#
# Verify the checked-in record of the native components against what the app and
# the repository actually carry.
#
#   scripts/check-native-licences.sh
#
# For every row of native/LICENSES.tsv:
#   * the licence asset exists in app/src/main/assets/licenses/ and is not empty,
#   * the same text appears in NOTICE (whitespace-insensitive),
#   * the version source named in the row exists and declares a version, and that
#     version appears in NOTICE next to the component,
#   * ThirdPartyLicenses.kt points at the same asset, so the in-app screen and the
#     manifest cannot drift apart.
#
# Run in CI next to the other shell checks; no Gradle, no network.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

python3 - <<'PY'
import os
import re
import sys

MANIFEST = 'native/LICENSES.tsv'
NOTICE = 'NOTICE'
CODE = 'app/src/main/java/us/leaf3stones/hy2droid/data/ThirdPartyLicenses.kt'

problems = []


def fail(message):
    problems.append(message)


def normalise(text):
    """Collapse all whitespace, so re-wrapped licence texts still compare equal."""
    return ' '.join(text.split())


notice = normalise(open(NOTICE, encoding='utf-8').read())
code = open(CODE, encoding='utf-8').read()

rows = []
for lineno, line in enumerate(open(MANIFEST, encoding='utf-8'), 1):
    line = line.rstrip('\n')
    if not line.strip() or line.startswith('#'):
        continue
    fields = line.split('\t')
    if len(fields) != 5:
        fail(f'{MANIFEST}:{lineno}: expected 5 tab-separated fields, found {len(fields)}')
        continue
    rows.append(fields)

if not rows:
    fail(f'{MANIFEST}: no components listed')

for component, version_source, asset, upstream, spdx in rows:
    asset_path = os.path.join('app/src/main/assets', asset)
    if not os.path.exists(asset_path):
        fail(f'{component}: missing licence asset {asset_path}')
        continue
    text = open(asset_path, encoding='utf-8').read()
    if not text.strip():
        fail(f'{component}: {asset_path} is empty')
        continue
    if normalise(text) not in notice:
        fail(f'{component}: the text of {asset_path} does not appear verbatim in {NOTICE}')
    if not text.lower().startswith(('copyright', 'permission')) and 'Copyright' not in text:
        fail(f'{component}: {asset_path} does not look like a licence text')

    if os.path.basename(asset) not in code:
        fail(f'{component}: ThirdPartyLicenses.kt does not reference {os.path.basename(asset)}')

    if version_source == 'binary':
        continue
    if ':' not in version_source:
        fail(f'{component}: version source "{version_source}" is not <file>:<KEY>')
        continue
    path, key = version_source.split(':', 1)
    if not os.path.exists(path):
        fail(f'{component}: version source {path} does not exist')
        continue
    match = re.search(rf'^{re.escape(key)}="?([^"\n]+)"?$', open(path, encoding='utf-8').read(), re.M)
    if not match:
        fail(f'{component}: {path} has no {key}= line')
        continue
    version = match.group(1)
    if version not in notice:
        fail(f'{component}: {NOTICE} does not mention version {version} (from {path})')
    if spdx not in notice:
        fail(f'{component}: {NOTICE} does not name the {spdx} licence')

if problems:
    for problem in problems:
        print(f'licences: {problem}', file=sys.stderr)
    print(f'native licence check FAILED ({len(problems)} problem(s))', file=sys.stderr)
    sys.exit(1)

print(f'native licence check ok ({len(rows)} components: '
      f'{", ".join(row[0] for row in rows)})')
PY
