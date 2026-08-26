#!/usr/bin/env bash
# Usage: scripts/loadgen.sh [host] [ingest_port] [msgs_per_worker] [workers] [http_port]
# Sends text-format lines over UDP using GNU parallel.
HOST=${1:-localhost}; PORT=${2:-11222}; N=${3:-20000}; W=${4:-8}; HTTP_PORT=${5:-8080}
worker() {
  local id=$1
  for ((i=0; i<N; i++)); do
    echo "loadgen$id [bench,w$id] INFO message $i $(date +%s%N)"
  done | nc -u -q0 "$HOST" "$PORT"
}
export -f worker; export HOST PORT N
time parallel worker ::: $(seq 1 "$W")
echo "sent $((N*W)) messages"
curl -s "http://$HOST:$HTTP_PORT/api/health" | jq .stats
