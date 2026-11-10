#!/usr/bin/env bash
# Capability eval for Flow E (compensation). Verifies that when trip assign fails,
# the matching saga compensates by releasing the reserved driver and terminates in COMPENSATED.
# Usage: bash tests/e2e/flow-e.sh
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

# E1: Make sure driver D01 is available
http PUT "/api/drivers/$D01/availability" '{"available":true}'
step E1 "driver D01 goes AVAILABLE" "200" "$CODE"

# E2: Driver D01 location
http PUT "/api/drivers/$D01/location" '{"lat":10.7769,"lng":106.7009}'
step E2 "driver D01 location updated" "200" "$CODE"

# E3: Enable chaos fault on trip service (fail-assign)
http PUT "/api/chaos/trip/fail-assign" '{"enabled":true}'
step E3 "enable fail-assign fault" "200" "$CODE"

# E4: Passenger requests a trip
http POST /api/trips "{\"passengerId\":\"$PASSENGER\",\"pickup\":{\"lat\":10.7765,\"lng\":106.7010},\"dropoff\":{\"lat\":10.7626,\"lng\":106.6602}}"
step E4 "passenger requests trip" "201" "$CODE"
TRIP=$(json_field id)

# E5: Saga tries to assign driver, fails, compensates, and finishes in COMPENSATED
wait_for 20 "/api/matching/sagas/$TRIP" status COMPENSATED
step E5 "saga compensates and reaches COMPENSATED" "COMPENSATED" "$LAST"

# E6: Verify driver D01 is released back to AVAILABLE (not stuck in RESERVED)
http GET "/api/drivers"
step E6 "driver D01 released back to AVAILABLE" "1" "$(printf '%s' "$BODY" | grep -o "\"id\":\"$D01\"[^}]*\"status\":\"AVAILABLE\"" | wc -l | tr -d ' ')"

# E7: Disable chaos fault
http PUT "/api/chaos/trip/fail-assign" '{"enabled":false}'
step E7 "disable fail-assign fault" "200" "$CODE"

summary
