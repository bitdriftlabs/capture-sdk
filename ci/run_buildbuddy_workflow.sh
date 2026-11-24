#!/usr/bin/env bash

set -euo pipefail

action="${1:?Expected the BuildBuddy action name}"
branch="${2:?Expected branch}"
commit="${3:?Expected commit SHA}"
: "${BUILDBUDDY_API_KEY:?Expected BuildBuddy API key}"
: "${GITHUB_REPOSITORY:?Expected GitHub repository}"

# shellcheck source=ci/buildbuddy_api.sh
source "$(dirname "${BASH_SOURCE[0]}")/buildbuddy_api.sh"

request="$(jq -nc --arg repo "https://github.com/$GITHUB_REPOSITORY" --arg branch "$branch" \
  --arg commit "$commit" --arg action "$action" '
    {repo_url: $repo, branch: $branch, commit_sha: $commit, action_names: [$action], async: true}
  ')"
# A retry of ExecuteWorkflow could start a second build if the response was lost.
invocation="$(buildbuddy_api ExecuteWorkflow --data "$request" | jq -er --arg action "$action" '
  .actionStatuses[] | select(.actionName == $action) |
  if (.status.code // 0) != 0 then error(.status.message)
  else .invocationId | select(length > 0) end
')"
echo "Started $action at https://bitdrift.buildbuddy.io/invocation/$invocation" >&2

request="$(jq -nc --arg id "$invocation" '{selector:{invocation_id:$id},include_artifacts:true}')"
deadline=$((SECONDS + 70 * 60))
while (( SECONDS < deadline )); do
  if result="$(buildbuddy_api GetInvocation --max-time 20 --data "$request")"; then
    state="$(jq -r '.invocation[0].invocationStatus // empty' <<< "$result")"
    case "$state" in
      COMPLETE_INVOCATION_STATUS)
        if jq -e --arg commit "$commit" '.invocation[0] | .success == true and .commitSha == $commit' <<< "$result" > /dev/null; then
          jq -e '.invocation[0].artifacts | length > 0' <<< "$result" > /dev/null && {
            printf '%s\n' "$invocation"
            exit 0
          }
        else
          echo "$action failed or built a different commit. Invocation $invocation." >&2
          exit 1
        fi
        ;;
      DISCONNECTED_INVOCATION_STATUS)
        echo "$action disconnected. Invocation $invocation." >&2
        exit 1
        ;;
    esac
  fi
  sleep 30
done

echo "Timed out waiting for artifacts from $action. Invocation $invocation." >&2
exit 1
