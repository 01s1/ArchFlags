# ArchFlags asset generator

Generates every ISO 3166-1 alpha-2 country's nametag flag glyph automatically -- nothing here is
hand-typed per country. Java and Bedrock are handled differently on purpose:

- **Java** gets its own fully independent resource pack (`generate.mjs`), embedded in the plugin
  jar and sent by the plugin itself, never merged into NxRanks'.
- **Bedrock** does **not** get a separate pack at all -- an earlier version of ArchFlags shipped
  one, and running two independently-active Bedrock packs broke custom glyph rendering for both
  on this network's Geyser setup. Instead, `generate.mjs` only produces the raw glyph sheet
  asset, and a second script, `merge-bedrock.mjs`, injects it into your existing, known-good
  Bedrock pack (e.g. `arch-bedrock-corrected.zip`) so Geyser only ever loads **one** pack.

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

## Step 1: generate

```bash
npm install
npm run generate
```

Reads `generator.config.json` (flag pixel dimensions, start codepoint, Java `pack_format`,
Bedrock glyph cell size) and `flags-source/*.svg`, and writes:

- `../../output/java/` -- the Java pack's source tree (`pack.mcmeta` +
  `assets/archflags/font/flags.json` + `assets/archflags/textures/font/flags/<code>.png`, one
  small PNG per country, default 16x11px, one bitmap-font provider per country each mapped to
  its own private-use-area codepoint)
- `../../output/ArchFlags-Java.zip` -- the zipped Java pack, also copied to
  `../../src/main/resources/archflags-java-pack.zip` so it's embedded in the plugin jar (the
  plugin sends/serves this itself -- see the main README's "Java resource pack" section)
- `../../output/bedrock/textures/font/glyph_EN.png` -- the raw Bedrock glyph sheet(s). **Not** a
  standalone pack -- see "Step 2: merge into your Bedrock pack" below.
- `../../output/glyph-mapping.yml` -- `country code -> {codepoint, name}`, also
  copied to `../../src/main/resources/glyph-mapping.yml` so the plugin ships a
  working default
- `../../output/GENERATION_REPORT.md` -- a human-readable summary of the run

Re-run after editing `generator.config.json` (e.g. to change `startCodepoint` -- **ArchFlags'
own range is `E200`-`E2F9`; NxRanks uses `E9xx` on this network, so never move into `E900`-`E9FF`,
and check any other plugin's private-use-area usage too**) -- and update `glyphs.start-codepoint`
in the plugin's `config.yml` to match, since the plugin reads the codepoint straight out of the
mapping file rather than assuming a fixed range.

## Step 2: merge into your Bedrock pack

```bash
npm run merge-bedrock -- --pack /path/to/arch-bedrock-corrected.zip
```

Writes `arch-bedrock-corrected-merged.zip` next to your input file by default (your original is
left untouched so you can verify the result first); pass `--in-place` to overwrite the input
directly, or `--out <path>` to pick a specific output location.

What it does, precisely:

- Reads every `textures/font/glyph_EN.png` sheet from `output/bedrock/` (produced in Step 1) and
  adds or replaces each one, at that exact same path, inside a copy of your target pack.
- Touches **nothing else** in your pack -- every other file (including NxRanks' own
  `textures/font/glyph_E9.png`) is copied through byte-for-byte.
- Reads and updates your target pack's `manifest.json`: bumps `header.version` and every entry
  in `modules[]`'s patch number, but **only when ArchFlags' glyph content actually changed**
  since the last merge (tracked via a small `archflags/.merge-state.json` marker written inside
  the merged pack itself -- so state survives even though your pack lives outside this repo).
  Re-running the merge with no real change leaves the version untouched. Your pack's existing
  header/module UUIDs are never changed -- changing them would make Geyser treat the result as a
  brand new pack instead of an update to the one it already has cached.
- If it finds something already at `textures/font/glyph_E2.png` that it didn't write itself on a
  previous run (a real naming collision, or a manually-edited file), it prints an explicit
  `WARNING:` line rather than silently clobbering it -- then still overwrites it, since that path
  is inside ArchFlags' own declared range.

Copy the resulting zip into `Geyser-Velocity/packs/` as your **one** active Bedrock pack. Delete
any leftover standalone `ArchFlags-Bedrock.zip` from an older ArchFlags version if one is still
there -- there must only ever be one Bedrock pack active.

## Bedrock glyph technique

Bedrock resource packs don't support Java's per-glyph bitmap font providers.
Instead this generator uses the standard "custom glyph sheet" convention:
`textures/font/glyph_EN.png`, a 16x16 grid of same-size cells, where `N` is the
high byte of the codepoint (e.g. codepoints `E200`-`E2FF` live in
`glyph_E2.png`). Each flag is centered in its cell at the same pixel
dimensions as the Java texture so the two platforms look as close as
reasonably possible. This is a widely used but **unofficial** convention (no
official Mojang Bedrock documentation covers it) -- test on your actual
Bedrock/Geyser client after merging. If it doesn't render correctly on your
Bedrock version, set `display.mode: COUNTRY_CODE` (or `fallback-mode:
COUNTRY_CODE`, which is the default) in `config.yml` rather than shipping a
broken glyph.

At 250 countries and a 16-codepoint-per-row, 16-row grid (256 slots), every
current ISO 3166-1 alpha-2 country fits in a single sheet (`glyph_E2.png`),
entirely separate from NxRanks' `glyph_E9.png`.
