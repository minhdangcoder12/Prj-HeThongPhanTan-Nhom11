#!/usr/bin/env bash
# Eval A3: trip-service chỉ áp payment.* khi chuyến đã COMPLETED (R3), bỏ qua event trùng (inbox),
# payment.failed đến muộn không ghi đè PAID. Event được bơm qua RabbitMQ management API nên chạy được khi chưa có payment-service.
# Chạy: bash tests/e2e/a3-payment-events.sh   (stack đang chạy, cần node)
cd "$(dirname "$0")/../.." || exit 1
source tests/e2e/lib.sh
source .env
without_matching
MQ=${MQ:-http://localhost:15672}
DRIVER=00000000-0000-0000-0000-0000000000d3

# publish <eventType> <tripId> [eventId]: phát PaymentEnvelope lên rideflow.events
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

as_passenger
http POST /api/trips "{\"pickup\":{\"lat\":10.7765,\"lng\":106.7010},\"dropoff\":{\"lat\":10.7626,\"lng\":106.6602}}"
TRIP=$(json_field id)

publish payment.succeeded "$TRIP"; sleep 2
check P1 "payment.succeeded before COMPLETED is ignored" "REQUESTED" "$(status_of "$TRIP")"

# Đưa chuyến tới COMPLETED (assign qua API nội bộ như saga).
internal "/internal/trips/$TRIP/assign-driver" "a3-$TRIP" "{\"driverId\":\"$DRIVER\"}" >/dev/null
as_driver 3 # DRIVER là D03: chỉ tài xế được gán mới accept được
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
