#!/usr/bin/env bash
# CC2 (đồng thời): saga retry assign-driver. Gọi song song cùng key, hoặc key mới cùng tài xế,
# phải gán đúng 1 lần và phát 1 event trip.driver-assigned. Tạm dừng matching để saga thật không chen vào.
# Chạy: bash tests/e2e/cc2-assign.sh   (stack đang chạy)
cd "$(dirname "$0")/../.." || exit 1
source tests/e2e/lib.sh
without_matching
DRIVER=00000000-0000-0000-0000-0000000000d2
OTHER=00000000-0000-0000-0000-0000000000d3

as_passenger
http POST /api/trips "{\"pickup\":{\"lat\":10.7765,\"lng\":106.7010},\"dropoff\":{\"lat\":10.7626,\"lng\":106.6602}}"
check C1 "trip created" "201" "$CODE"
TRIP=$(json_field id)

assign() { # assign <key> <driver> -> in mã HTTP
  internal "/internal/trips/$TRIP/assign-driver" "$1" "{\"driverId\":\"$2\"}"
}

# 10 request song song trong MỘT docker exec: song song thật ở service, và Windows không phải mở 10 docker.exe.
URL=http://localhost:8083/internal/trips/$TRIP/assign-driver
BODY="{\"driverId\":\"$DRIVER\"}"
CODES=$(docker exec rideflow-trip-service-1 sh -c "
  for i in 1 2 3 4 5; do
    curl -s -o /dev/null -w '%{http_code}
' -X POST -H 'Content-Type: application/json' -H 'Idempotency-Key: cc2-$TRIP' -d '$BODY' $URL &
    curl -s -o /dev/null -w '%{http_code}
' -X POST -H 'Content-Type: application/json' -H \"Idempotency-Key: cc2-$TRIP-\$i\" -d '$BODY' $URL &
  done; wait" | sort | uniq -c | tr -s ' ' | xargs)
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
