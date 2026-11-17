#!/usr/bin/env bash
# Capability eval for Flow C (Retry 2s -> 5s -> 10s -> DLQ -> Reprocess).
# Usage: bash tests/e2e/flow-c.sh
cd "$(dirname "$0")/../.." || exit 1
source tests/e2e/lib.sh

PASSENGER=bbbbbbbb-0000-4000-8000-000000000003

step() { # step <id> <description> <expected> <actual>
  check "$@"
  if [ "$3" != "$4" ]; then
    echo "     signature: HTTP $CODE ${BODY:0:200}"
    # Reset chaos fail rate before exiting to avoid leaving the system in a broken state
    http PUT "/api/chaos/notification/fail-rate" '{"rate":0.0}'
    summary; exit 1
  fi
}

echo "=== Step 1: Enable chaos fail-rate = 1.0 (100% notification failures) ==="
http PUT "/api/chaos/notification/fail-rate" '{"rate":1.0}'
step C1 "chaos fail-rate set to 1.0" "200" "$CODE"

echo "=== Step 2: Trigger an event by requesting a trip ==="
http POST /api/trips "{\"passengerId\":\"$PASSENGER\",\"pickup\":{\"lat\":10.7765,\"lng\":106.7010},\"dropoff\":{\"lat\":10.7626,\"lng\":106.6602}}"
step C2 "trip created to emit event" "201" "$CODE"
TRIP=$(json_field id)

echo "=== Step 3: Wait for bounded retries (2s + 5s + 10s = ~17s) to exhaust into DLQ ==="
echo "     Waiting 22s for message to land in DLQ..."
sleep 22

http GET "/api/notifications/dlq"
step C3 "GET /api/notifications/dlq returns 200" "200" "$CODE"
DLQ_COUNT=$(json_field count)
echo "     Current DLQ count: $DLQ_COUNT"
check C4 "message landed in DLQ (count >= 1)" "true" "$([ "${DLQ_COUNT:-0}" -ge 1 ] && echo true || echo false)"

echo "=== Step 4: Turn off chaos fail-rate (rate = 0.0) ==="
http PUT "/api/chaos/notification/fail-rate" '{"rate":0.0}'
step C5 "chaos fail-rate reset to 0.0" "200" "$CODE"

echo "=== Step 5: Reprocess DLQ back to main queue ==="
http POST "/api/notifications/dlq/reprocess"
step C6 "POST /api/notifications/dlq/reprocess returns 200" "200" "$CODE"
REPROCESSED=$(json_field reprocessed)
echo "     Reprocessed messages: $REPROCESSED"
check C7 "at least 1 message reprocessed" "true" "$([ "${REPROCESSED:-0}" -ge 1 ] && echo true || echo false)"

sleep 3
http GET "/api/notifications/dlq"
DLQ_AFTER=$(json_field count)
echo "     DLQ count after reprocess: $DLQ_AFTER"
step C8 "DLQ is now empty (count = 0)" "0" "${DLQ_AFTER:-0}"

http GET "/api/notifications"
step C9 "notifications successfully recorded" "yes" "$(printf '%s' "$BODY" | grep -q "$TRIP" && echo yes || echo no)"

summary
