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

# login <email> <password>: đăng nhập, đặt TOKEN và AUTH để http() gửi Bearer token từ đó trở đi
login() {
  http POST /api/auth/login "{\"email\":\"$1\",\"password\":\"$2\"}"
  TOKEN=$(json_field token)
  AUTH=(-H "Authorization: Bearer $TOKEN")
}
# Tài khoản seed (user-service V3__seed_users.sql), mật khẩu chung demo1234
as_passenger() { login "passenger${1:-1}@rideflow.demo" demo1234; }
as_driver() { login "driver${1:-1}@rideflow.demo" demo1234; }

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

# internal <path> <Idempotency-Key> <json>: gọi /internal/** từ trong container trip-service
# (service không mở cổng host, gateway không route /internal/**); in mã HTTP.
internal() {
  docker exec rideflow-trip-service-1 curl -s -o /dev/null -w '%{http_code}
' -X POST     -H 'Content-Type: application/json' -H "Idempotency-Key: $2" -d "$3" "http://localhost:8083$1"
}

# without_matching: dừng matching-service tới hết script để saga không tự xử lý chuyến của bài test.
without_matching() {
  bash scripts/fault.sh stop matching-service >/dev/null 2>&1
  if [ -n "$(docker ps -q --filter name=rideflow-matching-service --filter status=running)" ]; then
    echo "cannot stop matching-service, eval would race the real saga" >&2; exit 1
  fi
  trap 'bash scripts/fault.sh start matching-service >/dev/null 2>&1' EXIT
}
