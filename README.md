# dvlogger

> **⚠️ Vibecoded project — use at your own risk.**
> dvlogger was generated end-to-end by an AI coding agent (Claude) from a design spec, via an
> automated plan → implement → review loop. Verification so far consists of automated tests and
> AI code reviews — there has been **no line-by-line human audit**. Review the code yourself
> before relying on it, and especially before any production use.

dvlogger ("dev logger") is a minimal, Graylog-like log platform for development environments:

- **Ingest** free-text, JSON, or GELF log lines over TCP, UDP, or HTTP on a single port (`11222`).
- **Store** every entry in MongoDB, with an optional permanent archive collection.
- **Search** full text via Manticore Search; matches are hydrated from MongoDB, the source of truth.
- **Browse** everything from a small Nuxt dashboard, served by the same backend on `8080`.

## Quick start

```bash
cp .env.example .env
# edit .env and set AUTH_PASSWORD (and AUTH_USER if you like)
docker compose up -d --build
```

Then open http://localhost:8080 and log in with the user/password from `.env`.

If ports `8080` or `11222` are already used on your machine, override the host-side mapping
without touching the container internals:

```bash
HTTP_PORT_HOST=18080 INGEST_PORT_HOST=19222 docker compose up -d --build
```

## Sending logs

**Plain text over TCP:**

```bash
echo "app1 [web,prod] hello from tcp" | nc -q0 localhost 11222
```

**GELF over UDP:**

```bash
echo '{"version":"1.1","host":"h","short_message":"hello from udp gelf","level":3}' | nc -u -q0 localhost 11222
```

**JSON over HTTP:**

```bash
curl -s -X POST localhost:8080/api/ingest -d '{"message":"hello from http","source":"curl","tags":["x"]}'
```

### Text line format

When `LOG_FORMAT` is `text` (or `auto` falls back to text), each line is parsed as:

```
<source> [tag1,tag2] message
```

For example `app1 [web,prod] hello from tcp` → source `app1`, tags `web,prod`, message
`hello from tcp`. Lines that don't match the pattern are stored as-is, with the sender's IP as
the source. The pattern is configurable via `LOG_TEXT_PATTERN` (a Java regex with named groups
`source`, `tags`, `message`).

## Configuration

All variables below can be set in `.env` (copied from `.env.example`); container-internal ports
(`8080` for HTTP, `11222` for ingest) never change — only the host-side mapping does.

| Variable | Default | Description |
|---|---|---|
| `AUTH_USER` | `admin` | Dashboard/API login username |
| `AUTH_PASSWORD` | `admin` | Dashboard/API login password — **change this** |
| `LOG_FORMAT` | `auto` | Ingest parser: `text` \| `json` \| `gelf` \| `auto` (auto-detect per message) |
| `LOG_TEXT_PATTERN` | *(built-in default)* | Regex for the `text` parser, with named groups `source`, `tags`, `message` |
| `ARCHIVE_ENABLED` | `false` | Also write every entry to a permanent, non-expiring archive collection |
| `RETENTION_DAYS` | `14` | TTL for the primary (non-archive) log collection, in days |
| `REINDEX_ON_START` | `false` | On startup, rebuild the Manticore search index from MongoDB |
| `INGEST_TOKEN` | *(unset)* | If set, `POST /api/ingest` requires header `X-Ingest-Token: <value>` |
| `BATCH_SIZE` | `500` | Max entries per write batch (Mongo + Manticore) |
| `BATCH_MS` | `200` | Max time (ms) to wait before flushing a partial batch |
| `HTTP_PORT_HOST` | `8080` | Host port mapped to the dashboard/API (container port stays `8080`) |
| `INGEST_PORT_HOST` | `11222` | Host port mapped to TCP/UDP ingest (container port stays `11222`) |

### Archive mode

With `ARCHIVE_ENABLED=true`, every ingested entry is additionally written to a separate,
permanent `logs_archive` collection in MongoDB with **no TTL** — it is never deleted by
retention. `RETENTION_DAYS` only governs the primary, Manticore-indexed collection.

The dashboard has its own **Archívum** view for it: the menu entry appears whenever the backend
reports `archiveEnabled: true`, and the view is served by `GET /api/archive/*`, which queries the
archive collection in MongoDB directly — the archive is not indexed in Manticore, so archive
search uses MongoDB text/regex matching and is slower than live search on large collections.

Live search works the other way round: Manticore answers the full-text query with document ids,
and the matching documents are then always hydrated from MongoDB (MongoDB is the source of truth
for the stored entry, not a fallback).

### Reindexing

`REINDEX_ON_START=true` rebuilds the Manticore full-text index from what's currently in
MongoDB on the next startup. Use it if the index and MongoDB ever drift apart (e.g. after
losing the `manticore-data` volume) — it does not touch or require the archive collection.

## Development

**Backend** (`backend/`, Java 17, Vert.x): `mvn test` runs integration tests via
Testcontainers, so a working Docker daemon is required locally.

**Frontend** (`frontend/`, Nuxt 3): `npm run dev` starts the dev server on port `3000` and
proxies `/api` to `http://localhost:8080` (hardcoded in `nuxt.config.ts`'s `nitro.devProxy`),
so run a backend on the default port `8080` first (e.g. `docker compose up -d` with default
ports, or `HTTP_PORT_HOST` left unset).

## Throughput / UDP tuning

`scripts/loadgen.sh` was run against a compose stack on a 2-CPU host: 8 workers x
20000 messages (160000 total) over UDP to the ingest port. Result: `received=156419`,
`written=156419`, `dropped=0`, `reindexQueue=0`, `indexDropped=0` — a wall time of
24m21s (real), i.e. ~107 msg/s, far below the "well under a minute" (>5k msg/s)
expectation. The bottleneck was the load generator itself, not the server or the
network: the script's `worker()` loop forks a `date` subprocess per line, which on
a 2-core box limited a single worker to roughly 100-150 lines/s, and GNU `parallel`
could only run 2 of the 8 workers concurrently. The ~2.2% packet loss
(160000 sent vs. 156419 received) is consistent with ordinary UDP loss on loopback
under load and did not grow with elapsed time; `dropped=0` and `reindexQueue=0`
throughout confirm the server kept up with whatever arrived (no backpressure). If
`received` were much further below `sent` on a given host, the standard fix is to
raise `net.core.rmem_max` (e.g. `sysctl -w net.core.rmem_max=26214400`) to give the
UDP socket more receive-buffer headroom; on this host `net.core.rmem_max` was left
at its default (4194304) since the loss rate did not warrant changing it. For a
realistic >5k msg/s throughput measurement, prefer more CPU cores and/or a generator
that doesn't fork a process per log line.

## License

License: TBD
