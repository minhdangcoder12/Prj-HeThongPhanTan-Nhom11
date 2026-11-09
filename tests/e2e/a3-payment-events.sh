#!/usr/bin/env bash
# A3 eval: trip-service applies payment.succeeded / payment.failed only when the trip is COMPLETED (rule R3),
# ignores duplicates (inbox) and never lets a late failure overwrite PAID. Payment events are injected through
# the RabbitMQ management API so this runs before payment-service (task C1) exists.
# Usage: bash tests/e2e/a3-payment-events.sh   (stack running; needs node for JSON)
cd "$(dirname "$0")/../.." || exit 1
source tests/e2e/lib.sh
source .env
TRIP_URL=${TRIP_URL:-http://localhost:8083}
MQ=${MQ:-http://localhost:15672}
PASSENGER=bbbbbbbb-0000-4000-8000-000000000001
DRIVER=00000000-0000-0000-0000-0000000000d3

# publish <eventType> <tripId> [eventId] -> publishes a PaymentEnvelope on rideflow.events
publish() {
  local body
  body=$(node -e '
    const [type, trip, id] = process.argv.slice(1); const eventId = id || crypto.randomUUID();
    const env = { eventId, eventType: type, eventVersion: 1, correlationId: eventId, causationId: null,
      aggregateId: crypto.randomUUID(), producer: "payment-service", occurredAt: new Date().toISOString(),
      payload: { paymentId: crypto.randomUUID(), tripId: trip, amount: 31000, status: type.endsWith("succeeded") ? "SUCCEEDED" : "FAILED" } };
    console.log(JSON.stringify({ properties: { content_type: "application/json" }, routing_key: type,
      payload_encoding: "string", payload: JSON.stringify(env) }));' "$@")
  curl -s -o /dev/null -u "$RABBITMQ_USERNAME:$RABBITMQ_PASSWORD" -H 'Content-Type: application/json' \
    -X POST "$MQ/api/exchanges/%2F/rideflow.events/publish" -d "$body"
}
status_of() { http GET "/api/trips/$1"; json_field status; }

http POST /api/trips "{\"passengerId\":\"$PASSENGER\",\"pickup\":{\"lat\":10.7765,\"lng\":106.7010},\"dropoff\":{\"lat\":10.7626,\"lng\":106.6602}}"
TRIP=$(json_field id)

publish payment.succeeded "$TRIP"; sleep 2
check P1 "payment.succeeded before COMPLETED is ignored" "REQUESTED" "$(status_of "$TRIP")"

# Drive the trip to COMPLETED (assign via internal API, as the saga would).
curl -s -o /dev/null -X POST -H 'Content-Type: application/json' -H "Idempotency-Key: a3-$TRIP" \
  -d "{\"driverId\":\"$DRIVER\"}" "$TRIP_URL/internal/trips/$TRIP/assign-driver"
for action in accept start complete; do http POST "/api/trips/$TRIP/$action"; done
check P2 "trip reached COMPLETED" "COMPLETED" "$(status_of "$TRIP")"

EVENT=$(node -e 'console.log(crypto.randomUUID())')
publish payment.succeeded "$TRIP" "$EVENT"; publish payment.succeeded "$TRIP" "$EVENT"; sleep 2
check P3 "payment.succeeded (delivered twice) -> PAID" "PAID" "$(status_of "$TRIP")"

publish payment.failed "$TRIP"; sleep 2
check P4 "late payment.failed does not overwrite PAID" "PAID" "$(status_of "$TRIP")"

DLQ=$(curl -s -u "$RABBITMQ_USERNAME:$RABBITMQ_PASSWORD" "$MQ/api/queues/%2F/trip.payment-events.q.dlq" | grep -o '"messages":[0-9]*' | head -1 | cut -d: -f2)
check P5 "nothing went to the DLQ" "0" "${DLQ:-0}"

summary
