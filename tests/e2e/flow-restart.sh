#!/usr/bin/env bash
# Capability eval for spec 7.4 "stop one service during a business flow, restart it, processing continues".
# Kills payment-service (SIGKILL) right before the trip completes; trip.completed waits in the durable
# queue payment.trip-completed.q; after restart the trip must reach PAID. Prints recovery time (report §15).
# Expected to FAIL until Phase 1–2 land. Usage: bash tests/e2e/flow-restart.sh
cd "$(dirname "$0")/../.." || exit 1
source tests/e2e/lib.sh
D01=00000000-0000-0000-0000-0000000000d1

step() {
  check "$@"
  if [ "$3" != "$4" ]; then
    echo "     signature: HTTP $CODE ${BODY:0:200}"
    bash scripts/fault.sh start payment-service > /dev/null
    summary; exit 1
  fi
}

as_driver
http PUT "/api/drivers/$D01/availability" '{"available":true}'
step S1 "driver D01 AVAILABLE" "200" "$CODE"

as_passenger 2
http POST /api/trips "{\"pickup\":{\"lat\":10.7765,\"lng\":106.7010},\"dropoff\":{\"lat\":10.7626,\"lng\":106.6602}}"
step S2 "trip requested" "201" "$CODE"
TRIP=$(json_field id)

wait_for 20 "/api/trips/$TRIP" status DRIVER_ASSIGNED
step S3 "driver assigned" "DRIVER_ASSIGNED" "$LAST"
as_driver
for action in accept start; do
  http POST "/api/trips/$TRIP/$action"
  step S4 "driver $action" "200" "$CODE"
done

bash scripts/fault.sh kill payment-service
http POST "/api/trips/$TRIP/complete"
step S5 "trip completes while payment-service is down" "200" "$CODE"

sleep 5
http GET "/api/trips/$TRIP"
step S6 "trip not PAID while payment-service is down" "COMPLETED" "$(json_field status)"

STARTED=$SECONDS
bash scripts/fault.sh start payment-service
wait_for 120 "/api/trips/$TRIP" status PAID
step S7 "after restart the queued event is processed: PAID" "PAID" "$LAST"
echo "     recovery time (restart -> PAID): $((SECONDS - STARTED))s"

summary
