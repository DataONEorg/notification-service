#!/bin/zsh
#!/usr/bin/env zsh
# Usage:
#   ./notify.sh http://localhost:8080
#
# Requirements:
# - TOKEN environment variable must be set to a valid JWT
# - jq must be installed

set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <base-url>"
  echo "Example: $0 http://localhost:8080"
  echo
  BASE_URL="http://localhost:8080"
  echo "No base url provided; defaulting to ${BASE_URL}"
else
  BASE_URL="${1%/}"
fi

if [[ -z "${TOKEN:-}" ]]; then
  echo
  echo "ERROR: TOKEN environment variable is not set." >&2
  exit 1
fi

if ! command -v jq >/dev/null 2>&1; then
  echo
  echo "ERROR: jq is required but not installed." >&2
  exit 1
fi

make_pid() {
  local n="$1"
  printf "urn:uuid:this-is-test-pid-%02d" "$n"
}

echo
echo "Creating 10 test subscriptions..."
for i in {1..10}; do
  pid=$(make_pid "$i")
  echo "POST ${pid}"
  curl --silent --show-error --fail \
    --request POST "${BASE_URL}/notifications/datasets/${pid}" \
    --header "Authorization: Bearer $TOKEN" | jq .
done

echo
echo "Verifying all 10 PIDs are present..."
list_json="$(curl --silent --show-error --fail --request GET "${BASE_URL}/notifications/datasets" \
  --header "Authorization: Bearer $TOKEN")"
echo "$list_json" | jq .

# Check each expected pid is present
for i in {1..10}; do
  pid=$(make_pid "$i")
  echo "Checking presence: ${pid}"
  echo "$list_json" | jq -e --arg pid "$pid" 'index($pid) != null' >/dev/null
done
echo "All PIDs verified."

echo
echo "Deleting 10 test subscriptions..."
for i in {1..10}; do
  pid=$(make_pid "$i")
  echo "DELETE ${pid}"
  curl --silent --show-error --fail \
    --request DELETE "${BASE_URL}/notifications/datasets/${pid}" \
    --header "Authorization: Bearer $TOKEN" | jq .
done

echo
echo "Verifying list is empty..."
final_json="$(curl --silent --show-error --fail \
  --request GET "${BASE_URL}/notifications/datasets" \
  --header "Authorization: Bearer $TOKEN")"
echo "$final_json" | jq .
echo "$final_json" | jq -e 'type=="array" and length==0' >/dev/null
echo "Empty array verified. Done."
