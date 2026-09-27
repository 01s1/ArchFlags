# ArchFlags asset generator

Generates every ISO 3166-1 alpha-2 country's nametag flag glyph as two fully **independent**
resource packs (Java + Bedrock -- neither is ever merged into NxRanks') plus `glyph-mapping.yml`,
automatically -- nothing here is hand-typed per country.

## Source dataset

Flag artwork comes from **[lipis/flag-icons](https://github.com/lipis/flag-icons)**
(the `flags/4x3/*.svg` set), **MIT licensed**. A copy of the license is at
[`THIRD_PARTY_LICENSES/flag-icons-LICENSE.txt`](THIRD_PARTY_LICENSES/flag-icons-LICENSE.txt).
The MIT license only requires keeping the copyright/license notice alongside the
software -- that's satisfied by this file; no further per-server attribution is
required, but crediting lipis/flag-icons in your own credits page is appreciated.

The SVGs currently used are vendored into [`flags-source/`](flags-source) so the
generator is reproducible without re-cloning. To refresh them from upstream:

```bash
git clone --depth 1 https://github.com/lipis/flag-icons.git /tmp/flag-icons
cp /tmp/flag-icons/flags/4x3/*.svg flags-source/
cp /tmp/flag-icons/LICENSE THIRD_PARTY_LICENSES/flag-icons-LICENSE.txt
```

## What counts as a "country"

`flags-source/` also contains a handful of flag-icons entries that are **not**
ISO 3166-1 country assignments (the EU, the UN, Kosovo's unofficial `XK`, the
Canary Islands, Clipperton Island, and flag-icons' own "unknown" placeholder),
plus organization/subdivision flags with 3+ letter or hyphenated codes
(`asean`, `gb-eng`, `sh-ac`, ...). The generator keeps only exactly-2-letter
codes that `Intl.DisplayNames` recognizes as a real region **and** that aren't
in that small exclusion list -- see `NON_ISO_3166_1_CODES` at the top of
`generate.mjs`. Everything else (which country codes exist, and their English
names) comes from Node's built-in ICU data, not a hand-maintained list.

## Usage

```bash
npm install
npm run generate
```

Reads `generator.config.json` (flag pixel dimensions, start codepoint, Java `pack_format`,
Bedrock glyph cell size + pack UUIDs/version) and `flags-source/*.svg`, and writes:

- `../../output/java/` -- the Java pack's source tree (`pack.mcmeta` +
  `assets/archflags/font/flags.json` + `assets/archflags/textures/font/flags/<code>.png`, one
  small PNG per country, default 16x11px, one bitmap-font provider per country each mapped to
  its own private-use-area codepoint)
- `../../output/ArchFlags-Java.zip` -- the zipped Java pack, also copied to
  `../../src/main/resources/archflags-java-pack.zip` so it's embedded in the plugin jar (the
  plugin sends/serves this itself -- see the main README's "Resource packs" section)
- `../../output/bedrock/` -- the Bedrock pack's source tree (`manifest.json` +
  `textures/font/glyph_EN.png` sheet(s), see "Bedrock limitations" below)
- `../../output/ArchFlags-Bedrock.zip` -- the zipped Bedrock pack. **Not** embedded in the
  plugin -- copy this by hand to `Geyser-Velocity/packs/ArchFlags-Bedrock.zip` after every
  regeneration (see the main README)
- `../../output/glyph-mapping.yml` -- `country code -> {codepoint, name}`, also
  copied to `../../src/main/resources/glyph-mapping.yml` so the plugin ships a
  working default
- `../../output/GENERATION_REPORT.md` -- a human-readable summary of the run

Re-run after editing `generator.config.json` (e.g. to change `startCodepoint` -- **ArchFlags'
own range is `E200`-`E2F9`; NxRanks uses `E9xx` on this network, so never move into `E900`-`E9FF`,
and check any other plugin's private-use-area usage too**) -- and update `glyphs.start-codepoint`
in the plugin's `config.yml` to match, since the plugin reads the codepoint straight out of the
mapping file rather than assuming a fixed range.

Each run also checks whether the Bedrock pack's actual pixel content changed since the last run
(tracked via `bedrock.lastContentHash` in `generator.config.json`) and, if so, automatically
bumps `bedrock.version`'s patch number -- written into both the manifest and back into
`generator.config.json` -- so Geyser/Bedrock clients never keep a stale cached copy. The
header/module UUIDs in `generator.config.json` are generated once and must never change, or
Geyser will treat the next zip as a brand new pack instead of an update to this one.

## Bedrock limitations

Bedrock resource packs don't support Java's per-glyph bitmap font providers.
Instead this generator uses the standard "custom glyph sheet" convention:
`textures/font/glyph_EN.png`, a 16x16 grid of same-size cells, where `N` is the
high byte of the codepoint (e.g. codepoints `E200`-`E2FF` live in
`glyph_E2.png`). Each flag is centered in its cell at the same pixel
dimensions as the Java texture so the two platforms look as close as
reasonably possible. This is a widely used but **unofficial** convention (no
official Mojang Bedrock documentation covers it) -- test on your actual
Bedrock/Geyser client after copying the pack into `Geyser-Velocity/packs/`. If
it doesn't render correctly on your Bedrock version, set `display.mode:
COUNTRY_CODE` (or `fallback-mode:
COUNTRY_CODE`, which is the default) in `config.yml` rather than shipping a
broken glyph.

At 250 countries and a 16-codepoint-per-row, 16-row grid (256 slots), every
current ISO 3166-1 alpha-2 country fits in a single sheet.
