#!/usr/bin/env bash
# Regression eval: what Phase 0 guarantees. Must stay green after every PR.
# Usage: bash tests/e2e/regression.sh   (stack running, .env present)
cd "$(dirname "$0")/../.." || exit 1
source tests/e2e/lib.sh
set -a; source .env; set +a
D01=00000000-0000-0000-0000-0000000000d1

http GET /api/monitor/health
check R1 "all 6 services UP" "6" "$(printf '%s' "$BODY" | grep -o '"UP"' | wc -l | tr -d ' ')"

http GET /api/drivers
check R2 "seeded driver D01 listed" "1" "$(printf '%s' "$BODY" | grep -c "$D01")"

http GET /api/trips/00000000-0000-0000-0000-000000000000
check R3 "unknown trip -> 404 ProblemDetail" "404 Not Found" "$CODE $(json_field title)"

http GET /internal/drivers
check R4 "/internal/** not routed by gateway" "404" "$CODE"

BIG=$(mktemp)
{ printf '{"pad":"'; head -c 70000 /dev/zero | tr '\0' x; printf '"}'; } > "$BIG"
CODE=$(curl -s -o /dev/null -w '%{http_code}' -H 'Content-Type: application/json' --data-binary @"$BIG" "$GW/api/trips")
rm -f "$BIG"
check R9 "body over 64KB rejected at gateway (spec 4.5)" "413" "$CODE"

CID=$(cat /proc/sys/kernel/random/uuid 2>/dev/null || date +%s%N)
ECHO=$(curl -s -D - -o /dev/null -H "X-Correlation-Id: $CID" "$GW/api/drivers" | grep -i '^x-correlation-id' | tr -d '\r' | awk '{print $2}')
check R5 "X-Correlation-Id propagated and echoed" "$CID" "$ECHO"

SSE=$(mktemp)
timeout 6 curl -sN "$GW/api/monitor/events" > "$SSE" &
sleep 1
http POST /api/monitor/test-event
PING=$(json_field eventId)
wait
check R6 "test event visible on SSE monitor" "1" "$(grep -c "$PING" "$SSE")"
rm -f "$SSE"

# R7 – Flow D: same eventId delivered twice => one notification.
count_notifications() { http GET /api/notifications; printf '%s' "$BODY" | grep -o '"type"' | wc -l | tr -d ' '; }
BEFORE=$(count_notifications)
EID=$(cat /proc/sys/kernel/random/uuid 2>/dev/null || printf '%08d-0000-4000-8000-%012d' $RANDOM $RANDOM)
TRIP=aaaaaaaa-0000-4000-8000-000000000001
EVENT="{\\\"eventId\\\":\\\"$EID\\\",\\\"eventType\\\":\\\"trip.created\\\",\\\"eventVersion\\\":1,\\\"correlationId\\\":\\\"$EID\\\",\\\"causationId\\\":null,\\\"aggregateId\\\":\\\"$TRIP\\\",\\\"producer\\\":\\\"eval\\\",\\\"occurredAt\\\":\\\"2026-01-01T00:00:00Z\\\",\\\"payload\\\":{}}"
curl -s -u "$RABBITMQ_USERNAME:$RABBITMQ_PASSWORD" -H 'Content-Type: application/json' \
  -X POST "http://localhost:15672/api/exchanges/%2F/rideflow.events/publish" \
  -d "{\"properties\":{\"content_type\":\"application/json\"},\"routing_key\":\"trip.created\",\"payload\":\"$EVENT\",\"payload_encoding\":\"string\"}" > /dev/null
sleep 3
AFTER_FIRST=$(count_notifications)
http POST "/api/monitor/replay/$EID"
sleep 3
AFTER_REPLAY=$(count_notifications)
check R7 "first delivery creates 1 notification" "$((BEFORE + 1))" "$AFTER_FIRST"
check R8 "replay with same eventId creates none (inbox)" "$AFTER_FIRST" "$AFTER_REPLAY"

summary
