#!/usr/bin/env bash
# Performance load testing script for RideFlow (Task C7 / Report Chapter 15).
# Fires N concurrent ride booking requests to API Gateway, measuring latency percentiles (p50, p90, p95),
# throughput (RPS), and success rates.
#
# Usage:
#   bash scripts/load.sh [concurrency] [total_requests]
#   Example: bash scripts/load.sh 10 50

cd "$(dirname "$0")/.." || exit 1

GW=${GW:-http://localhost:8080}
CONCURRENCY=${1:-10}
TOTAL=${2:-50}

echo "=========================================================="
echo "          RideFlow Performance Load Test (Task C7)       "
echo "=========================================================="
echo "Target Gateway: $GW"
echo "Concurrency:    $CONCURRENCY concurrent workers"
echo "Total Requests: $TOTAL ride requests"
echo "Start Time:     $(date '+%Y-%m-%d %H:%M:%S')"
echo "=========================================================="

TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT

RESULTS_FILE="$TMP_DIR/results.txt"
> "$RESULTS_FILE"

send_request() {
  local id="$1"
  local passenger="00000000-0000-4000-8000-$(printf '%012x' "$id")"
  local payload="{\"passengerId\":\"$passenger\",\"pickup\":{\"lat\":10.7765,\"lng\":106.7010},\"dropoff\":{\"lat\":10.7626,\"lng\":106.6602}}"
  
  local out
  out=$(curl -s -w "%{http_code} %{time_total}\n" -o /dev/null -X POST \
    -H 'Content-Type: application/json' \
    -d "$payload" \
    "$GW/api/trips" 2>/dev/null)
    
  echo "$out" >> "$RESULTS_FILE"
}

START_TIME=$(node -e 'console.log(Date.now())')

# Dispatch requests with bounded concurrency
running=0
for i in $(seq 1 "$TOTAL"); do
  send_request "$i" &
  running=$((running + 1))
  if [ "$running" -ge "$CONCURRENCY" ]; then
    wait -n 2>/dev/null || wait
    running=$((running - 1))
  fi
done

wait

END_TIME=$(node -e 'console.log(Date.now())')
ELAPSED_SEC=$(node -e "console.log((($END_TIME - $START_TIME) / 1000).toFixed(2))")

echo ""
echo "Requests completed in ${ELAPSED_SEC}s. Processing statistics..."

node -e '
const fs = require("fs");
const file = process.argv[1];
const elapsed = parseFloat(process.argv[2]);
const totalConfigured = parseInt(process.argv[3], 10);
const concurrency = parseInt(process.argv[4], 10);

if (!fs.existsSync(file)) {
  console.log("No results collected.");
  process.exit(1);
}

const lines = fs.readFileSync(file, "utf8").trim().split("\n").filter(Boolean);
let success = 0;
let failed = 0;
const latenciesMs = [];

for (const line of lines) {
  const [code, sec] = line.trim().split(/\s+/);
  if (code === "201" || code === "200") {
    success++;
  } else {
    failed++;
  }
  const ms = parseFloat(sec) * 1000;
  if (!isNaN(ms)) {
    latenciesMs.push(ms);
  }
}

latenciesMs.sort((a, b) => a - b);

const total = latenciesMs.length;
const rps = (total / elapsed).toFixed(2);
const min = total > 0 ? latenciesMs[0].toFixed(1) : 0;
const max = total > 0 ? latenciesMs[total - 1].toFixed(1) : 0;
const sum = latenciesMs.reduce((acc, v) => acc + v, 0);
const mean = total > 0 ? (sum / total).toFixed(1) : 0;

function p(pct) {
  if (total === 0) return 0;
  const idx = Math.min(total - 1, Math.floor((pct / 100) * total));
  return latenciesMs[idx].toFixed(1);
}

const p50 = p(50);
const p90 = p(90);
const p95 = p(95);
const p99 = p(99);

const report = `
### Kết quả đo tải hiệu năng (Performance Test Report)

| Thông số (Metric) | Giá trị (Value) |
|---|---|
| Tổng yêu cầu (Total Requests) | ${total} |
| Số luồng đồng thời (Concurrency) | ${concurrency} |
| Thành công (HTTP 201 Created) | ${success} (${((success / total) * 100).toFixed(1)}%) |
| Thất bại (Failed / Non-201) | ${failed} |
| Tổng thời gian (Total Duration) | ${elapsed} s |
| Thông lượng (Throughput) | **${rps} req/s** |
| Latency Min | ${min} ms |
| Latency Average (Mean) | ${mean} ms |
| **Latency p50 (Median)** | **${p50} ms** |
| **Latency p90** | **${p90} ms** |
| **Latency p95** | **${p95} ms** |
| Latency p99 | ${p99} ms |
| Latency Max | ${max} ms |
`;

console.log(report);

fs.writeFileSync("docs/load-test-results.md", report, "utf8");
console.log("Saved report table to docs/load-test-results.md");
' "$RESULTS_FILE" "$ELAPSED_SEC" "$TOTAL" "$CONCURRENCY"

echo "=========================================================="
echo "Done."
