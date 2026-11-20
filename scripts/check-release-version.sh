#!/usr/bin/env bash
#
# Regression test for the release version guard.
#
#   scripts/check-release-version.sh
#
# `1.0.0-rc1` and `1.0.0` derive the same versionCode (1_000_000), so a
# pre-release tag that slipped past `release.yml` would publish a build Play then
# refuses as a duplicate. This asserts that:
#   * `scripts/version.sh release` accepts a stable `<major>.<minor>.<patch>`,
#   * it rejects a `-rc`, `-dev`, `+build` or otherwise non-stable name,
#   * `version.sh code` still accepts `-dev` (local and CI builds must keep
#     working), and
#   * `release.yml` actually calls the guard before it builds or publishes.
#
# Docs-only gate: no Gradle, no Android SDK, ~1 s.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

fail=0
ok() { echo "ok  $*"; }
bad() { echo "release-version: $*" >&2; fail=1; }

accepts() {
  local got
  if ! got="$(bash scripts/version.sh release "$1" 2>/dev/null)"; then
    bad "release '$1' was rejected"
  elif [ "$got" != "$1" ]; then
    bad "release '$1' printed '$got'"
  else
    ok "release accepts $1"
  fi
}

rejects() {
  if bash scripts/version.sh release "$1" >/dev/null 2>&1; then
    bad "release '$1' was accepted"
  else
    ok "release rejects ${1:-<empty>}"
  fi
}

# Stable names, including the edges of the versionCode rule.
for name in 0.0.1 1.0.0 2.1.0 2100.999.999; do
  accepts "$name"
done

# Local/CI names keep building; only publishing is restricted.
for name in 0.9.0-dev.11+g1f5e2fa 0.0.0-dev; do
  if [ "$(bash scripts/version.sh code "$name")" = "" ]; then
    bad "code '$name' produced nothing"
  else
    ok "code still accepts $name"
  fi
done

# The collision that motivated the guard.
if [ "$(bash scripts/version.sh code 1.0.0)" != "$(bash scripts/version.sh code 1.0.0-rc1)" ]; then
  bad "1.0.0 and 1.0.0-rc1 no longer share a versionCode; revisit the guard"
else
  ok "1.0.0 and 1.0.0-rc1 share a versionCode"
fi

# Prefixes, suffixes and malformed shapes.
for name in 1.0.0-rc1 1.0.0-rc.1 1.0.0-dev 1.0.0+build 1.0.0-rc1+build v1.0.0 1.0 1.0.0.0 '' 1.0.0.0-rc1; do
  rejects "$name"
done
# Out of range for versionCode, so also not publishable.
rejects 2101.0.0
rejects 1.1000.0

# The release workflow has to run the guard before anything is built.
guard_line="$(grep -n 'version.sh release' .github/workflows/release.yml | head -n1 | cut -d: -f1)"
build_line="$(grep -n 'scripts/core.sh fetch --pinned' .github/workflows/release.yml | head -n1 | cut -d: -f1)"
if [ -n "$guard_line" ]; then
  ok "release.yml runs the guard"
else
  bad "release.yml does not call 'scripts/version.sh release'"
fi
if [ -n "$guard_line" ] && [ -n "$build_line" ] && [ "$guard_line" -lt "$build_line" ]; then
  ok "the guard runs before the build"
else
  bad "the guard runs after the build"
fi

if [ "$fail" -ne 0 ]; then
  echo "release version guard FAILED" >&2
  exit 1
fi
echo "release version guard passed"
