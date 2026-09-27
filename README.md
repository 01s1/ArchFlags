# ArchFlags

Detects a player's country from their real Velocity-forwarded IP and exposes it through
PlaceholderAPI, for use **only** in the above-head nametag. ArchFlags never edits TAB, chat,
the player list, scoreboards, bossbars, or titles itself -- it just answers placeholders that
you wire into TAB's own nametag config.

```
[Rank Badge] | PlayerName | [Country Flag]
```

ArchFlags is fully **independent of NxRanks**: its own glyph codepoint range (`E200`-`E2F9`,
never `E9xx`), its own Java resource pack (sent additively, never merged into NxRanks'), and its
own Bedrock pack (a separate file in Geyser's pack folder). It's designed to be installed
identically on HUB, SMP, and SURVIVAL, with the player's country and flag preference following
them across all three via the network's shared LuckPerms.

## Requirements

- Paper or Leaf, Minecraft 1.21.11, Java 21
- Velocity 4.x with **modern** player-info forwarding
- A local `GeoLite2-Country.mmdb` (MaxMind account required to download one -- see below)
- Optional but recommended: PlaceholderAPI, LuckPerms, TAB
- For reliable Bedrock detection specifically: Floodgate installed on **this backend server**,
  not just the proxy -- see "Bedrock detection" below, this is easy to get wrong

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
   `glyph-mapping.yml`, then review `config.yml` (see below) if you're using `CUSTOM_GLYPH`
   mode -- in particular `resourcepack.java.url`. **Recommended:** host
   `output/ArchFlags-Java.zip` on your own web server/CDN and put that one URL in
   `resourcepack.java.url` on all three backends, so you're not running a self-hosted HTTP
   server (and opening a port) on every server. Self-hosting is on by default as a fallback for
   quick testing, but needs `resourcepack.java.self-host.public-address` set (and its own port
   per backend if they share a machine) -- see "Resource packs" below.
5. If you want the Bedrock flag glyphs too, copy `output/ArchFlags-Bedrock.zip` to
   `Velocity/plugins/Geyser-Velocity/packs/ArchFlags-Bedrock.zip` on the proxy, next to
   NxRanks' own Bedrock pack -- see "Resource packs" below. This step is required for
   `CUSTOM_GLYPH` mode; `UNICODE` and `COUNTRY_CODE` modes need no resource pack at all, on
   either platform.
6. Configure TAB's above-head nametag to include `%archflags_nametag_suffix%` -- see
   "TAB integration" below.
7. If Floodgate is only installed on your Velocity proxy (the common setup), also install it on
   each backend server -- see "Bedrock detection" below. Without this, ArchFlags cannot
   reliably tell Java and Bedrock players apart, and Bedrock players may end up receiving the
   Java pack, which they can't use.
8. Repeat steps 2-7 identically on HUB, SMP, and SURVIVAL.

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

### Immediate refresh (no relog)

`/flag on|off|toggle` and `ArchFlagsService.setFlagVisible(...)` take effect immediately. The
placeholder itself is never cached by ArchFlags or PlaceholderAPI, so TAB will always show the
new value on its own next refresh cycle regardless -- but if TAB is installed, ArchFlags also
calls into TAB's developer API right away to force that refresh instantly, rather than waiting.
This uses reflection (TAB has no single Maven/JitPack artifact that reliably builds across
recent versions to compile against directly), so it degrades gracefully: if TAB's internal API
shape ever changes, ArchFlags just logs a debug warning and falls back to TAB's normal refresh
timing -- nothing breaks. Check `/archflags status` to see whether it's currently hooked.

### MiniMessage font tag

In `CUSTOM_GLYPH` mode, `%archflags_flag%` (and therefore `%archflags_nametag_suffix%`) is
wrapped in a MiniMessage `<font:archflags:flags>...</font>` tag by default
(`glyphs.wrap-minimessage: true` in `config.yml`), so the glyph renders in ArchFlags' custom
font automatically wherever the placeholder is used, as long as the renderer honors MiniMessage
`<font>` tags (TAB's Adventure-based text rendering does, on modern TAB/Paper versions). If your
TAB version doesn't support MiniMessage in the above-head nametag, set
`glyphs.wrap-minimessage: false` and switch `display.mode` to `UNICODE` or `COUNTRY_CODE`
instead, which need no custom font at all.

## Resource packs (independent of NxRanks -- never merged)

Both of ArchFlags' packs are generated by `tools/asset-generator/generate.mjs` and are always
kept as their own standalone files. Neither is ever merged into, or overwrites anything in,
NxRanks' packs, and an NxRanks update can never remove ArchFlags' assets (they simply aren't in
the same file).

**Java** -- `output/ArchFlags-Java.zip`: embedded directly in the plugin jar. ArchFlags sends it
to every Java player itself, right on join, using Minecraft's multi-resource-pack protocol
(1.20.3+) with `replace: false` -- i.e. **added alongside** whatever pack(s) NxRanks already
sends, never replacing them, and an NxRanks pack update likewise never removes ArchFlags' pack
(they're two separate pack IDs). Bedrock players are skipped -- see "Bedrock detection" below for
how that's actually determined on this network. Nothing to install by hand for Java players
beyond making the pack reachable -- see `resourcepack.java` in `config.yml`:
- **Externally hosted (recommended):** host `output/ArchFlags-Java.zip` on your own web
  server/CDN and set `resourcepack.java.url` to it on all three backends -- one URL, no ports to
  open on the game servers themselves.
- **Self-hosted (default fallback, fine for testing):** ArchFlags runs a tiny built-in HTTP
  server and serves the pack itself. Set `resourcepack.java.self-host.public-address` to a
  host/IP players' clients can actually reach (not `0.0.0.0`/`127.0.0.1`), and give each backend
  server its own `self-host.port` if HUB/SMP/SURVIVAL share a machine. Ignored when
  `resourcepack.java.url` is set.

If neither is configured, ArchFlags does not silently do nothing -- it logs
`Java pack disabled: no public URL configured.` on startup and in `/archflags status`.

**Bedrock** -- `output/ArchFlags-Bedrock.zip`: ArchFlags does **not** send this itself --
Geyser/Bedrock resource packs are delivered by Geyser reading its own pack folder on the
Velocity proxy, which a backend Paper plugin has no access to. Copy the file there by hand:

```
Geyser-Velocity/packs/
├── NxRanks-Bedrock.zip       (unchanged, managed by NxRanks)
└── ArchFlags-Bedrock.zip     (copy from output/ after every regeneration)
```

Every time the generator's output actually changes (different flag pixels, dimensions, or cell
size), it automatically bumps the pack's `manifest.json` version number so Geyser/Bedrock
clients don't keep a stale cached copy -- re-running the generator with no real change keeps the
version as-is. The pack's header/module UUIDs never change, so Geyser always treats a new zip as
an *update* to the same pack rather than a brand new one. After copying an updated zip, restart
Geyser (or wait for its own pack reload, depending on your Geyser version) to pick it up.

If proper Bedrock custom-glyph rendering ever doesn't work correctly on your Geyser version
(this is an unofficial, widely-used convention -- see `tools/asset-generator/README.md` for
details), set `display.mode: COUNTRY_CODE` rather than shipping a broken glyph; Java and Bedrock
share the exact same country resolution and visibility logic either way, so this only affects
how the flag is *drawn*, never which country is shown or on which side of the name.

See [`tools/asset-generator/README.md`](tools/asset-generator/README.md) for the flag source
dataset's license/attribution and generator usage/limitations in full.

## Bedrock detection (read this if Floodgate is only on your proxy)

ArchFlags needs to know whether a connecting player is Java or Bedrock for exactly one reason:
so it doesn't send the Java resource pack to a Bedrock client, which can't use it. **Country
resolution, visibility, and everything else about ArchFlags is completely unaffected either
way** -- this section only matters for that one decision.

On a typical Velocity network, **Geyser and Floodgate run on the proxy, not on the backend Paper
servers** -- and that's exactly the setup this network uses. Floodgate's own official, reliable
detection API (`FloodgateApi.isFloodgatePlayer(uuid)`) only works on a server where the
Floodgate *plugin* is loaded. If it's only on Velocity, a backend server like HUB, SMP, or
SURVIVAL has no way to call that API at all -- it isn't a bug in ArchFlags, there's just nothing
there to call.

**The reliable fix (recommended):** install Floodgate on each backend server too, using the
*same* `key.pem` as the proxy's Floodgate installation:

1. On the proxy, in Floodgate's config, set `send-floodgate-data: true`.
2. Copy `plugins/floodgate/key.pem` from the **proxy** to `plugins/floodgate/key.pem` on **each**
   of HUB, SMP, and SURVIVAL. (Never share this file outside your own servers -- see
   [Floodgate's setup guide](https://geysermc.org/wiki/floodgate/setup/proxy-servers/).)
3. Restart each backend. ArchFlags will log `Hooked Floodgate on this backend for reliable
   Bedrock detection.` on startup, and `/archflags status` will show `Bedrock detection:
   Floodgate API (reliable)`.

Backend Floodgate here is lightweight -- it doesn't run Geyser or bridge any connections itself,
it just decodes the data the proxy's Floodgate already forwards.

**If you don't want to install backend Floodgate:** ArchFlags will say so explicitly in its
startup logs rather than silently guessing wrong, and offers an optional, clearly best-effort
fallback: Floodgate already rewrites a Bedrock player's visible Java username on the proxy
(adding a prefix -- `.` by default) before forwarding them anywhere, so that prefix is often
still visible to a backend even without Floodgate installed there. Enable it with:

```yaml
bedrock-detection:
  username-prefix-fallback:
    enabled: true
    prefix: "."   # must match Floodgate's own "username-prefix" setting on the proxy
```

This is **not** an officially documented or guaranteed Floodgate behavior -- it breaks if that
prefix is set to empty on the proxy, and it's off by default. `/archflags status` reports
`Bedrock detection: username-prefix heuristic (best-effort, ...)` when it's the only thing
active, so you always know which mode you're actually running in. If neither backend Floodgate
nor the fallback is available, every player is treated as Java (also stated plainly in the logs
and in `/archflags status`), which means Bedrock players may incorrectly receive the Java pack
until one of the two is set up.

## Display modes

```yaml
display:
  mode: CUSTOM_GLYPH        # CUSTOM_GLYPH, UNICODE, or COUNTRY_CODE
  fallback-mode: COUNTRY_CODE
```

- `CUSTOM_GLYPH` -- the generated resource-pack glyphs (needs the independent packs above).
- `UNICODE` -- standard regional-indicator flag emoji (🇸🇦). No resource pack needed, but
  rendering depends entirely on the client's own font/emoji support.
- `COUNTRY_CODE` -- plain text like `[SA]`. Always works, needs nothing.

If `CUSTOM_GLYPH` is selected but a country has no mapped glyph (or the mapping file failed to
load), ArchFlags automatically renders `fallback-mode` for that placeholder instead of ever
showing a broken-square tofu glyph.

## Commands & permissions

| Command | Aliases | Permission | Default |
|---|---|---|---|
| `/flag`, `/flag on`, `/flag off`, `/flag toggle`, `/flag status` | `/flags` | `archflags.command` | granted to everyone |
| `/archflags reload` | | `archflags.admin.reload` | op |
| `/archflags lookup <player>` | | `archflags.admin.lookup` | op |
| `/archflags status` | | `archflags.admin.status` | op |

`archflags.admin` is a parent permission that grants all three `archflags.admin.*` nodes.
Default visibility for every player is **ON**.

Both commands support tab completion: `/flag <TAB>` suggests `on`/`off`/`toggle`/`status`
(filtered by whatever's already typed, e.g. `/flag o<TAB>` → `on`, `off`), and `/flags <TAB>`
behaves identically since it's just the alias. `/archflags <TAB>` only suggests the subcommands
the sender actually has permission for, and `/archflags lookup <TAB>` suggests online player
names. Any unrecognized `/flag` argument (e.g. `/flag banana`) is rejected with the `flag-usage`
message -- it does **not** silently fall back to showing status.

`/archflags lookup` never prints a raw IP address -- only the resolved country name/code and
visibility. Offline players who haven't connected recently show as "Unknown" (nothing is
resolvable without a live connection, and ArchFlags never stores a raw IP to look up later).

`/archflags status` reports every integration's actual state at a glance -- GeoIP/mapping
loaded, display mode, LuckPerms/PlaceholderAPI/TAB hooks, Bedrock detection mode, and the Java
pack's delivery mode/URL/SHA-1/self-host port (never anything secret).

## LuckPerms-backed visibility (network-wide)

Flag visibility is stored as a plain permission node on the player
(`luckperms.preference-node` in `config.yml`, default `archflags.visible`, `true`/`false`) via
the LuckPerms API -- **not** a new database, not a group/prefix/suffix change. Since HUB, SMP,
and SURVIVAL all share the same LuckPerms MySQL backend, a player who runs `/flag off` on HUB
and then switches to SMP or SURVIVAL finds it's still off there -- the preference travels with
them, the same way their rank does. On whichever single server they're actually on, the change
takes effect immediately, with no relog: LuckPerms persists it right away, and ArchFlags never
caches the placeholder value itself, so TAB re-evaluates it as soon as ArchFlags calls for an
immediate refresh (or, worst case, on TAB's own next normal refresh tick). If LuckPerms isn't
installed, ArchFlags still works, but the choice only persists in memory for that single
server's session (a startup warning is logged).

## Java/Bedrock parity

Country resolution, caching, and the LuckPerms-backed visibility toggle are all keyed purely by
player UUID and IP address -- nothing in that logic branches on platform, so Java Premium, Java
cracked, and Bedrock/Geyser/Floodgate players all get identical country detection and identical
`/flag` behavior. The nametag placeholder (`%archflags_nametag_suffix%`) is the same single
placeholder plugged into the same TAB nametag format for every player regardless of platform
(Geyser presents Bedrock players to TAB as ordinary Bukkit players), so the flag always renders
on the same side, with the same separator, for both. The only platform-specific piece is
*drawing* the glyph itself in `CUSTOM_GLYPH` mode -- the Java pack (bitmap font) and the Bedrock
pack (glyph sheet) are generated from the exact same source images, at the exact same pixel
dimensions, mapped to the exact same codepoint per country, so the same country always renders
as visually the same flag on both platforms (format differences aside, per the Bedrock
limitations note above).

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
can't collide with another plugin's copy). The jar already embeds the current
`output/ArchFlags-Java.zip` -- see `tools/asset-generator/README.md` if you need to regenerate
flag assets (different dimensions, a different start codepoint -- keep it outside NxRanks'
`E9xx` range -- or an updated country list); re-run `npm run generate` there *before* building
the plugin jar so the embedded pack and `glyph-mapping.yml` stay in sync.
