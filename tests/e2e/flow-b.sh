#!/usr/bin/env bash
# Capability eval for Flow B (Concurrency: 2 passengers compete for 1 available driver).
# Usage: bash tests/e2e/flow-b.sh
cd "$(dirname "$0")/../.." || exit 1
source tests/e2e/lib.sh

D01=00000000-0000-0000-0000-0000000000d1
D02=00000000-0000-0000-0000-0000000000d2
D03=00000000-0000-0000-0000-0000000000d3

PASSENGER_A=bbbbbbbb-0000-4000-8000-000000000001
PASSENGER_B=bbbbbbbb-0000-4000-8000-000000000002

step() { # step <id> <description> <expected> <actual>
  check "$@"
  if [ "$3" != "$4" ]; then
    echo "     signature: HTTP $CODE ${BODY:0:200}"
    summary; exit 1
  fi
}

echo "=== Setup: Only D01 is AVAILABLE, other drivers are OFFLINE ==="
http PUT "/api/drivers/$D02/availability" '{"available":false}'
http PUT "/api/drivers/$D03/availability" '{"available":false}'

http PUT "/api/drivers/$D01/availability" '{"available":true}'
step B1 "driver D01 is AVAILABLE" "200" "$CODE"

http PUT "/api/drivers/$D01/location" '{"lat":10.7769,"lng":106.7009}'
step B2 "driver D01 location updated" "200" "$CODE"

echo "=== Requesting 2 trips concurrently for Passenger A and Passenger B ==="
REQ_A="{\"passengerId\":\"$PASSENGER_A\",\"pickup\":{\"lat\":10.7765,\"lng\":106.7010},\"dropoff\":{\"lat\":10.7626,\"lng\":106.6602}}"
REQ_B="{\"passengerId\":\"$PASSENGER_B\",\"pickup\":{\"lat\":10.7766,\"lng\":106.7011},\"dropoff\":{\"lat\":10.7626,\"lng\":106.6602}}"

TMP_A=$(mktemp)
TMP_B=$(mktemp)

curl -s -w '\n%{http_code}' -X POST -H 'Content-Type: application/json' -d "$REQ_A" "$GW/api/trips" > "$TMP_A" &
PID_A=$!
curl -s -w '\n%{http_code}' -X POST -H 'Content-Type: application/json' -d "$REQ_B" "$GW/api/trips" > "$TMP_B" &
PID_B=$!

wait $PID_A $PID_B

OUT_A=$(cat "$TMP_A")
OUT_B=$(cat "$TMP_B")
rm -f "$TMP_A" "$TMP_B"

CODE_A=${OUT_A##*$'\n'}
BODY_A=${OUT_A%$'\n'*}
CODE_B=${OUT_B##*$'\n'}
BODY_B=${OUT_B%$'\n'*}

step B3 "Passenger A trip created" "201" "$CODE_A"
step B4 "Passenger B trip created" "201" "$CODE_B"

TRIP_A=$(printf '%s' "$BODY_A" | grep -o '"id":"\?[^",}]*' | head -1 | sed -E 's/"id":"?//')
TRIP_B=$(printf '%s' "$BODY_B" | grep -o '"id":"\?[^",}]*' | head -1 | sed -E 's/"id":"?//')

echo "     Trip A: $TRIP_A"
echo "     Trip B: $TRIP_B"

echo "=== Waiting for saga resolution (max 25s) ==="
sleep 10

http GET "/api/trips/$TRIP_A"
STATUS_A=$(json_field status)
DRIVER_A=$(json_field driverId)

http GET "/api/trips/$TRIP_B"
STATUS_B=$(json_field status)
DRIVER_B=$(json_field driverId)

echo "     Trip A status: $STATUS_A (driver: $DRIVER_A)"
echo "     Trip B status: $STATUS_B (driver: $DRIVER_B)"

# At most one trip can get driver D01
ASSIGNED_COUNT=0
[ "$DRIVER_A" = "$D01" ] && ASSIGNED_COUNT=$((ASSIGNED_COUNT + 1))
[ "$DRIVER_B" = "$D01" ] && ASSIGNED_COUNT=$((ASSIGNED_COUNT + 1))

step B5 "Atomic reservation: exactly 1 trip wins driver D01" "1" "$ASSIGNED_COUNT"

# The losing trip should not be assigned to D01
if [ "$DRIVER_A" = "$D01" ]; then
  step B6 "Winner is Trip A" "DRIVER_ASSIGNED" "$STATUS_A"
  check B7 "Loser Trip B is not assigned to D01" "true" "$([ "$DRIVER_B" != "$D01" ] && echo true || echo false)"
else
  step B6 "Winner is Trip B" "DRIVER_ASSIGNED" "$STATUS_B"
  check B7 "Loser Trip A is not assigned to D01" "true" "$([ "$DRIVER_A" != "$D01" ] && echo true || echo false)"
fi

summary
