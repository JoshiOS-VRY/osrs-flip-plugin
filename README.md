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
| `price` / `quantity` / `quantityFilled` | Offer details |
| `state` | `buying`, `selling`, `cancelled_buy`, `cancelled_sell`, `bought`, `sold` |
| `slot` | GE slot index (0–7) |
| `occurredAt` | UTC timestamp |
| `source` | Always `plugin` |

Duplicate events are deduplicated client-side and server-side via `idempotencyKey`.

## Market tab

The sidebar panel includes: **Connection** (pairing), **Import history** (Flipping Utilities JSON), **My slots** (live GE dashboard), **Session** (GP/hr widget), **GE setup** (item detail while configuring offers), **Recipe flips** (Ultra+), and **Market** (opportunities, slot optimizer, watchlist).

Optional GE overlays (plugin settings, all display-only):

- **Slot stagnation timers** — time since last slot activity
- **GE copilot overlay** — score, margins, insta buy/sell, reprice hints (Ultra)
- **GE price chart overlay** — mini sparkline on offer setup
- **Watchlist GE hint** — type `1` in GE search to see favorites list

Works alongside Flipping Utilities: import FU JSON on the web or in-plugin; FlipX adds cloud analytics and market discovery FU does not provide.

| Route | Purpose |
| ----- | ------- |
| `GET /api/plugin/entitlements` | Tier limits that drive the UI (refresh interval, slots, presets, etc.) |
| `GET /api/plugin/slots/live` | Enriched open GE offers with overbid/undercut badges |
| `GET /api/plugin/session` | Live session GP/hr and flip stats |
| `GET /api/plugin/analytics/items` | Session-scoped item profit breakdown (`from`, `limit`) |
| `POST /api/plugin/import/flipping-utilities` | Import Flipping Utilities JSON history |
| `GET /api/plugin/recipes/opportunities` | Ranked recipe flip margins (Ultra+) |
| `POST /api/plugin/market/query` | Filtered/ranked opportunities + summary + top ROI movers |
| `GET /api/plugin/items/{id}` | Single-item detail (with tier-scoped chart snapshots) |
| `POST /api/plugin/slots/optimize` | Best GP/slot-hour fill for your tier's slots |
| `GET`/`POST /api/plugin/watchlists*` | List and add/remove watchlist items |
| `GET /api/plugin/copilot/item/{id}` · `POST /api/plugin/copilot/items` | GE overlay copilot (Ultra) |

Filtering, presets and score re-ranking run through the same shared `market-query` logic the web app uses, so the plugin and website show identical results for the same account. Polling respects the tier `refreshIntervalMs` (minimum **15 seconds** in-plugin, even on Elite's 5s web interval) and pauses when the Market tab is not visible.

The **GE copilot overlay** (Ultra, off by default) shows the live score and estimated economics for the item in your open Grand Exchange offer. It is display-only.

## Architecture

```
GeEventListener       → listens for GrandExchangeOfferChanged
EventMapper           → maps RuneLite offer → ingest schema
IngestClient          → batches events, POSTs with retry
PairingService        → exchanges 6-digit code for API key
PluginApiClient       → Bearer JSON client for /api/plugin/* routes
OpportunitiesClient   → polls entitlements + market query on tier interval
CopilotClient         → per-item copilot with a short TTL cache
WatchlistClient       → reads/syncs watchlists
FlipFinderPanel       → tabbed sidebar UI (Sync | Market)
MarketPanel           → market summary, presets, list, item detail, slots, watchlist
MarketCopilotOverlay  → display-only GE score/profit overlay (Ultra)
```

Events flush every ~8 seconds or when 50 events are queued.

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

- Only GE offer data for your logged-in account is uploaded.
- No inventory, bank, or chat data is collected.
- Upload requires explicit pairing with a code you generate on the web app.
- Revoke the device at any time from web settings.

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

- [x] Privacy policy URL (`https://flipx.gg/privacy`)
- [x] Opt-in upload disabled by default with IP warning
- [x] No automation disclaimer in panel
- [x] Unofficial / not Jagex-endorsed disclaimer
- [x] BSD-2 LICENSE
- [x] Plugin icon (`icon.png` at repo root + classpath resource)
- [x] `runelite-plugin.properties` with `version` and `build=standard`
- [x] Production API URL hardcoded (`https://flipx.gg`) — not user-editable
- [ ] Public GitHub repo + Plugin Hub PR ([submission guide](./PLUGIN_HUB.md))

## Related

- Web app: [FlipX](https://flipx.gg)
- Ingest API: `POST /api/ingest/ge-events`
- Pairing API: `POST /api/devices/pair`
