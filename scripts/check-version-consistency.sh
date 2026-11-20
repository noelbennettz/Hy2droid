#!/usr/bin/env bash
#
# Check that the documentation and core pin agree with the newest release.
#
#   scripts/check-version-consistency.sh            (CI: publishes nothing)
#   scripts/check-version-consistency.sh --local    use the local tags only
#
# Checked:
#   * every "Applies to release `vX.Y.Z`" line in README.md and DOCS.md names the
#     newest *pushed* `v*` tag. A local tag that was never pushed is not a
#     release, so it is ignored by default.
#   * every `versions/vX.Y.Z/` path in those files belongs to a real tag.
#   * the Hysteria core badge in README.md matches `core/core.properties`.
#   * when origin has no `v*` tag at all, the version checks are skipped with a
#     warning: there is no published release to compare the documents against.
#     (Deleting a release is a deliberate state; only an unreachable remote falls
#     back to local tags instead.)
#
# Docs-only gate: no Gradle, no Android SDK, ~1 s.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

source="origin"
if [ "${1:-}" = "--local" ]; then source="local"; fi

fail=0
note() { echo "version-consistency: $*" >&2; fail=1; }

newest="$(bash scripts/version.sh newest-tag "$source" || true)"
if [ -z "$newest" ]; then
  # A repository with no published release has nothing to compare against. That
  # is a deliberate state (tags and releases can be removed), not a docs error;
  # warn and keep the build usable.
  echo "version-consistency: no v* tag in $source; nothing is published to compare against, skipping" >&2
  exit 0
fi
echo "newest published tag: $newest (source: $source)"

# 1. The release each document claims to describe.
claims="$(grep -hoE 'Applies to release `v[0-9]+\.[0-9]+\.[0-9]+`' README.md DOCS.md \
          | sed -E 's/.*`(v[0-9]+\.[0-9]+\.[0-9]+)`.*/\1/' | sort -u || true)"
if [ -z "$claims" ]; then
  note "no 'Applies to release' line found in README.md or DOCS.md"
fi
for claim in $claims; do
  if [ "$claim" != "$newest" ]; then
    note "docs claim $claim but the newest published tag is $newest"
  else
    echo "ok  docs claim $claim"
  fi
done

# 2. Versioned documentation links have to point at a tag that exists.
tags="$newest"
if git rev-parse --git-dir > /dev/null 2>&1; then
  tags="$(git ls-remote --tags --refs origin 'v*' 2>/dev/null | sed -n 's#.*refs/tags/##p')"
  [ -n "$tags" ] || tags="$(git tag --list 'v*')"
fi
for linked in $(grep -hoE 'versions/v[0-9]+\.[0-9]+\.[0-9]+/' README.md DOCS.md \
                | sed 's#/$##' | sort -u || true); do
  tag="${linked#versions/}"
  if printf '%s\n' "$tags" | grep -qx "$tag"; then
    echo "ok  link $tag"
  else
    note "docs link to $tag but no such tag exists"
  fi
done

# 3. The core badge is a version line too.
core_pin="$(sed -n 's/^HYSTERIA_VERSION=//p' core/core.properties | head -n1)"
badge="$(grep -oE 'Hysteria%20core-[0-9]+\.[0-9]+\.[0-9]+' README.md | head -n1 | sed 's/.*core-//')"
if [ -n "$core_pin" ] && [ -n "$badge" ]; then
  if [ "$badge" != "${core_pin#v}" ]; then
    note "README badge says core $badge, core/core.properties pins $core_pin"
  else
    echo "ok  core badge ${core_pin}"
  fi
fi

if [ "$fail" -ne 0 ]; then
  echo "version consistency FAILED — update README.md/DOCS.md or push the tag." >&2
  exit 1
fi
echo "version consistency passed"
