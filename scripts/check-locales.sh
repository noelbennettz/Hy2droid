#!/usr/bin/env bash
#
# Compare the complete key set of every `values*/strings.xml` against the
# default `values/strings.xml`.
#
#   scripts/check-locales.sh
#
# Fails on:
#   * a key that exists in the default file but not in a translation
#     (an untranslated string shows English, which is intended only for keys
#     marked translatable="false"),
#   * a key in a translation that the default file does not have (usually a
#     typo or a string that was renamed in one place only),
#   * a duplicate key inside one file (aapt2 silently keeps the last one).
#
# Successful output names the locales and the number of keys, so a locale that
# silently fell out of the build is visible too.
#
# No dependencies beyond python3; CI runs it next to check-style.sh.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

python3 - <<'PY'
import glob
import sys
import xml.etree.ElementTree as ET

BASE = 'app/src/main/res/values/strings.xml'
ELEMENTS = ('string', 'plurals', 'string-array')


def load(path):
    """Return {name: (tag, translatable)} and the list of duplicate names."""
    root = ET.parse(path).getroot()
    keys, duplicates = {}, []
    for element in root:
        if element.tag not in ELEMENTS:
            continue
        name = element.get('name')
        if name in keys:
            duplicates.append(name)
        keys[name] = (element.tag, element.get('translatable') == 'false')
    return keys, duplicates


base, base_dupes = load(BASE)
required = {name for name, (_, untranslated) in base.items() if not untranslated}

problems = []


def report(path, message):
    problems.append(f'{path}: {message}')


for name in base_dupes:
    report(BASE, f'duplicate key "{name}"')

locales = sorted(glob.glob('app/src/main/res/values-*/strings.xml'))
if not locales:
    report(BASE, 'no translations found (values-*/strings.xml)')

for path in locales:
    keys, duplicates = load(path)
    for name in duplicates:
        report(path, f'duplicate key "{name}"')
    for name in sorted(required - set(keys)):
        report(path, f'missing key "{name}"')
    for name in sorted(set(keys) - set(base)):
        report(path, f'unknown key "{name}" (not in {BASE})')
    for name in sorted(set(keys) & set(base)):
        if keys[name][0] != base[name][0]:
            report(path, f'key "{name}" is a <{keys[name][0]}> here '
                         f'but a <{base[name][0]}> in {BASE}')

if problems:
    for problem in problems:
        print(f'locales: {problem}', file=sys.stderr)
    print(f'locale parity FAILED ({len(problems)} problem(s)); '
          f'every values-*/strings.xml needs the same keys as {BASE}.',
          file=sys.stderr)
    sys.exit(1)

print(f'locale parity ok: {len(required)} keys in {len(locales)} translations '
      f'({", ".join(p.split("/")[4] for p in locales)})')
PY
