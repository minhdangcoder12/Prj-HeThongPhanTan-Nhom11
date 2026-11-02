#!/usr/bin/env bash
# Capability eval for Flow A (happy path). Expected to FAIL until Phase 1–2 land; the first failing
# step is the current "failure signature". Stops at the first failure because later steps depend on it.
# Usage: bash tests/e2e/flow-a.sh   (set TOKEN=... once JWT is enforced)
cd "$(dirname "$0")/../.." || exit 1
source tests/e2e/lib.sh
D01=00000000-0000-0000-0000-0000000000d1
PASSENGER=bbbbbbbb-0000-4000-8000-000000000001

step() { # step <id> <description> <expected> <actual>
  check "$@"
  if [ "$3" != "$4" ]; then
    echo "     signature: HTTP $CODE ${BODY:0:200}"
    summary; exit 1
  fi
}

http PUT "/api/drivers/$D01/availability" '{"available":true}'
step A1 "driver D01 goes AVAILABLE" "200" "$CODE"

http PUT "/api/drivers/$D01/location" '{"lat":10.7769,"lng":106.7009}'
step A2 "driver D01 location updated" "200" "$CODE"

http POST /api/trips "{\"passengerId\":\"$PASSENGER\",\"pickup\":{\"lat\":10.7765,\"lng\":106.7010},\"dropoff\":{\"lat\":10.7626,\"lng\":106.6602}}"
step A3 "passenger requests trip" "201" "$CODE"
TRIP=$(json_field id)

wait_for 20 "/api/trips/$TRIP" status DRIVER_ASSIGNED
step A4 "saga assigns a driver within 20s" "DRIVER_ASSIGNED" "$LAST"

for action in accept start complete; do
  http POST "/api/trips/$TRIP/$action"
  step "A5" "driver $action" "200" "$CODE"
done

wait_for 20 "/api/trips/$TRIP" status PAID
step A6 "payment settles trip to PAID within 20s" "PAID" "$LAST"

http GET /api/notifications
step A7 "notifications recorded for the trip" "yes" "$(printf '%s' "$BODY" | grep -q "$TRIP" && echo yes || echo no)"

http GET "/api/drivers"
step A8 "driver D01 free again after trip" "1" "$(printf '%s' "$BODY" | grep -o "\"id\":\"$D01\"[^}]*\"status\":\"AVAILABLE\"" | wc -l | tr -d ' ')"

summary
