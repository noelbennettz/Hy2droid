#!/usr/bin/env bash
# Write SHA256SUMS.txt for a directory of release APKs and check their signer.
#
# Output (in the given directory, sha256sum -c compatible):
#   <sha256>  <name>.apk
# and, on stdout only, one line:
#   Signing certificate SHA-256: <hex>
#
# The release is only trustworthy if every APK is signed by the same
# certificate, so the script fails (non-zero) when two APKs disagree, when an APK
# is unsigned, or when apksigner cannot verify one. `apksigner` comes from
# $ANDROID_HOME/build-tools/*/apksigner (newest), or from PATH.
#
# Usage: release-verify.sh <directory>      (default: app/build/outputs/apk/release)
set -euo pipefail

dir="${1:-app/build/outputs/apk/release}"

die() {
  echo "::error::$*" >&2
  exit 1
}

[ -d "$dir" ] || die "no such directory: $dir"

shopt -s nullglob
apks=("$dir"/*.apk)
shopt -u nullglob
[ "${#apks[@]}" -gt 0 ] || die "no APKs in $dir"

find_apksigner() {
  if [ -n "${APKSIGNER:-}" ]; then
    printf '%s\n' "$APKSIGNER"
    return
  fi
  if [ -n "${ANDROID_HOME:-}" ]; then
    local candidate
    candidate="$(find "$ANDROID_HOME/build-tools" -maxdepth 2 -name apksigner -type f \
      2>/dev/null | sort -V | tail -n1)"
    if [ -n "$candidate" ]; then
      printf '%s\n' "$candidate"
      return
    fi
  fi
  command -v apksigner || true
}

apksigner="$(find_apksigner)"
[ -n "$apksigner" ] || die "apksigner not found; set ANDROID_HOME or APKSIGNER"

# sha256sum's own format: "<digest><space><space><name>", which
#   sha256sum -c SHA256SUMS.txt
# reads back. Sort by name so the file is stable between runs.
sums="$dir/SHA256SUMS.txt"
: > "$sums"
while IFS= read -r apk; do
  digest="$(sha256sum "$apk" | cut -d' ' -f1)"
  printf '%s  %s\n' "$digest" "$(basename "$apk")" >> "$sums"
done < <(printf '%s\n' "${apks[@]}" | sort)
echo "Wrote $sums (${#apks[@]} APK(s))" >&2
cat "$sums" >&2

# `--print-certs` prints one "Signer #N certificate SHA-256 digest: <hex>" line
# per signer; anything other than exactly one distinct digest means the APKs
# were not all signed with the same key.
certs=()
for apk in "${apks[@]}"; do
  out="$("$apksigner" verify --print-certs "$apk" 2>&1)" || {
    echo "$out" >&2
    die "apksigner could not verify $(basename "$apk") (unsigned or malformed)"
  }
  # Newer apksigner prints "Signer #1 certificate SHA-256 digest: ..."; older
  # builds use "... certificate SHA-256 digest:" with the same wording, so this
  # pattern matches both. Only the first signer is considered: an APK with a
  # second signer is a different distribution model.
  one="$(printf '%s\n' "$out" \
    | sed -n 's/^Signer #1 certificate SHA-256 digest: *//p' | head -n1)"
  [ -n "$one" ] || die "no certificate digest in apksigner output for $(basename "$apk")"
  certs+=("$one")
  echo "$(basename "$apk"): $one" >&2
done

first="${certs[0]}"
for cert in "${certs[@]:1}"; do
  if [ "$cert" != "$first" ]; then
    die "signing certificates differ between APKs ($first vs $cert)"
  fi
done

# stdout is machine-readable: the release workflow appends this to the notes and
# users compare it with `apksigner verify --print-certs` on their own download.
echo "Signing certificate SHA-256: $first"
