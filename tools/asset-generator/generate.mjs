#!/usr/bin/env node
// ArchFlags asset generator.
//
// Reads ISO 3166-1 alpha-2 country flag SVGs from ./flags-source (sourced from
// lipis/flag-icons, MIT licensed -- see THIRD_PARTY_LICENSES/flag-icons-LICENSE.txt
// and README.md in this directory) and produces:
//
//   - ../../output/java/assets/archflags/textures/font/flags/<code>.png   (one per country)
//   - ../../output/java/assets/archflags/font/flags.json                 (Java bitmap font provider list)
//   - ../../output/bedrock/textures/font/glyph_EN.png                    (16x16 Bedrock glyph sheet(s))
//   - ../../output/glyph-mapping.yml                                     (country -> codepoint -> name)
//   - ../../output/GENERATION_REPORT.md                                  (human-readable summary)
//
// The generated glyph-mapping.yml is also copied to
// ../../src/main/resources/glyph-mapping.yml so the plugin ships with a working
// default mapping out of the box. Re-run this script any time generator.config.json
// changes (dimensions, start codepoint, etc.) and re-copy that file if you edit the
// copy step out.
//
// Country codes and English names are NOT hand-typed: this script intersects the
// flag SVGs on disk with Node's built-in Intl.supportedValuesOf('region') (ICU's
// canonical ISO 3166-1 region list) and asks Intl.DisplayNames for each name, so
// adding/removing a country is just adding/removing an SVG file.

import { readFileSync, writeFileSync, mkdirSync, readdirSync, existsSync, rmSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import sharp from "sharp";
import * as yaml from "js-yaml";

const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT = join(__dirname, "..", "..");
const SRC_DIR = join(__dirname, "flags-source");
const OUT_DIR = join(ROOT, "output");
const JAVA_OUT = join(OUT_DIR, "java", "assets", "archflags");
const BEDROCK_OUT = join(OUT_DIR, "bedrock", "textures", "font");
const RESOURCES_DIR = join(ROOT, "src", "main", "resources");

const config = JSON.parse(readFileSync(join(__dirname, "generator.config.json"), "utf8"));
const WIDTH = config.width;
const HEIGHT = config.height;
const START_CODEPOINT = parseInt(config.startCodepoint, 16);
const JAVA_FONT_HEIGHT = config.java.fontHeight;
const JAVA_FONT_ASCENT = config.java.fontAscent;
const BEDROCK_CELL = config.bedrock.cellSize;
const BEDROCK_GRID = 16; // Bedrock glyph sheets are always a 16x16 grid.

function resetDir(dir) {
    if (existsSync(dir)) rmSync(dir, { recursive: true, force: true });
    mkdirSync(dir, { recursive: true });
}

// This Node build's ICU does not implement Intl.supportedValuesOf("region"), so there is no
// runtime API to enumerate "all ISO 3166-1 alpha-2 codes" directly. Instead: take every 2-letter
// filename present in flags-source (already narrows out flag-icons' organization/subdivision
// entries, which use 3+ letter or hyphenated names -- "asean", "gb-eng", "sh-ac", etc.), drop the
// small, fixed set of 2-letter flag-icons entries that ARE real ICU-recognized CLDR regions but
// are NOT official ISO 3166-1 country assignments, and validate what's left against
// Intl.DisplayNames (which still supplies every country's English name -- nothing here is
// hand-typed per-country).
const NON_ISO_3166_1_CODES = new Set([
    "EU", // European Union -- supranational, not an ISO 3166-1 country
    "UN", // United Nations -- organization, not a country
    "XX", // flag-icons' "unknown/no flag" placeholder
    "IC", // Canary Islands -- not separately assigned in ISO 3166-1 (part of ES)
    "CP", // Clipperton Island -- not separately assigned in ISO 3166-1 (French possession)
    "XK"  // Kosovo -- widely used unofficial/user-assigned code, not formally ISO 3166-1
]);

function loadCandidateCodes() {
    const svgFiles = readdirSync(SRC_DIR).filter((f) => f.endsWith(".svg"));
    const fromSvg = [...new Set(svgFiles.map((f) => f.replace(/\.svg$/, "").toUpperCase()))]
        .filter((code) => /^[A-Z]{2}$/.test(code));

    const dn = new Intl.DisplayNames(["en"], { type: "region" });
    const accepted = [];
    const skippedNonIso = [];
    for (const code of fromSvg) {
        const recognized = dn.of(code) !== code; // ICU knows this region code at all
        if (recognized && !NON_ISO_3166_1_CODES.has(code)) {
            accepted.push(code);
        } else {
            skippedNonIso.push(code);
        }
    }
    accepted.sort();
    skippedNonIso.sort();
    return { accepted, skippedNonIso };
}

function countryName(code) {
    const dn = new Intl.DisplayNames(["en"], { type: "region" });
    const name = dn.of(code);
    return name && name !== code ? name : code;
}

async function renderFlagPng(code) {
    const svgPath = join(SRC_DIR, `${code.toLowerCase()}.svg`);
    const svgBuffer = readFileSync(svgPath);
    return sharp(svgBuffer, { density: 384 })
        .resize(WIDTH, HEIGHT, { fit: "fill" })
        .png()
        .toBuffer();
}

async function main() {
    if (!existsSync(SRC_DIR)) {
        console.error(`Missing flags-source directory: ${SRC_DIR}`);
        console.error("See README.md for how to populate it from lipis/flag-icons.");
        process.exit(1);
    }

    const { accepted, skippedNonIso } = loadCandidateCodes();
    console.log(`Found ${accepted.length} ISO 3166-1 alpha-2 countries with source flags.`);
    if (skippedNonIso.length) {
        console.log(`Skipped ${skippedNonIso.length} non-ISO-3166-1 entries: ${skippedNonIso.join(", ")}`);
    }

    resetDir(join(JAVA_OUT, "textures", "font", "flags"));
    mkdirSync(join(JAVA_OUT, "font"), { recursive: true });
    resetDir(BEDROCK_OUT);

    const mapping = {};
    const javaProviders = [];
    const bedrockSheets = new Map(); // sheetIndex -> sharp composite ops

    for (let i = 0; i < accepted.length; i++) {
        const code = accepted[i];
        const codepoint = START_CODEPOINT + i;
        const hex = codepoint.toString(16).toUpperCase().padStart(4, "0");
        const name = countryName(code);
        const lower = code.toLowerCase();

        const png = await renderFlagPng(code);

        // Java: one bitmap-font provider per glyph, referencing its own small texture.
        writeFileSync(join(JAVA_OUT, "textures", "font", "flags", `${lower}.png`), png);
        javaProviders.push({
            type: "bitmap",
            file: `archflags:font/flags/${lower}.png`,
            ascent: JAVA_FONT_ASCENT,
            height: JAVA_FONT_HEIGHT,
            // Written as the literal PUA character (not a "\uXXXX" text escape) -- Gson reads a
            // raw UTF-8 codepoint here the same as an escape sequence, and this avoids a
            // double-escaping bug where JSON.stringify would re-escape a literal backslash.
            chars: [String.fromCodePoint(codepoint)]
        });

        // Bedrock: place into the appropriate 16x16 glyph_EN.png sheet, centered in its cell.
        const sheetIndex = codepoint >> 8; // e.g. 0xE2 for 0xE200-0xE2FF
        const withinSheet = codepoint & 0xff;
        const row = Math.floor(withinSheet / BEDROCK_GRID);
        const col = withinSheet % BEDROCK_GRID;
        const left = col * BEDROCK_CELL + Math.round((BEDROCK_CELL - WIDTH) / 2);
        const top = row * BEDROCK_CELL + Math.round((BEDROCK_CELL - HEIGHT) / 2);
        if (!bedrockSheets.has(sheetIndex)) bedrockSheets.set(sheetIndex, []);
        bedrockSheets.get(sheetIndex).push({ input: png, left: Math.max(0, left), top: Math.max(0, top) });

        mapping[code] = { codepoint: hex, name };
    }

    writeFileSync(join(JAVA_OUT, "font", "flags.json"), JSON.stringify({ providers: javaProviders }, null, 2));

    for (const [sheetIndex, composites] of bedrockSheets.entries()) {
        const sheetHex = sheetIndex.toString(16).toUpperCase();
        const size = BEDROCK_GRID * BEDROCK_CELL;
        const sheet = sharp({
            create: { width: size, height: size, channels: 4, background: { r: 0, g: 0, b: 0, alpha: 0 } }
        }).composite(composites);
        await sheet.png().toFile(join(BEDROCK_OUT, `glyph_${sheetHex}.png`));
    }

    const mappingDoc = {
        "start-codepoint": config.startCodepoint,
        width: WIDTH,
        height: HEIGHT,
        countries: mapping
    };
    const mappingYaml =
        "# Generated by tools/asset-generator/generate.mjs -- do not hand-edit.\n" +
        "# Re-run the generator instead (see tools/asset-generator/README.md).\n" +
        yaml.dump(mappingDoc, { lineWidth: -1 });
    mkdirSync(OUT_DIR, { recursive: true });
    writeFileSync(join(OUT_DIR, "glyph-mapping.yml"), mappingYaml);
    writeFileSync(join(RESOURCES_DIR, "glyph-mapping.yml"), mappingYaml);

    const sheetList = [...bedrockSheets.keys()]
        .sort((a, b) => a - b)
        .map((s) => `glyph_${s.toString(16).toUpperCase()}.png`);
    const report = [
        "# ArchFlags asset generation report",
        "",
        `Generated: ${new Date().toISOString()}`,
        `Countries: ${accepted.length}`,
        `Flag glyph dimensions: ${WIDTH}x${HEIGHT}px`,
        `Codepoint range: U+${START_CODEPOINT.toString(16).toUpperCase()} - U+${(START_CODEPOINT + accepted.length - 1).toString(16).toUpperCase()}`,
        `Bedrock sheets written: ${sheetList.join(", ")}`,
        `Skipped non-ISO-3166-1 source files: ${skippedNonIso.join(", ") || "(none)"}`,
        "",
        "See README.md for licensing/attribution and how to merge output/java and",
        "output/bedrock into the existing NxRanks resource packs."
    ].join("\n");
    writeFileSync(join(OUT_DIR, "GENERATION_REPORT.md"), report + "\n");

    console.log(`Wrote ${accepted.length} flags. Codepoints U+${START_CODEPOINT.toString(16).toUpperCase()}-U+${(START_CODEPOINT + accepted.length - 1).toString(16).toUpperCase()}.`);
    console.log(`Bedrock sheets: ${sheetList.join(", ")}`);
    console.log("Done.");
}

main().catch((err) => {
    console.error(err);
    process.exit(1);
});
