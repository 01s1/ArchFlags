# ArchFlags

Detects a player's country from their real Velocity-forwarded IP and exposes it through
PlaceholderAPI, for use **only** in the above-head nametag. ArchFlags never edits TAB, chat,
the player list, scoreboards, bossbars, or titles itself -- it just answers placeholders that
you wire into TAB's own nametag config.

```
[Rank Badge] | PlayerName | [Country Flag]
```

## Requirements

- Paper or Leaf, Minecraft 1.21.11, Java 21
- Velocity 4.x with **modern** player-info forwarding
- A local `GeoLite2-Country.mmdb` (MaxMind account required to download one -- see below)
- Optional but recommended: PlaceholderAPI, LuckPerms, TAB

ArchFlags does **not** create or require its own MySQL/MariaDB database.

## Installation

1. Set up Velocity modern forwarding if you haven't already:
   - Velocity `velocity.toml`: `player-info-forwarding-mode = "modern"` and a shared
     `forwarding-secret`.
   - Every backend Paper server's `config/paper-global.yml`:
     ```yaml
     proxies:
       velocity:
         enabled: true
         online-mode: true
         secret: "<same forwarding secret>"
     ```
   Paper rewrites `Player#getAddress()` to the real client IP for you once this is set --
   ArchFlags does not need (and does not ship) a Velocity-side plugin.
2. Drop `ArchFlags-<version>.jar` into `plugins/` on every backend server (Hub, Survival, SMP, ...).
3. Get a `GeoLite2-Country.mmdb` from MaxMind (a free GeoLite2 account is required:
   <https://www.maxmind.com/en/geolite2/signup>) and place it at
   `plugins/ArchFlags/GeoLite2-Country.mmdb`. Without it, ArchFlags stays up and every
   placeholder falls back to the configured "unknown" values -- it does not error or disable
   itself.
4. Start the server once to generate `plugins/ArchFlags/config.yml` and
   `glyph-mapping.yml`, then review `config.yml` (see below).
5. Merge the generated flag assets into your existing NxRanks resource packs -- see
   "Resource pack / NxRanks merge" below. This step is required for `CUSTOM_GLYPH` mode;
   `UNICODE` and `COUNTRY_CODE` modes work with no resource pack at all.
6. Configure TAB's above-head nametag to include `%archflags_nametag_suffix%` -- see
   "TAB integration" below.

## Placeholders

| Placeholder | Example | Notes |
|---|---|---|
| `%archflags_flag%` | the glyph/emoji/`[SA]` | `""` if the player hid their flag, or if unresolved and `unknown-flag` is blank |
| `%archflags_code%` | `SA` | configured `unknown-code` if unresolved |
| `%archflags_country%` | `Saudi Arabia` | configured `unknown-country` if unresolved |
| `%archflags_visible%` | `true` / `false` | the player's own toggle state |
| `%archflags_nametag_suffix%` | ` \| ` + flag | `""` whenever `%archflags_flag%` is `""`, so no dangling separator is ever shown |

`persist()` is `true` on the expansion, so it survives `/papi reload`.

## TAB integration

ArchFlags does not touch TAB. In TAB's `config.yml`, add the suffix placeholder to the
**above-head nametag** value only -- never to `tablist-name-formatting` or any chat format:

```yaml
# TAB config.yml (illustrative -- keep your existing NxRanks/LuckPerms prefix on the left)
per-world:
  # ...
  above-head:
    tag-format: "%luckperms_prefix%%player%%archflags_nametag_suffix%"
```

The exact key name depends on your TAB version/config layout (`nametag_format`,
`tagprefix`/`tagsuffix`, or the above-head section under `nametags` in newer TAB releases) --
the important part is: put `%archflags_nametag_suffix%` at the very end of the **nametag**
value only, after the existing rank prefix and player name, and do not add any ArchFlags
placeholder to TAB's player-list format.

### MiniMessage font tag

In `CUSTOM_GLYPH` mode, `%archflags_flag%` (and therefore `%archflags_nametag_suffix%`) is
wrapped in a MiniMessage `<font:archflags:flags>...</font>` tag by default
(`glyphs.wrap-minimessage: true` in `config.yml`), so the glyph renders in ArchFlags' custom
font automatically wherever the placeholder is used, as long as the renderer honors MiniMessage
`<font>` tags (TAB's Adventure-based text rendering does, on modern TAB/Paper versions). If your
TAB version doesn't support MiniMessage in the above-head nametag, set
`glyphs.wrap-minimessage: false` and switch `display.mode` to `UNICODE` or `COUNTRY_CODE`
instead, which need no custom font at all.

## Resource pack / NxRanks merge

ArchFlags never sends its own resource pack -- it only generates assets under `output/` for
you to merge into your existing NxRanks Java and Bedrock packs.

**Java** (`output/java/`):
- Copy `output/java/assets/archflags/textures/font/flags/*.png` into your pack at
  `assets/archflags/textures/font/flags/`.
- Copy `output/java/assets/archflags/font/flags.json` into your pack at
  `assets/archflags/font/flags.json` (a **new** namespace/font file -- this does not
  overwrite NxRanks' or NxEmoji's own `assets/<their namespace>/font/*.json`, so nothing of
  theirs is replaced). If you'd rather not add a whole new font namespace, you can instead
  copy the entries from `flags.json`'s `providers` array into the *end* of your existing
  default font's provider list -- just make sure the private-use-area codepoints
  (`glyphs.start-codepoint` in `config.yml`, default `E200`-`E2F9`) don't collide with ranges
  NxRanks/NxEmoji already use in that font.

**Bedrock** (`output/bedrock/`):
- Copy `output/bedrock/textures/font/glyph_E2.png` (or whichever `glyph_EN.png` sheet(s) the
  generator produced) into your Bedrock pack at `textures/font/glyph_E2.png`. If NxRanks/NxEmoji
  already ships a `glyph_E2.png` in that same slot, they collide -- change
  `glyphs.start-codepoint` in `config.yml` to a free sheet, re-run the generator, and merge the
  new sheet file instead.

See [`tools/asset-generator/README.md`](tools/asset-generator/README.md) for the flag source
dataset's license/attribution and generator usage/limitations in full.

## Display modes

```yaml
display:
  mode: CUSTOM_GLYPH        # CUSTOM_GLYPH, UNICODE, or COUNTRY_CODE
  fallback-mode: COUNTRY_CODE
```

- `CUSTOM_GLYPH` -- the generated resource-pack glyphs (needs the merge step above).
- `UNICODE` -- standard regional-indicator flag emoji (🇸🇦). No resource pack needed, but
  rendering depends entirely on the client's own font/emoji support.
- `COUNTRY_CODE` -- plain text like `[SA]`. Always works, needs nothing.

If `CUSTOM_GLYPH` is selected but a country has no mapped glyph (or the mapping file failed to
load), ArchFlags automatically renders `fallback-mode` for that placeholder instead of ever
showing a broken-square tofu glyph.

## Commands & permissions

| Command | Aliases | Permission | Default |
|---|---|---|---|
| `/flags`, `/flags on`, `/flags off`, `/flags toggle` | `/flag` | `archflags.use` | granted to everyone |
| `/archflags reload` | | `archflags.admin` | op |
| `/archflags lookup <player>` | | `archflags.admin` | op |
| `/archflags status` | | `archflags.admin` | op |

`/archflags lookup` never prints a raw IP address -- only the resolved country name/code and
visibility. Offline players who haven't connected recently show as "Unknown" (nothing is
resolvable without a live connection, and ArchFlags never stores a raw IP to look up later).

## LuckPerms-backed visibility

Flag visibility is stored as a plain permission node on the player
(`luckperms.preference-node` in `config.yml`, default `archflags.visible`, `true`/`false`) via
the LuckPerms API -- **not** a new database, not a group/prefix/suffix change. Since your
network's LuckPerms already shares one MySQL backend across Hub/Survival/SMP, this follows the
player everywhere automatically. If LuckPerms isn't installed, ArchFlags still works, but the
choice only persists in memory for that server session (a startup warning is logged).

## ArchFlagsService API (for ArchSettings)

```java
ArchFlagsService service = ArchFlagsService.get(); // or Bukkit.getServicesManager().load(...)

CountryInfo info = service.getCountry(uuid);           // non-blocking, cached
service.resolveCountry(uuid).thenAccept(info -> ...);  // awaits resolution if not cached yet
boolean visible = service.isFlagVisible(uuid);
service.setFlagVisible(uuid, false);
service.toggleFlagVisibility(uuid);
```

Also fires `ArchFlagVisibilityChangeEvent` and `ArchCountryResolvedEvent` (both synchronous,
main-thread) for any plugin to listen to instead of polling.

## Privacy & threading

- The raw IP is read once per connection (from Paper's Velocity-forwarded address), used for a
  single in-memory `.mmdb` lookup, and discarded -- never written to disk, never logged, and
  never included in `/archflags lookup` output.
- The `.mmdb` file is opened off the main thread on enable/reload; every lookup runs on a
  dedicated single-thread executor. `ArchFlagsService.getCountry(uuid)` only ever reads an
  in-memory cache, so it's always safe to call from the main thread.
- Resolved countries are cached per-player in memory and evicted a configurable number of
  minutes after quit (`cache.retain-after-quit-minutes`).

## Building from source

```bash
./gradlew build
```

Produces `build/libs/ArchFlags-<version>.jar` (shaded; relocates the MaxMind DB reader so it
can't collide with another plugin's copy). See `tools/asset-generator/README.md` if you need
to regenerate flag assets (different dimensions, a different start codepoint, or an updated
country list).
