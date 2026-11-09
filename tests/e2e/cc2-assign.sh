#!/usr/bin/env bash
# CC2 (đồng thời): saga retry assign-driver. Gọi song song cùng key, hoặc key mới cùng tài xế,
# phải gán đúng 1 lần và phát 1 event trip.driver-assigned. /internal/** không qua gateway nên gọi thẳng trip-service.
# Chạy: bash tests/e2e/cc2-assign.sh   (stack đang chạy)
cd "$(dirname "$0")/../.." || exit 1
source tests/e2e/lib.sh
TRIP_URL=${TRIP_URL:-http://localhost:8083}
PASSENGER=bbbbbbbb-0000-4000-8000-000000000001
DRIVER=00000000-0000-0000-0000-0000000000d2
OTHER=00000000-0000-0000-0000-0000000000d3

http POST /api/trips "{\"passengerId\":\"$PASSENGER\",\"pickup\":{\"lat\":10.7765,\"lng\":106.7010},\"dropoff\":{\"lat\":10.7626,\"lng\":106.6602}}"
check C1 "trip created" "201" "$CODE"
TRIP=$(json_field id)

assign() { # assign <key> <driver> -> in mã HTTP
  curl -s -o /dev/null -w '%{http_code}\n' -X POST -H 'Content-Type: application/json' -H "Idempotency-Key: $1" \
    -d "{\"driverId\":\"$2\"}" "$TRIP_URL/internal/trips/$TRIP/assign-driver"
}

CODES=$( { for i in 1 2 3 4 5; do assign "cc2-$TRIP" "$DRIVER" & done;
           for i in 1 2 3 4 5; do assign "cc2-$TRIP-$i" "$DRIVER" & done; wait; } | sort | uniq -c | tr -s ' ' | xargs)
check C2 "10 parallel assigns (same driver) all 200" "10 200" "$CODES"

http GET "/api/trips/$TRIP"
check C3 "trip is DRIVER_ASSIGNED to that driver" "DRIVER_ASSIGNED $DRIVER" "$(json_field status) $(json_field driverId)"

check C4 "another driver is refused" "409" "$(assign "cc2-$TRIP-other" "$OTHER")"

# Đếm trong outbox của trip-service, không phụ thuộc service khác.
source .env
EVENTS=$(docker exec rideflow-postgres-1 psql -U "$POSTGRES_USER" -d rideflow_trip -tAc \
  "select count(*) from outbox_event where aggregate_id = '$TRIP' and event_type = 'trip.driver-assigned'")
check C5 "exactly one trip.driver-assigned event" "1" "$EVENTS"

summary
