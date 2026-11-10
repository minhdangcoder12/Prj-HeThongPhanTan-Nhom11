#!/usr/bin/env bash
# Scale matching-service and trip-service to 2 instances each (Task B9),
# and verify that multi-instance scaling is functional.
# Usage: bash scripts/scale.sh [matching_instances] [trip_instances]
set -euo pipefail

cd "$(dirname "$0")/.." || exit 1

MATCHING_INSTANCES=${1:-2}
TRIP_INSTANCES=${2:-2}

echo "=== Scaling RideFlow Services ==="
echo "Scaling matching-service to ${MATCHING_INSTANCES} instances"
echo "Scaling trip-service to ${TRIP_INSTANCES} instances"

bash scripts/compose.sh up -d --scale matching-service="${MATCHING_INSTANCES}" --scale trip-service="${TRIP_INSTANCES}"

echo "Waiting for services to become healthy via API Gateway..."
for i in $(seq 1 60); do
  h=$(curl -s localhost:8080/api/monitor/health 2>/dev/null || true)
  if [ -n "$h" ] && ! echo "$h" | grep -q DOWN; then
    echo "All services UP: $h"
    echo "=== Scaled Containers ==="
    bash scripts/compose.sh ps matching-service trip-service
    echo "=== Multi-instance scaling completed successfully ==="
    exit 0
  fi
  sleep 3
done

echo "Timed out waiting for scaled services to be healthy: ${h:-none}" >&2
exit 1
