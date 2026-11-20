#!/usr/bin/env bash
#
# Check that the documentation still matches the repository.
#
#   scripts/check-docs.sh
#
# Fails when:
#   * a relative link in a Markdown file points at a path that does not exist,
#   * a `#anchor` in a Markdown link names no heading in the target file,
#   * a script in `scripts/` or a workflow in `.github/workflows/` is not
#     mentioned in DOCS.md, CONTRIBUTING.md or README.md,
#   * the table of contents at the top of DOCS.md lists an anchor that no heading
#     produces, or a top-level heading is missing from it.
#
# External URLs are not fetched: this is the check that can run offline, next to
# scripts/check-style.sh and scripts/check-version-consistency.sh.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

python3 - <<'PY'
import glob
import os
import re
import sys

MARKDOWN = ['README.md', 'DOCS.md', 'CONTRIBUTING.md', 'SECURITY.md',
            'native/tun2socks/README.md']
DESCRIBED_IN = ['DOCS.md', 'CONTRIBUTING.md', 'README.md']

problems = []


def fail(message):
    problems.append(message)


def slug(heading):
    """GitHub's heading anchor: lowercase, drop punctuation, spaces to '-'."""
    text = heading.strip().lower()
    text = re.sub(r'`', '', text)
    text = ''.join(ch if (ch.isalnum() or ch in ' -_') else '' for ch in text)
    return text.replace(' ', '-')


def anchors(path):
    found = set()
    for line in open(path, encoding='utf-8'):
        match = re.match(r'^(#{1,6})\s+(.*?)\s*$', line)
        if match:
            found.add(slug(match.group(2)))
    return found


LINK = re.compile(r'\[[^\]]*\]\(([^)\s]+)(?:\s+"[^"]*")?\)')

files = [path for path in MARKDOWN if os.path.exists(path)]
for path in glob.glob('*.md'):
    if path not in files:
        files.append(path)

anchor_cache = {}


def anchors_of(path):
    if path not in anchor_cache:
        anchor_cache[path] = anchors(path)
    return anchor_cache[path]


for path in files:
    for target in LINK.findall(open(path, encoding='utf-8').read()):
        if re.match(r'^[a-z][a-z0-9+.-]*:', target) or target.startswith('//'):
            continue  # http(s):/mailto:/… — not checked offline
        file_part, _, anchor = target.partition('#')
        if file_part:
            resolved = os.path.normpath(os.path.join(os.path.dirname(path), file_part))
            if not os.path.exists(resolved):
                fail(f'{path}: link to "{target}" — {resolved} does not exist')
                continue
        else:
            resolved = path
        if anchor and resolved.endswith('.md'):
            if anchor not in anchors_of(resolved):
                fail(f'{path}: link to "{target}" — no heading in {resolved} '
                     f'produces the anchor "{anchor}"')

# Every script and workflow has to be mentioned in the documentation.
described = '\n'.join(open(p, encoding='utf-8').read() for p in DESCRIBED_IN)
for path in sorted(glob.glob('scripts/*.sh')):
    if os.path.basename(path) not in described:
        fail(f'{path} is not mentioned in {" or ".join(DESCRIBED_IN)}')
for path in sorted(glob.glob('.github/workflows/*.yml')):
    if os.path.basename(path) not in described:
        fail(f'{path} is not mentioned in {" or ".join(DESCRIBED_IN)}')

# DOCS' table of contents and its headings must agree.
docs = open('DOCS.md', encoding='utf-8').read()
toc_block = re.search(r'^## Contents\n(.*?)(?=^## )', docs, re.S | re.M)
if not toc_block:
    fail('DOCS.md has no "## Contents" section')
else:
    listed = set(re.findall(r'^- \[[^\]]+\]\(#([^)]+)\)', toc_block.group(1), re.M))
    headings = {slug(h) for h in re.findall(r'^## (.+)$', docs, re.M)}
    for anchor in sorted(listed - headings):
        fail(f'DOCS.md: the contents list points at "#{anchor}", which no ## heading produces')
    for heading in sorted(headings - listed):
        if heading in ('contents',):
            continue
        fail(f'DOCS.md: the ## heading "{heading}" is missing from the contents list')

if problems:
    for problem in problems:
        print(f'docs: {problem}', file=sys.stderr)
    print(f'docs consistency FAILED ({len(problems)} problem(s))', file=sys.stderr)
    sys.exit(1)

print(f'docs consistency ok ({len(files)} files)')
PY
