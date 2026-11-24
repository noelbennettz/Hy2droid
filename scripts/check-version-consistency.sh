#!/usr/bin/env bash
#
# Check that the documentation stays version-neutral and agrees with the tree.
#
#   scripts/check-version-consistency.sh            (CI: publishes nothing)
#   scripts/check-version-consistency.sh --local    resolve versioned links
#                                                 against the local tags only
#
# Checked:
#   * README.md and DOCS.md must not pin the tree to one release ("Applies to
#     release `vX.Y.Z`"). They describe `main`; a release keeps the
#     documentation it shipped with at its tag, so a pin is stale by the next
#     release.
#   * every `versions/vX.Y.Z/` path in those files belongs to a real tag.
#   * the Hysteria core badge in README.md matches `core/core.properties`.
#
# Docs-only gate: no Gradle, no Android SDK, ~1 s.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

source="origin"
if [ "${1:-}" = "--local" ]; then source="local"; fi

fail=0
note() { echo "version-consistency: $*" >&2; fail=1; }

# 1. A header that names the release it applies to is wrong as soon as the next
#    release exists; the tree itself describes `main`. The backticks are part of
#    the pattern, not shell quoting.
# shellcheck disable=SC2016
pins="$(grep -hoE 'Applies to (release )?`v[0-9]+\.[0-9]+\.[0-9]+`' README.md DOCS.md \
          | sort -u || true)"
if [ -n "$pins" ]; then
  note "README.md or DOCS.md pins the tree to a release ($(printf '%s' "$pins" | tr '\n' ' ')); describe the current tree instead"
else
  echo "ok  README/DOCS are version-neutral"
fi

# 2. The core badge is a version line too.
core_pin="$(sed -n 's/^HYSTERIA_VERSION=//p' core/core.properties | head -n1)"
badge="$(grep -oE 'Hysteria%20core-[0-9]+\.[0-9]+\.[0-9]+' README.md | head -n1 | sed 's/.*core-//')"
if [ -n "$core_pin" ] && [ -n "$badge" ]; then
  if [ "$badge" != "${core_pin#v}" ]; then
    note "README badge says core $badge, core/core.properties pins $core_pin"
  else
    echo "ok  core badge ${core_pin}"
  fi
fi

# 3. A versioned documentation link, if any, has to point at a tag that exists.
links="$(grep -hoE 'versions/v[0-9]+\.[0-9]+\.[0-9]+/' README.md DOCS.md \
          | sed 's#/$##' | sort -u || true)"
if [ -z "$links" ]; then
  echo "ok  no versioned documentation links"
elif ! git rev-parse --git-dir > /dev/null 2>&1; then
  echo "version-consistency: not a git checkout; cannot verify the versioned links, skipping" >&2
else
  tags=""
  if [ "$source" = "origin" ]; then
    tags="$(git ls-remote --tags --refs origin 'v*' 2>/dev/null | sed -n 's#.*refs/tags/##p' || true)"
  fi
  [ -n "$tags" ] || tags="$(git tag --list 'v*')"
  for linked in $links; do
    tag="${linked#versions/}"
    if printf '%s\n' "$tags" | grep -qx "$tag"; then
      echo "ok  link $tag"
    else
      note "docs link to $tag but no such tag exists"
    fi
  done
fi

if [ "$fail" -ne 0 ]; then
  echo "version consistency FAILED — update README.md/DOCS.md or push the tag." >&2
  exit 1
fi
echo "version consistency passed"
