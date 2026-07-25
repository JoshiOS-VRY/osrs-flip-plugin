# FlipX RuneLite plugin — design system

Authoritative UI spec for the FlipX sidebar. Grounded in the official RuneLite client APIs ([PluginPanel](https://static.runelite.net/runelite-client/apidocs/net/runelite/client/ui/PluginPanel.html), [ColorScheme](https://static.runelite.net/runelite-client/apidocs/net/runelite/client/ui/ColorScheme.html), [FontManager](https://static.runelite.net/runelite-client/apidocs/net/runelite/client/ui/FontManager.html), [SwingUtil](https://static.runelite.net/runelite-client/apidocs/net/runelite/client/util/SwingUtil.html)).

Implementation lives in `PluginUi` and `SidebarContentPanel`. **Do not** introduce one-off colors, fonts, or panel widths in feature panels.

---

## 1. Canvas & scroll (non‑negotiable)

| Token | Source | Value / rule |
|--------|--------|----------------|
| Content width | `PluginPanel.PANEL_WIDTH` | **225px** — every `SidebarContentPanel` |
| Scroll gutter | `PluginPanel.SCROLLBAR_WIDTH` | **17px** — reserved by wrapped `PluginPanel`; never pad for it manually |
| Outer inset | `PluginPanel.BORDER_OFFSET` | Use as `PluginUi.PADDING` / `SPACING_XL` |
| Scroll ownership | `PluginPanel` default `wrap=true` | **No nested `JScrollPane`** in sidebar content |
| Scrollbar LAF | `RuneLiteScrollBarUI` | Do not set scrollbar width; after `updateComponentTreeUI()`, call `reapplyScrollBarLaf()` |

**Layout discipline**

- Prefer `BoxLayout` (Y_AXIS) for stacks; `BorderLayout.NORTH` for section hosts that must not stretch (`FlipFinderPanel`, card stacks).
- Never use `GridLayout` on views that can grow with viewport height unless every row has a **fixed height** (`PluginUi.lockRowHeight`).
- Never set `maximumSize.height = preferredSize.height` on containers that should grow with content.

---

## 2. Spacing scale (4px grid)

Use `PluginUi` spacing constants only:

| Token | px | Use |
|--------|-----|-----|
| `SPACING_XS` | 4 | Between list rows, tight inline gaps |
| `SPACING_SM` | 6 | Between related controls in a form |
| `SPACING_MD` | 8 | Between form groups, card sections |
| `SPACING_LG` | 12 | Before major sections |
| `SPACING_XL` | `PADDING` | Section rules, card padding, header/footer |

Helpers: `PluginUi.gap(n)`, `PluginUi.verticalStack(...)`, `PluginUi.styleCheckBox(...)`.

---

## 3. Color

**Base:** `ColorScheme` only for backgrounds, borders, text hierarchy.

| Role | API |
|------|-----|
| Panel background | `ColorScheme.DARK_GRAY_COLOR` |
| Card / input background | `ColorScheme.DARKER_GRAY_COLOR` |
| Border / divider | `ColorScheme.MEDIUM_GRAY_COLOR` |
| Row hover | `ColorScheme.DARK_GRAY_HOVER_COLOR` |
| Muted text | `ColorScheme.LIGHT_GRAY_COLOR` |
| Section accent | `ColorScheme.BRAND_ORANGE` (collapsible headers, RuneLite-native) |
| Primary text | `Color.WHITE` |

**FlipX accent (sparingly):**

| Token | Use |
|--------|-----|
| `PluginUi.GOLD` | Profit, primary CTA text, active nav |
| `PluginUi.GOLD_DIM` | Secondary links, grid top rule |
| `PluginUi.POSITIVE` / `NEGATIVE` / `WARNING` | `ColorScheme.PROGRESS_*` — status, P&amp;L |

Do not add new accent colors without updating this doc.

---

## 4. Typography

All text via `FontManager` — RuneScape bitmap fonts (**ASCII only** in labels; no Unicode arrows/emojis).

| Role | Font | Color |
|------|------|--------|
| App title | `getRunescapeBoldFont()` ~16pt | White |
| Section title | `getRunescapeBoldFont()` | `BRAND_ORANGE` |
| Body / values | `getRunescapeSmallFont()` | White or light gray |
| Field label | `getRunescapeSmallFont()` | `LIGHT_GRAY_COLOR` |
| Hints / captions | `getRunescapeSmallFont()` | `#999` via `wrappedCaption` |

Long copy: `PluginUi.wrappedCaption()` / `cardHint()` with explicit wrap width (`CONTENT_WIDTH - padding`).

---

## 5. Components

### 5.1 Fields

- Pattern: `PluginUi.labeledField(label, control)` — label above, full width.
- Inputs: `textField`, `passwordField`, `styleCombo` — darker fill, `MEDIUM_GRAY` border, 5×6px inner padding.
- Full width: `PluginUi.fullWidth(field)` on combos and text fields in forms.

### 5.2 Buttons

- `SwingUtil.removeButtonDecorations` on all buttons (see `PluginUi.styleButton`).
- **Primary:** gold text, bordered (`primaryButton`).
- **Secondary:** white text (`secondaryButton`).
- **Link:** small font, gray → gold on hover (`linkButton`, `externalLinkButton`).
- **Back:** left-aligned gold text, no border (`backButton`).

### 5.3 Cards & sections

- **Card:** `PluginUi.card()` — darker fill, 1px border, `PADDING` inset. Use for grouped controls (market browse bar, connection form).
- **Section rule:** `sectionHeader(title)` or collapsible header — bottom `MEDIUM_GRAY` matte border + `SPACING_XL` vertical padding.
- **Collapsible:** `collapsibleSection(title, body, startOpen)` — ASCII `>` / `v` toggles only.

### 5.4 Lists

- **Row height:** fixed via `lockRowHeight` (market row ~52px, watchlist ~40px).
- **Row chrome:** left accent bar (score color), 6–8px padding, hover → `DARK_GRAY_HOVER`.
- **List gap:** `SPACING_XS` between rows in `listContainer`.

### 5.5 Checkboxes (high-contrast)

Use **`PluginUi.checkBox`** only. FlatLaf defaults on dark plugin panels produce **invisible** checkmarks — FlipX ships **custom gold check icons** via `styleCheckBox`.

| Do | Don't |
|----|--------|
| `checkBox` / `styleCheckBox` / `checkboxGroup` | Raw `JCheckBox` with LAF icons only |
| `nestedCheckbox` for rows under fields (inventory coins) | Ad-hoc `indented` px that misaligns the icon column |
| `contentAreaFilled=false`, `setOpaque(false)` | `setOpaque(true)` + dark `setBackground` on the box |

Grouped quality filters: `checkboxGroup(...)` (left rule + padding).  
Under-field toggles: `nestedCheckbox` or `indented` (uses `CHECKBOX_LABEL_INDENT` for checkboxes).

### 5.6 Status

- `statusBadge(PluginState)` — bordered pill, semantic color.
- Inline status: `caption` / `loadingCaption` / `errorLabel`.

---

## 6. Page patterns

### Shell (`FlipFinderPanel`)

1. Header (title, version subtitle, status badge).
2. `SPACING_SM` → View combo (full width).
3. `SPACING_MD` → `SectionContentHost` (single section visible).

### Market list

1. **Browse card** (card): preset → saved views → sort + desc → search.
2. **Filters** (collapsible, default closed): numeric filters + quality checkboxes.
3. **Actions:** secondary button row (slot optimizer / watchlist).
4. **Status** caption + opportunity list.

### Item detail / slots

- Back button → hero grid or summary strip → scrollable detail blocks (`statBlock`).

---

## 7. Accessibility & UX

- Tooltips on non-obvious filters and sort (preset vs custom sort).
- Disable controls when entitlements or preset mode forbids them (don’t hide without explanation — use `advancedHint`).
- Display-only: never imply automation; copy matches web “risk context, not predictions” where relevant.

---

## 8. Checklist (PR / agent)

- [ ] No width other than `PluginPanel.PANEL_WIDTH` for the outer column; children use `INNER_WIDTH` after `pageInsets()`
- [ ] No nested scroll panes
- [ ] Spacing from `PluginUi.SPACING_*` or `gap()`, not magic numbers (except fixed row heights)
- [ ] Colors from `ColorScheme` + `PluginUi` accents
- [ ] Fonts from `FontManager` only
- [ ] Buttons use `SwingUtil.removeButtonDecorations`
- [ ] New sidebar UI composed via `PluginUi` helpers, not raw `JPanel` styling
- [ ] Checkboxes via `PluginUi.checkBox` only (`styleCheckBox` — never opaque dark fill on `JCheckBox`)
- [ ] ASCII-only visible strings in labels

---

## 10. Layout failures (never ship)

These caused visible overlap/clipping in production — forbidden patterns:

| Symptom | Cause | Fix |
|---------|--------|-----|
| Text stacked on text | `lockWidthFixed` / `lockRowHeight` on rows whose **line count varies** (GE slot cards) | `SidebarContentPanel.lockWidth` only + `wrappedBody` / `stackLine` |
| Right-edge cutoff | `maxWidth = PANEL_WIDTH` while parent has **no** `pageInsets()` | Apply `PluginUi.pageInsets()` on shell; constrain children to `INNER_WIDTH` |
| Title flush to corner | Missing page insets on `FlipFinderPanel` layout shell | `layoutPanel.setBorder(PluginUi.pageInsets())` |
| Orange/grey labels on one line | `BorderLayout` with 3+ labels + button in one row (saved views bar) | Vertical stack or `BoxLayout` X_AXIS + horizontal glue |
| Long status clipped | Plain `JLabel` single-line for copy wider than column | `setMultilineCaption` or `wrappedCaption` |
| Hero grid labels clip | `statCell` used `lockWidth` (full column) inside 2-column `GridLayout` | `heroGridCellWidth`, HTML-wrapped captions/values, compact GP in hero |

**Width rule:** `CONTENT_WIDTH` = full column (225px). **`INNER_WIDTH`** = drawable width after insets — use for all `lockWidth`, wraps, and combos.

**Height rule:** Fixed height only when the row layout is **constant** (e.g. two-line opportunity row). Variable guidance blocks must grow with content.

---

## 11. References

- [RuneLite Client API overview](https://static.runelite.net/runelite-client/apidocs/)
- [PluginPanel](https://static.runelite.net/runelite-client/apidocs/net/runelite/client/ui/PluginPanel.html)
- [ColorScheme](https://static.runelite.net/runelite-client/apidocs/net/runelite/client/ui/ColorScheme.html)
- [FontManager](https://static.runelite.net/runelite-client/apidocs/net/runelite/client/ui/FontManager.html)
- Internal: `AGENTS.md` (threading, config, hub rules)
