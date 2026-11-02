#!/usr/bin/env bash
# Build and start everything, then wait until every service reports UP through the gateway.
cd "$(dirname "$0")/.." || exit 1
bash scripts/compose.sh up -d --build "$@" || exit 1
for i in $(seq 1 60); do
  h=$(curl -s localhost:8080/api/monitor/health)
  if [ -n "$h" ] && ! echo "$h" | grep -q DOWN; then echo "all UP: $h"; exit 0; fi
  sleep 3
done
echo "not all services UP after 180s: $h" >&2; exit 1
