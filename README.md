# FlipX — RuneLite Plugin

RuneLite companion plugin for [FlipX](https://flipx.gg). It does two things once paired:

1. **Portfolio sync** — observes Grand Exchange offer changes in-game and syncs them to the web app for portfolio analytics, beat-the-market stats, and flip matching.
2. **Market browsing** — brings the web app's discovery workflow into RuneLite: tier-scoped opportunities, quick presets, search, item detail, a slot optimizer, watchlist sync, and a display-only Grand Exchange copilot overlay.

**This plugin does not automate trading.** It only uploads GE offer activity you already perform manually, and the Market features are read-only — no price injection, offer auto-fill, or click simulation.

## Prerequisites

- **Java 11+** (JDK)
- **Gradle** (wrapper included — `./gradlew`)
- [RuneLite](https://runelite.net/) with **Developer Mode** enabled (for sideloading)
- FlipX web app running locally or deployed, with Supabase configured
- **Pro subscription** (or `DEV_MODE=true` on the web app for local testing)

## Build

```bash
./gradlew build
./gradlew shadowJar
```

Output JAR: `build/libs/flipx-plugin-1.0.0-all.jar`

Run embedded client for development:

```bash
./gradlew run
# or
./run-dev.sh
```

## Sideload (local testing)

### Option A — automated install (recommended)

```bash
./gradlew installSideloaded
```

Copies `build/libs/flipx-plugin-1.0.0-all.jar` to `~/.runelite/sideloaded-plugins/`. Restart RuneLite with **Developer mode** enabled; the plugin loads automatically.

> **Note:** `./run-dev.sh` / `./gradlew run` load the plugin from source. If a sideloaded JAR is present, you get duplicate sidebar icons — `run-dev.sh` moves the sideload JAR aside automatically.

### Option B — manual load

1. `./gradlew shadowJar`
2. Open RuneLite → **Settings** → enable **Developer mode**.
3. **Developer** → **Load local plugin** → select `build/libs/flipx-plugin-1.0.0-all.jar`.
4. Enable **FlipX** in the plugin list.

## Pairing

1. Log in to the web app and open **Settings → RuneLite plugin devices**.
2. Click **Generate pairing code** (Pro required).
3. In RuneLite, open the **FlipX** sidebar panel.
4. Enter the 6-digit code and click **Connect**.
5. The plugin stores an API key locally — it is shown only once during pairing.

To revoke access, use **Disconnect** in the plugin or **Revoke** on the web settings page.

## What gets synced

On each GE offer change, the plugin sends a normalized event to:

```http
POST https://flipx.gg/api/ingest/ge-events
Authorization: Bearer <apiKey>
```

Each event includes:

| Field | Description |
| ----- | ----------- |
| `accountHash` | Jagex account hash from RuneLite (not your RSN) |
| `itemId` / `itemName` | Item being traded |
| `side` | `buy` or `sell` |
| `price` / `quantity` / `quantityFilled` | **`price` is the average fill per item** (`getSpent() / getQuantitySold()` when filled), not the limit you type in the GE form. Open offers with zero fill use the limit price. |
| `state` | `buying`, `selling`, `cancelled_buy`, `cancelled_sell`, `bought`, `sold` |
| `slot` | GE slot index (0–7) |
| `occurredAt` | UTC timestamp |
| `source` | Always `plugin` |

Duplicate events are deduplicated client-side and server-side via `idempotencyKey`.

## Market tab

The sidebar panel includes: **Connection** (pairing), **Import history** (Flipping Utilities JSON), **My slots** (live GE dashboard), **Session** (GP/hr widget), **GE setup** (item detail while configuring offers), **Recipe flips** (Ultra+), and **Market** (opportunities, slot optimizer, watchlist).

Optional GE features (plugin settings):

- **Slot stagnation timers** — time since last slot activity
- **GE copilot overlay** — score, margins, insta buy/sell, reprice hints (Pro)
- **GE slot tooltips** — hover a slot tab on the main GE grid for FlipX score, margins, and reprice hints (display-only; on by default)
- **GE slot highlights** — colored borders when an offer needs attention
- **FlipX GE price buttons** — clickable FlipX buy/sell in the GE price chatbox (Pro; you still confirm)
- **Watchlist GE hint** — type `1` in GE search to see favorites list

Works alongside Flipping Utilities: import FU JSON on the web or in-plugin; FlipX adds cloud analytics and market discovery FU does not provide.

| Route | Purpose |
| ----- | ------- |
| `GET /api/plugin/entitlements` | Tier limits that drive the UI (refresh interval, slots, presets, etc.) |
| `GET /api/plugin/slots/live` | Enriched open GE offers with overbid/undercut badges |
| `GET /api/plugin/session` | Portfolio stats for a time period (`period`: `session`, `1d`, `1w`, `1m`, `1y`, `all`; also `liveSession`, `from`, `to`, `days` as on web). Session includes live slot P&amp;L; other periods are completed flips only. |
| `GET /api/plugin/analytics/items` | Item profit breakdown for the same period query params as session (`from`, `to`, `liveSession`, `limit`) |
| `POST /api/plugin/import/flipping-utilities` | Import Flipping Utilities JSON history |
| `GET /api/plugin/recipes/opportunities` | Ranked recipe flip margins (Ultra+) |
| `POST /api/plugin/market/query` | Filtered/ranked opportunities + summary + top ROI movers |
| `GET /api/plugin/items/search` | Item catalog search (paired Free + Pro) |
| `GET /api/plugin/items/{id}` | Single-item detail (with tier-scoped chart snapshots) |
| `POST /api/plugin/slots/optimize` | Best GP/slot-hour fill for your tier's slots (Pro+) |
| `GET`/`POST /api/plugin/watchlists*` | List and add/remove watchlist items |
| `GET`/`POST`/`DELETE /api/plugin/bookmarks*` | Saved filter presets synced with the web app |
| `POST /api/ingest/ge-events` · `POST /api/ingest/ge-reconcile` | Offer upload and slot reconciliation |

The GE copilot overlay reads item detail through `GET /api/plugin/items/{id}` rather than a
dedicated copilot route.

Filtering, presets and score re-ranking run through the same shared `market-query` logic the web app uses, so the plugin and website show identical results for the same account. Market polling uses the same phase-aligned schedule as the web app (`nextPublishInMs` + tier `publishLeadMs`, with tier `refreshIntervalMs` as fallback). The sidebar shows **Next refresh in Ns** (no fixed “every Xs” label). Polling pauses when the Market tab is not visible.

The **GE copilot overlay** (off by default) shows the live score and estimated economics for the item in your open Grand Exchange offer when your plan includes copilot API access. It is display-only.

## Architecture

```
GeEventListener       → listens for GrandExchangeOfferChanged
EventMapper           → maps RuneLite offer → ingest schema
IngestClient          → batches events, POSTs with retry
PairingService        → exchanges 6-digit code for API key
PluginApiClient       → Bearer JSON client for /api/plugin/* routes
OpportunitiesClient   → polls `/api/plugin/entitlements` every **20s** while paired; market query on wiki-aligned interval when Market tab is open
CopilotClient         → per-item copilot with a short TTL cache
WatchlistClient       → reads/syncs watchlists
FlipFinderPanel       → tabbed sidebar UI (Sync | Market)
MarketPanel           → market summary, presets, list, item detail, slots, watchlist
MarketCopilotOverlay  → display-only GE score/profit overlay (Ultra)
```

Events flush every ~8 seconds or when 50 events are queued.

**P&amp;L and prices:** Ingest sends executed unit price (`spent / qty filled`), not the GE limit price. The **Session** tab reflects that via the API. **My slots** compares your limit to FlipX estimates and shows fill average when partial fills differ; slot alerts use fill-aware net (2% GE tax, same exemptions as the web app).

## Error handling

| Response | Plugin behavior |
| -------- | --------------- |
| `401` | Clears API key; prompts re-pair |
| `403 upgrade_required` | Shows Pro subscription required |
| Network error | Retries with events kept in queue |

## API contract verification (without RuneLite)

With the web app running:

```bash
# Invalid key → 401
curl -s -X POST http://localhost:3000/api/ingest/ge-events \
  -H "Authorization: Bearer invalid" \
  -H "Content-Type: application/json" \
  -d '{"events":[{"idempotencyKey":"k","accountHash":"1","itemId":995,"side":"buy","price":1,"quantity":1,"quantityFilled":0,"state":"buying","occurredAt":"2026-07-21T01:00:00.000Z","source":"plugin"}]}'

# Invalid payload → 400
curl -s -X POST http://localhost:3000/api/ingest/ge-events \
  -H "Authorization: Bearer invalid" \
  -H "Content-Type: application/json" \
  -d '{"events":[{"bad":"payload"}]}'
```

Full end-to-end test: pair via UI, place GE offers in-game, confirm rows in Supabase `ge_offer_events` and `/portfolio`.

## Privacy

- Network features (GE upload, market, overlays, imports) are opt-in and call FlipX at `https://www.flipx.gg` — see plugin config warnings and [flipx.gg/privacy](https://www.flipx.gg/privacy).
- When **GE upload** is enabled and you are paired, the plugin sends GE offer events for your logged-in account (and may backfill recent trades from RuneLite’s saved GE history for that profile).
- Optional **Flipping Utilities** import (web or in-plugin file picker) uploads export files you choose.
- **Inventory coins** are read locally only to pre-fill market filters; they are not uploaded.
- No bank, chat, or full inventory data is collected.
- Pairing requires enabling **GE upload** or **Market panel** first, then a code from the web app.
- Revoke the device at any time from web settings or **Disconnect** in the plugin.

## Troubleshooting

| Issue | Fix |
| ----- | --- |
| Pairing fails | Confirm Pro subscription and code not expired (10 min) |
| Sync error retrying | Check network/firewall; plugin backs off automatically |
| Pro required (403) | Upgrade at web app `/pricing` |
| Invalid API key (401) | Disconnect and re-pair with a fresh code |
| No events syncing | Enable **Enable GE upload** in plugin settings (off by default) |

Build output: `build/libs/flipx-plugin-1.0.0-all.jar`

## Production release

1. Ensure CI passes: `./gradlew test shadowJar`
2. Tag: `git tag -a v1.0.0 -m "FlipX RuneLite plugin 1.0.0" && git push origin v1.0.0`
3. GitHub Actions attaches the shadow JAR to the release (for sideload beta testers)
4. Submit to Plugin Hub — see **[PLUGIN_HUB.md](./PLUGIN_HUB.md)**

## Plugin Hub readiness

- [x] Privacy policy URL (`https://www.flipx.gg/privacy`)
- [x] Opt-in upload disabled by default with IP warning
- [x] No automation disclaimer in panel
- [x] Unofficial / not Jagex-endorsed disclaimer
- [x] BSD-2 LICENSE
- [x] Plugin icon (`icon.png` at repo root + classpath resource)
- [x] `runelite-plugin.properties` with `version` and `build=standard`
- [x] Production API URL hardcoded (`https://www.flipx.gg`) — not user-editable
- [x] Public GitHub repo (`JoshiOS-VRY/osrs-flip-plugin`)
- [ ] Plugin Hub PR ([submission guide](./PLUGIN_HUB.md))

## Related

- Web app: [FlipX](https://flipx.gg)
- Ingest API: `POST /api/ingest/ge-events`
- Pairing API: `POST /api/devices/pair`
