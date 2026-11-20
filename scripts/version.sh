#!/usr/bin/env bash
#
# Single source of truth for the app's versionName and versionCode.
#
#   scripts/version.sh name            versionName for this checkout
#   scripts/version.sh code [name]     versionCode for that name (default: `name`)
#   scripts/version.sh newest-tag      newest v* tag, default: from origin
#   scripts/version.sh print           name and code, one `key=value` per line
#   scripts/version.sh release <name>  validate a stable release name
#
# `app/build.gradle` calls this from `defaultConfig`, so a local build and a CI
# release derive the version from the same inputs. `-PversionName` /
# `HY2_VERSION_NAME` (and the code equivalents) still win, which is what a
# workflow_dispatch release needs: there is no tag to read.
#
# Rules
#   versionName  the exact `v*` tag on HEAD, else the nearest `v*` tag plus
#                `-dev.<commits-since-tag>+g<short-sha>`, else `0.0.0-dev`.
#   versionCode  major*1_000_000 + minor*1_000 + patch, taken from the version
#                name's semantic prefix (`0.9.0-dev.5+…` -> 9000). It is a pure
#                function of the name, so rebuilding the same commit cannot
#                produce a different, non-upgradeable code. The largest value
#                Google Play accepts is 2_100_000_000, which caps the major at
#                2100 and the minor/patch at 999.
#                A checkout with no tags at all (`0.0.0-dev`) derives 0, which
#                Android rejects as a versionCode, so it is clamped to 1. Those
#                builds are for CI and local use only; nothing is published from
#                them.
#   release      a published version has to be exactly `<major>.<minor>.<patch>`.
#                `-dev`, `-rc1`, `+g<sha>` and similar suffixes are fine for local
#                and CI builds (nothing is published from them), but two suffixed
#                names can collide with an already-published code: `1.0.0-rc1` and
#                `1.0.0` both derive 1_000_000, and Play refuses the second upload
#                as a duplicate. The release workflow therefore runs
#                `version.sh release`, which fails loudly on any suffix.
#
# Nothing here reads the clock or the workflow run number.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

die() { echo "version: $*" >&2; exit 1; }

have_git() { git rev-parse --git-dir > /dev/null 2>&1; }

# Semantic order, oldest to newest: `sort -V` puts 0.9.0 before 1.0.0, which a
# plain string sort gets wrong.
sort_versions() { sort -V; }

newest_tag() {
  local source="${1:-origin}"
  local tags=""
  if have_git; then
    if [ "$source" = "origin" ]; then
      # The release a user can download is the pushed tag, not a local one, so
      # an unpushed tag never becomes "the newest release" here.
      tags="$(git ls-remote --tags --refs origin 'v*' 2>/dev/null \
                | sed -n 's#.*refs/tags/##p' || true)"
    fi
    if [ -z "$tags" ]; then
      tags="$(git tag --list 'v*')"
    fi
  fi
  [ -n "$tags" ] || return 1
  printf '%s\n' "$tags" | grep -E '^v[0-9]+\.[0-9]+\.[0-9]+$' | sort_versions | tail -n1
}

version_name() {
  if ! have_git; then
    printf '0.0.0-dev\n'
    return 0
  fi
  local exact
  exact="$(git describe --tags --exact-match --match 'v[0-9]*' 2>/dev/null || true)"
  if [ -n "$exact" ]; then
    printf '%s\n' "${exact#v}"
    return 0
  fi
  local nearest
  nearest="$(git describe --tags --abbrev=0 --match 'v[0-9]*' 2>/dev/null || true)"
  if [ -z "$nearest" ]; then
    printf '0.0.0-dev\n'
    return 0
  fi
  local commits sha
  commits="$(git rev-list --count "$nearest..HEAD" 2>/dev/null || echo 0)"
  sha="$(git rev-parse --short HEAD 2>/dev/null || echo unknown)"
  printf '%s-dev.%s+g%s\n' "${nearest#v}" "$commits" "$sha"
}

version_code() {
  local name="${1:-$(version_name)}"
  local core="${name%%[-+]*}"
  if ! [[ "$core" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
    die "cannot derive a versionCode from '$name' (expected <major>.<minor>.<patch>[-…])"
  fi
  local major="${BASH_REMATCH[1]}" minor="${BASH_REMATCH[2]}" patch="${BASH_REMATCH[3]}"
  if (( major > 2100 || minor > 999 || patch > 999 )); then
    die "'$name' does not fit the versionCode rule (major <= 2100, minor/patch <= 999)"
  fi
  local code=$(( major * 1000000 + minor * 1000 + patch ))
  # A shallow CI clone has no tags, so the name is `0.0.0-dev` and the rule gives
  # 0 — which AGP rejects ("versionCode is set to 0, but it should be a positive
  # integer"). Nothing with that name is ever published, so one is enough.
  (( code > 0 )) || code=1
  printf '%s\n' "$code"
}

release_name() {
  local name="${1:-}"
  if ! [[ "$name" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    die "'${name:-<empty>}' is not a stable release version — expected" \
        "<major>.<minor>.<patch> with no suffix (a -rc/-dev/+build name shares" \
        "its versionCode with the final release, so Play refuses the upload)"
  fi
  version_code "$name" > /dev/null
  printf '%s\n' "$name"
}

case "${1:-}" in
  name)       version_name ;;
  code)       version_code "${2:-}" ;;
  newest-tag) newest_tag "${2:-origin}" ;;
  release)    release_name "${2:-}" ;;
  print)
    name="$(version_name)"
    printf 'name=%s\ncode=%s\n' "$name" "$(version_code "$name")"
    ;;
  *)
    sed -n '3,9p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
    exit 2
    ;;
esac
