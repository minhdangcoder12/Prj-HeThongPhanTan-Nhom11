#!/usr/bin/env bash
# Fault injection for the mandatory "stop a service mid-flow, restart, processing continues" demo.
# Usage: bash scripts/fault.sh stop|kill|start|restart <service>   e.g. bash scripts/fault.sh kill payment-service
# stop = graceful SIGTERM, kill = abrupt SIGKILL (crash). Messages wait in durable RabbitMQ queues meanwhile.
cd "$(dirname "$0")/.." || exit 1
case "${1:-}" in
  stop|kill|start|restart) ;;
  *) echo "usage: $0 stop|kill|start|restart <service>" >&2; exit 1 ;;
esac
[ -n "${2:-}" ] || { echo "service name required" >&2; exit 1; }
echo "$(date -u +%FT%TZ) fault: $1 $2"
bash scripts/compose.sh "$1" "$2"
