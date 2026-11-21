#!/usr/bin/env bash

set -euo pipefail

commit_sha="${1:?Expected the commit SHA to check}"
check_name="${2:-Test}"
# Give BuildBuddy its one-hour execution window plus time to queue and report.
deadline=$((SECONDS + 70 * 60))

while (( SECONDS < deadline )); do
  state="$(gh api "repos/$GITHUB_REPOSITORY/commits/$commit_sha/status" |
    jq -r --arg check "$check_name" '[.statuses[] | select(.context == $check)][0].state // "missing"')"

  case "$state" in
    success)
      echo "BuildBuddy $check_name succeeded for $commit_sha."
      exit 0
      ;;
    missing | pending)
      sleep 30
      ;;
    *)
      echo "BuildBuddy $check_name finished with state $state for $commit_sha." >&2
      exit 1
      ;;
  esac
done

echo "Timed out waiting for BuildBuddy $check_name on $commit_sha." >&2
exit 1
