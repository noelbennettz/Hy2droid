#!/usr/bin/env bash
# Check the DCO sign-off on every commit in a range.
#
# Every non-merge commit must carry a `Signed-off-by: Name <email>` trailer whose
# address matches the commit author, which is what the Developer Certificate of
# Origin 1.1 asks for (https://developercertificate.org/). Commits whose author
# is a bot (`something[bot]`) are skipped: bots cannot sign anything, and their
# output is reviewed by the human who merges it.
#
# No GitHub App is involved; .github/workflows/dco.yml runs this script on every
# pull request. Exit status is 0 when every commit passes.
#
# Usage: check-dco.sh <base-sha> <head-sha>
set -euo pipefail

base="${1:-}"
head="${2:-}"
if [ -z "$base" ] || [ -z "$head" ]; then
  echo "usage: $0 <base-sha> <head-sha>" >&2
  exit 2
fi

if ! git cat-file -e "${head}^{commit}" 2>/dev/null; then
  echo "::error::unknown commit $head" >&2
  exit 1
fi

mapfile -t commits < <(git rev-list --no-merges "$base..$head")
if [ "${#commits[@]}" -eq 0 ]; then
  echo "No commits between $base and $head; nothing to check."
  exit 0
fi

failed=0
checked=0
for sha in "${commits[@]}"; do
  author_name="$(git log -1 --format=%an "$sha")"
  author_email="$(git log -1 --format=%ae "$sha")"
  subject="$(git log -1 --format=%s "$sha")"

  case "$author_name" in
    *'[bot]')
      echo "skip   ${sha:0:12} $subject (bot: $author_name)"
      continue
      ;;
  esac

  checked=$((checked + 1))
  # One sign-off per line; the address is what has to match the author.
  emails="$(git log -1 --format=%B "$sha" | grep -iE '^Signed-off-by: .+ <[^<>]+>$' \
    | sed -E 's/.*<([^<>]+)>.*/\1/' || true)"
  if [ -z "$emails" ]; then
    echo "::error::${sha:0:12} $subject has no Signed-off-by line (use: git commit -s)"
    failed=$((failed + 1))
    continue
  fi
  if ! printf '%s\n' "$emails" | tr '[:upper:]' '[:lower:]' \
      | grep -qxF "$(printf '%s' "$author_email" | tr '[:upper:]' '[:lower:]')"; then
    echo "::error::${sha:0:12} $subject is signed off by ${emails//$'\n'/, } but authored by <$author_email>"
    failed=$((failed + 1))
    continue
  fi
  echo "ok     ${sha:0:12} $subject"
done

if [ "$failed" -ne 0 ]; then
  echo "::error::$failed of $checked commit(s) failed the DCO check; see CONTRIBUTING.md"
  exit 1
fi
echo "All $checked commit(s) are signed off."
