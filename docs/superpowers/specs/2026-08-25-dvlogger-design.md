# dvlogger – design spec

## Context

Minimalista, Graylog-szerű log-platform egyetlen `docker compose up`-pal. Üres repo, zöldmezős.
Elvárt volumen: **>10M sor/nap**, ezért batch-elt ingest és külön keresőindex kell.
Cél: kevés mozgó alkatrész, env-változós konfig, egyszerű dashboard.

## Döntések (a brainstormingból)

| Téma | Döntés |
|---|---|
| Backend | Java 21, Maven, Vert.x 4 (core, web, mongo-client, mysql-client a Manticore-hoz) |
| Frontend | Nuxt 3, `ssr:false`, Tailwind 4, Pinia; `nuxt generate` → statikus fájlok a backend image-ben |
| Perma tár | MongoDB (a "forrás az igazság", teljes rekord) |
| Kereső | Manticore Search (RT index, csak indexelt mezők + message); keresés Manticore-ból, teljes rekord Mongóból ID alapján |
| Ingest | 11222 **TCP + UDP** (compose forward), plusz `POST /api/ingest` a HTTP porton |
| Formátum | `LOG_FORMAT=text\|json\|gelf\|auto` (auto: `{`→JSON, `version`+`short_message`→GELF, egyébként text) |
| Text parse | `<source> [tag1,tag2] message` prefix; ha nincs, source = küldő IP, tag üres. Felülírható `LOG_TEXT_PATTERN` (named groups: source, tags, message) |
| Pipeline | Receiver verticle-k → EventBus → Writer verticle batch-el (500 db / 200 ms) → `insertMany` Mongo + bulk `INSERT` Manticore |
| Retenció | `RETENTION_DAYS` – Mongo TTL index + periodikus Manticore `DELETE WHERE ts < …` |
| Auth | `AUTH_USER` / `AUTH_PASSWORD`, `POST /api/login` → HttpOnly session cookie (Vert.x SessionHandler, LocalSessionStore) |
| Dashboard | Szűrők: dátum (from/to **vagy** "utolsó 5/15/60 perc" gyorsválasztó), szöveg, source, tag, level. Auto-refresh "every X s" kliens oldalon (polling, nincs WS). Kattintásra oldalsó részlet-panel a raw rekorddal. |
| Archive mode | `ARCHIVE_ENABLED=true`: a Writer a batch-et a `logs` mellett a `logs_archive` collectionbe is beszúrja (ugyanaz a dokumentum). Az archívon **nincs TTL**, a retenció nem érinti. Böngészés a dashboardon külön "Archive" nézetben, **direkt Mongóból** (nincs Manticore), ugyanazokkal a szűrőkkel. |

## Repo felépítés

```
dvlogger/
  docker-compose.yml          # app (backend+frontend), mongo, manticore
  .env.example
  backend/                    # Maven, Vert.x
    Dockerfile                # multi-stage: node build (frontend) + maven build + JRE runtime
    src/main/java/hu/borat/dvlogger/
      Main.java               # deploy verticle-k, Config betöltés
      Config.java             # env → record
      model/LogEntry.java     # id, ts, source, tags[], level, message, raw(JSON), host
      ingest/
        TcpReceiverVerticle.java   # newline/NUL delimitelt
        UdpReceiverVerticle.java   # GELF chunk reassembly is itt
        HttpIngestHandler.java     # POST /api/ingest (1 vagy több sor / JSON tömb)
        parser/LogParser.java      # interface: parse(String raw, String remoteIp) -> LogEntry
        parser/TextParser.java, JsonParser.java, GelfParser.java, AutoParser.java
      store/
        WriterVerticle.java        # EventBus "log.in" consumer, batch, dual-write
        MongoStore.java            # insertMany, findByIds, TTL index létrehozás
        ArchiveStore.java          # logs_archive: insertMany, search(filters,cursor), distinct source/tag; csak ARCHIVE_ENABLED esetén
        ManticoreIndex.java        # CREATE TABLE ha nincs, bulk INSERT, search(), deleteBefore()
        RetentionVerticle.java     # napi Manticore törlés
      api/
        ApiVerticle.java           # Router: /api/login, /api/logout, /api/logs, /api/sources, /api/tags, /api/ingest, static
        AuthHandler.java
        SearchHandler.java         # query paramok → Manticore → Mongo hydrate → JSON
        ArchiveSearchHandler.java  # ugyanazok a paramok → ArchiveStore (direkt Mongo)
  frontend/                   # Nuxt 3 SPA
    nuxt.config.ts (ssr:false, tailwind), app.vue
    pages/login.vue, pages/index.vue, pages/archive.vue   # archive: ugyanaz a FilterBar/LogTable, csak a /api/archive/* endpointot hívja; menüben csak ha az /api/health szerint ARCHIVE_ENABLED
    stores/auth.ts, stores/logs.ts (pinia)
    components/FilterBar.vue, LogTable.vue, LogDetailPanel.vue, AutoRefresh.vue
    middleware/auth.global.ts
```

## Adatmodell

**Mongo `logs`**: `{ _id: ObjectId, ts: Date, source, tags: [], level: int|null, message, host, raw: {} }`
Indexek: `{ts:1}` (TTL `expireAfterSeconds` = RETENTION_DAYS*86400), `{source:1, ts:-1}`.

**Mongo `logs_archive`** (csak ha `ARCHIVE_ENABLED`): azonos séma, azonos `_id`. Indexek: `{ts:-1}`, `{source:1, ts:-1}`, `{tags:1, ts:-1}`, `{level:1}`, **text index** `{message:"text"}` – nincs TTL. A szövegszűrő `$text` (szó-alapú, gyors); ha a user idézőjelben ad meg mintát, `$regex` fallback (lassabb, de substring).

**Manticore `logs`** (RT table): `id bigint` (Manticore autoinc), `mongo_id string attribute` (ObjectId hex), `ts timestamp`, `source string attribute`, `tags json`, `level uint` (null → 0), `message text` (full-text). Keresés: `MATCH(q)` + attribútum-szűrők, `ORDER BY ts DESC`. `FACET source` / tags aggregálás adja a source/tag listát.

## API

- `POST /api/login {user,password}` → 204 + cookie; `POST /api/logout`
- `GET /api/logs?q=&from=&to=&source=&tag=&level=&limit=100&before=<cursor>` → `{items:[LogEntry], next}` – Manticore keres (ts desc), ID-k → Mongo hydrate
- `GET /api/sources`, `GET /api/tags` – Manticore FACET az utolsó RETENTION napból
- `GET /api/archive/logs?…` – ugyanazok a paraméterek mint `/api/logs`, de Mongo `logs_archive` lekérdezés (`find` + indexelt szűrők + `$text`), cursor = `_id`. `GET /api/archive/sources`, `/api/archive/tags` – Mongo `distinct`. 404 ha az archív nincs bekapcsolva.
- `POST /api/ingest` – body: egy sor / JSON / JSON tömb; ugyanaz a parser-lánc, auth **nélkül** (opcionális `INGEST_TOKEN` header, ha be van állítva)

## Config (env)

`AUTH_USER, AUTH_PASSWORD, ARCHIVE_ENABLED=false, LOG_FORMAT=auto, LOG_TEXT_PATTERN, RETENTION_DAYS=14, MONGO_URL, MANTICORE_HOST, MANTICORE_PORT=9306, HTTP_PORT=8080, INGEST_PORT=11222, INGEST_TOKEN(opc), BATCH_SIZE=500, BATCH_MS=200`

## Hibakezelés

- Parse hiba → `level=null`, `message=raw`, `tags=["_unparsed"]`; nem dobjuk el.
- Mongo write hiba → retry 3x, utána log + drop (metrika számláló `/api/health`-ben).
- Manticore write hiba → Mongo már megvan; a batch ID-jait egy in-memory "reindex queue"-ba tesszük és újrapróbáljuk. Induláskor opcionális `REINDEX_ON_START=true` teljes újraépítés Mongóból.
- Backpressure: EventBus consumer `maxBufferedMessages`; TCP socket `pause()` ha a writer túl lassú.

## Tesztelés / verifikáció

- Unit: parserek (text prefix, JSON, GELF, auto-detekt, GELF chunk reassembly).
- Integration (Testcontainers: mongo + manticore): batch write → search visszaadja; retenció törlés; `ARCHIVE_ENABLED` esetén a rekord `logs_archive`-ban is megvan, és a retenció után **csak** ott; archív keresés szűrőkkel.
- E2E kézzel: `docker compose up`, `echo "app1 [web,prod] hello" | nc localhost 11222`, `curl -X POST :8080/api/ingest -d '{"short_message":"x","version":"1.1","host":"h"}'`, login a UI-n, szűrők, auto-refresh, részlet-panel.
- Terhelés: kis `loadgen` script (bash/parallel) 5k msg/s UDP-n, ellenőrizni, hogy a batch tartja és nincs drop.

## Következő lépés

Jóváhagyás után: spec fájl `docs/superpowers/specs/2026-08-25-dvlogger-design.md`, majd `writing-plans` skill az implementációs tervhez (fázisok: 1. skeleton+compose, 2. ingest+parserek, 3. store+search, 4. API+auth, 5. frontend, 6. retenció+loadtest).
