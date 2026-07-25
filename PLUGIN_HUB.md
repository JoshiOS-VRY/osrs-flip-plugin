# FlipX — RuneLite Plugin Hub submission

This document is the checklist for listing FlipX in the [official RuneLite Plugin Hub](https://github.com/runelite/plugin-hub) (the third-party plugin marketplace built into RuneLite).

## Prerequisites

- [x] **Public GitHub repository** — Plugin Hub only accepts public repos. `JoshiOS-VRY/osrs-flip-plugin` is public.
- [ ] **Production API live** — Default base URL is `https://www.flipx.gg` (the apex `flipx.gg` is normalized to `www` by the plugin). DNS must resolve before reviewers or users can pair in production.
- [ ] **Privacy policy** — Published at [https://www.flipx.gg/privacy](https://www.flipx.gg/privacy) (covers plugin data collection).
- [ ] **CI green** — `./gradlew test shadowJar` passes on `main`/`master`.

## Repository checklist (already done in this repo)

| Requirement | Status |
| ----------- | ------ |
| `runelite-plugin.properties` with `displayName`, `author`, `description`, `tags`, `plugins`, `version`, `build=standard` | Done |
| `icon.png` at repo root (≤ 48×72 px) | Done — `icon.png` |
| Runtime icon at `src/main/resources/com/osrsflipfinder/runelite/icon.png` | Done |
| BSD-2 `LICENSE` | Done |
| No trading automation — display-only overlays, manual GE actions only | Done |
| Opt-in network features with RuneLite IP warnings | Done (`enableUpload`, market, overlays) |
| No extra runtime dependencies beyond RuneLite (`compileOnly` client) | Done — standard build, no third-party verification needed |
| README with features, privacy, pairing instructions | Done |

## Jagex / RuneLite compliance notes

Reviewers verify the plugin against [Jagex third-party client guidelines](https://secure.runescape.com/m=news/third-party-client-guidelines?oldschool=1) and RuneLite's [rejected features list](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features).

FlipX is designed to pass:

- **No automation** — Does not place offers, click GE buttons, or inject prices.
- **Opt-in data upload** — GE sync disabled by default; user pairs with a web-generated code.
- **Display-only overlays** — Copilot, charts, stagnation timers, and watchlist hints are read-only.
- **Third-party server disclosed** — Config warnings on every feature that calls FlipX APIs.

## Submit to Plugin Hub (one-time)

### 1. Tag a release commit (recommended)

After merging production-ready changes to `main`:

```bash
git tag -a v1.0.0 -m "FlipX RuneLite plugin 1.0.0"
git push origin v1.0.0
```

Plugin Hub pins a **commit hash**, not a Git tag — but tagging makes releases easy to reference.

### 2. Fork plugin-hub

1. Fork [https://github.com/runelite/plugin-hub](https://github.com/runelite/plugin-hub).
2. Clone your fork locally.

### 3. Add the manifest file

Create `plugins/flipx` (filename = plugin id, lowercase, no spaces):

```
repository=https://github.com/JoshiOS-VRY/osrs-flip-plugin.git
commit=FULL_40_CHARACTER_COMMIT_SHA
```

Replace:

- `JoshiOS-VRY/osrs-flip-plugin` with the actual public repo URL.
- `commit=` with the **full** SHA of the commit you want users to receive (usually latest on `main` after CI passes).

Example:

```
repository=https://github.com/JoshiOS-VRY/osrs-flip-plugin.git
commit=abc123def4567890abc123def4567890abc123de
```

### 4. Open a pull request

1. Branch: `flipx` (or similar).
2. Push only the new `plugins/flipx` file.
3. Open a PR against `runelite/plugin-hub` **master**.
4. PR description should include:
   - What the plugin does (GE sync + market browsing).
   - Link to privacy policy: https://flipx.gg/privacy
   - Confirmation that upload and all network features are opt-in.
   - Confirmation there is no automation.

### 5. CI and review

The PR runs two checks:

| Check | If it fails |
| ----- | ----------- |
| **build** (`build.yml`) | Click *Details* — usually compile errors or bad `commit=` hash. Fix in plugin repo, push, update manifest `commit=`. |
| **RuneLite Plugin Hub Checks** | Automated policy scan; address any "Changes are needed" comments. |

Review is manual and can take days to weeks. Keep all fixes in the **same PR** (update `commit=` as you push fixes).

### 6. After merge

- Plugin appears in RuneLite under **Configuration → Plugin Hub** after the next hub deploy.
- Users search **FlipX** or tags like `flipping`, `grand exchange`.
- **No sideloading required** — RuneLite downloads and updates the plugin automatically.

## Updating after launch

For each plugin release:

1. Merge changes to the plugin repo `main`.
2. Note the new commit SHA.
3. Open a PR to `runelite/plugin-hub` updating only `commit=` in `plugins/flipx`.

See [plugin-hub updating docs](https://github.com/runelite/plugin-hub#updating-a-plugin).

## Sideloading (before Hub approval)

Until the Hub PR merges, distribute the shadow JAR for beta testers:

```bash
./gradlew shadowJar
# build/libs/flipx-plugin-1.0.0-all.jar
```

Users need **Developer mode** in RuneLite and either `./gradlew installSideloaded` or **Developer → Load local plugin**.

## Troubleshooting review

| Review concern | Response |
| -------------- | -------- |
| External server / privacy | Point to https://flipx.gg/privacy; all network features opt-in with config warnings |
| Automation | Plugin only observes GE events and shows overlays; no input simulation |
| Malicious code | Open source; no obfuscation; standard `build=standard` with no extra deps |
| Domain not reachable | Wait for DNS propagation on `flipx.gg` before resubmitting |

## Useful links

- [Plugin Hub README](https://github.com/runelite/plugin-hub/blob/master/README.md)
- [Example plugin template](https://github.com/runelite/example-plugin)
- [RuneLite Discord](https://discord.gg/runelite) — `#plugin-hub` for questions
- [FlipX web app](https://flipx.gg)
