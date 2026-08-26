# dvlogger

> **⚠️ Vibecoded project — use at your own risk.**
> dvlogger was generated end-to-end by an AI coding agent (Claude) from a design spec, via an
> automated plan → implement → review loop. Verification so far consists of automated tests and
> AI code reviews — there has been **no line-by-line human audit**. Review the code yourself
> before relying on it, and especially before any production use.

dvlogger ("dev logger") is a minimal, Graylog-like log platform for development environments:

- **Ingest** free-text, JSON, or GELF log lines over TCP, UDP, or HTTP on a single port (`11222`).
- **Store** every entry in MongoDB, with an optional permanent archive collection.
- **Search** full text via Manticore Search, with a MongoDB fallback for exact document lookup.
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
| `AUTH_PASSWORD` | `change-me` | Dashboard/API login password — **change this** |
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
retention. There is no dedicated archive UI: browse it directly in MongoDB (or via the
archive search API), independently of `RETENTION_DAYS`, which only governs the primary,
Manticore-indexed collection.

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

## License

License: TBD
