#!/usr/bin/env bash
# Shared helpers for eval scripts. Requires: bash, curl, a running stack (docker compose up).
set -uo pipefail

GW=${GW:-http://localhost:8080}
PASS=0
FAIL=0
AUTH=()
[ -n "${TOKEN:-}" ] && AUTH=(-H "Authorization: Bearer $TOKEN")

# check <id> <description> <expected> <actual>
check() {
  if [ "$3" = "$4" ]; then
    PASS=$((PASS + 1)); printf 'PASS %-4s %s\n' "$1" "$2"
  else
    FAIL=$((FAIL + 1)); printf 'FAIL %-4s %s (expected %s, got %s)\n' "$1" "$2" "$3" "$4"
  fi
}

# http <method> <path> [json-body] -> sets CODE and BODY
http() {
  local out
  out=$(curl -s -w '\n%{http_code}' -X "$1" "${AUTH[@]}" -H 'Content-Type: application/json' \
    ${3:+-d "$3"} "$GW$2")
  CODE=${out##*$'\n'}
  BODY=${out%$'\n'*}
}

# json_field <field>: first string/number value of "field" in $BODY (flat lookup, good enough for evals)
json_field() {
  printf '%s' "$BODY" | grep -o "\"$1\":\"\?[^\",}]*" | head -1 | sed -E "s/\"$1\":\"?//"
}

# wait_for <seconds> <path> <field> <expected>: polls until field == expected; sets LAST
wait_for() {
  local deadline=$((SECONDS + $1))
  while [ $SECONDS -lt $deadline ]; do
    http GET "$2"
    LAST=$(json_field "$3")
    [ "$LAST" = "$4" ] && return 0
    sleep 1
  done
  return 1
}

summary() {
  echo "---- $PASS passed, $FAIL failed"
  [ "$FAIL" -eq 0 ]
}
