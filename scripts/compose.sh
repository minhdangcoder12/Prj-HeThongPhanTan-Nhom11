#!/usr/bin/env bash
# docker compose with the repo layout baked in. Usage: bash scripts/compose.sh <any compose args>
cd "$(dirname "$0")/.." || exit 1
[ -f .env ] || { echo ".env missing: cp .env.example .env" >&2; exit 1; }
exec docker compose --env-file .env -f deploy/docker-compose.yml "$@"
