#!/usr/bin/env node
// ArchFlags asset generator.
//
// Reads ISO 3166-1 alpha-2 country flag SVGs from ./flags-source (sourced from
// lipis/flag-icons, MIT licensed -- see THIRD_PARTY_LICENSES/flag-icons-LICENSE.txt
// and README.md in this directory) and produces two fully INDEPENDENT resource packs
// (never merged into NxRanks) plus the plugin's glyph mapping:
//
//   - ../../output/java/...                    -- Java pack source tree (assets/, pack.mcmeta)
//   - ../../output/ArchFlags-Java.zip           -- zipped Java pack (also embedded in the plugin jar)
//   - ../../output/bedrock/...                  -- Bedrock pack source tree (textures/, manifest.json)
//   - ../../output/ArchFlags-Bedrock.zip        -- zipped Bedrock pack (copy manually to Geyser-Velocity/packs/)
//   - ../../output/glyph-mapping.yml            -- country -> codepoint -> name
//   - ../../output/GENERATION_REPORT.md         -- human-readable summary
//
// The generated glyph-mapping.yml and the Java pack zip are also copied into
// ../../src/main/resources/ so the plugin ships with a working default mapping and its own
// resource pack out of the box. Re-run this script any time generator.config.json changes
// (dimensions, start codepoint, etc.).
//
// Country codes and English names are NOT hand-typed: this script intersects the flag SVGs
// on disk with Node's built-in Intl.DisplayNames (ICU's region name data) and a small fixed
// exclusion list for non-ISO-3166-1 entries -- see NON_ISO_3166_1_CODES below.
//
// Glyph codepoints (default U+E200-U+E2F9) are a DIFFERENT, deliberately separate range from
// NxRanks' own private-use-area glyphs (E9xx) -- ArchFlags never touches that range, and this
// generator's output never gets merged into NxRanks' packs.

import { readFileSync, writeFileSync, mkdirSync, readdirSync, existsSync, rmSync, createWriteStream } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { createHash } from "node:crypto";
import sharp from "sharp";
import * as yaml from "js-yaml";
import { ZipArchive } from "archiver";

const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT = join(__dirname, "..", "..");
const SRC_DIR = join(__dirname, "flags-source");
const OUT_DIR = join(ROOT, "output");
const JAVA_PACK_DIR = join(OUT_DIR, "java");
const JAVA_ASSETS_OUT = join(JAVA_PACK_DIR, "assets", "archflags");
const BEDROCK_PACK_DIR = join(OUT_DIR, "bedrock");
const BEDROCK_FONT_OUT = join(BEDROCK_PACK_DIR, "textures", "font");
const RESOURCES_DIR = join(ROOT, "src", "main", "resources");
const CONFIG_PATH = join(__dirname, "generator.config.json");

const config = JSON.parse(readFileSync(CONFIG_PATH, "utf8"));
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

function zipDirectory(sourceDir, outFile) {
    return new Promise((resolve, reject) => {
        const output = createWriteStream(outFile);
        const archive = new ZipArchive({ zlib: { level: 9 } });
        output.on("close", () => resolve());
        archive.on("error", reject);
        archive.pipe(output);
        archive.directory(sourceDir, false);
        archive.finalize();
    });
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

    resetDir(join(JAVA_ASSETS_OUT, "textures", "font", "flags"));
    mkdirSync(join(JAVA_ASSETS_OUT, "font"), { recursive: true });
    resetDir(BEDROCK_FONT_OUT);

    const mapping = {};
    const javaProviders = [];
    const bedrockSheetComposites = new Map(); // sheetIndex -> sharp composite ops

    for (let i = 0; i < accepted.length; i++) {
        const code = accepted[i];
        const codepoint = START_CODEPOINT + i;
        const hex = codepoint.toString(16).toUpperCase().padStart(4, "0");
        const name = countryName(code);
        const lower = code.toLowerCase();

        const png = await renderFlagPng(code);

        // Java: one bitmap-font provider per glyph, referencing its own small texture.
        writeFileSync(join(JAVA_ASSETS_OUT, "textures", "font", "flags", `${lower}.png`), png);
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
        if (!bedrockSheetComposites.has(sheetIndex)) bedrockSheetComposites.set(sheetIndex, []);
        bedrockSheetComposites.get(sheetIndex).push({ input: png, left: Math.max(0, left), top: Math.max(0, top) });

        mapping[code] = { codepoint: hex, name };
    }

    writeFileSync(join(JAVA_ASSETS_OUT, "font", "flags.json"), JSON.stringify({ providers: javaProviders }, null, 2));

    // Java pack.mcmeta -- pack_format 75 = Minecraft 1.21.11 (see tools/asset-generator/README.md
    // if this ever needs bumping for a newer Minecraft version).
    writeFileSync(join(JAVA_PACK_DIR, "pack.mcmeta"), JSON.stringify({
        pack: {
            pack_format: config.java.packFormat,
            description: config.java.packDescription
        }
    }, null, 2));

    const bedrockSheetBuffers = new Map(); // sheetIndex -> PNG buffer, for hashing + writing
    for (const [sheetIndex, composites] of bedrockSheetComposites.entries()) {
        const size = BEDROCK_GRID * BEDROCK_CELL;
        const buffer = await sharp({
            create: { width: size, height: size, channels: 4, background: { r: 0, g: 0, b: 0, alpha: 0 } }
        }).composite(composites).png().toBuffer();
        bedrockSheetBuffers.set(sheetIndex, buffer);
    }
    const sortedSheetIndices = [...bedrockSheetBuffers.keys()].sort((a, b) => a - b);
    for (const sheetIndex of sortedSheetIndices) {
        const sheetHex = sheetIndex.toString(16).toUpperCase();
        writeFileSync(join(BEDROCK_FONT_OUT, `glyph_${sheetHex}.png`), bedrockSheetBuffers.get(sheetIndex));
    }

    // Bedrock manifest version: bump the patch number only when the actual pixel content (or
    // cell size) changed since the last run, so Geyser/Bedrock clients don't keep a stale cached
    // copy -- but don't churn the version (and thus force every client to re-download) on a
    // no-op re-run. header/module UUIDs stay fixed forever: changing them would make Bedrock
    // treat this as a brand new pack instead of an update.
    const contentHash = createHash("sha256");
    contentHash.update(String(BEDROCK_CELL));
    for (const sheetIndex of sortedSheetIndices) {
        contentHash.update(bedrockSheetBuffers.get(sheetIndex));
    }
    const newHash = contentHash.digest("hex");

    const bedrockCfg = config.bedrock;
    let version = Array.isArray(bedrockCfg.version) ? [...bedrockCfg.version] : [1, 0, 0];
    const contentChanged = bedrockCfg.lastContentHash !== newHash;
    if (contentChanged) {
        version[2] = (version[2] ?? 0) + 1;
        console.log(`Bedrock pack content changed -> bumping manifest version to ${version.join(".")}.`);
    } else {
        console.log(`Bedrock pack content unchanged -> keeping manifest version ${version.join(".")}.`);
    }

    writeFileSync(join(BEDROCK_PACK_DIR, "manifest.json"), JSON.stringify({
        format_version: 2,
        header: {
            name: bedrockCfg.packName,
            description: bedrockCfg.packDescription,
            uuid: bedrockCfg.headerUuid,
            version,
            min_engine_version: bedrockCfg.minEngineVersion
        },
        modules: [
            {
                type: "resources",
                uuid: bedrockCfg.moduleUuid,
                version
            }
        ]
    }, null, 2));

    // Persist the (possibly bumped) version + hash back into generator.config.json so the next
    // run knows whether content changed again.
    config.bedrock.version = version;
    config.bedrock.lastContentHash = newHash;
    writeFileSync(CONFIG_PATH, JSON.stringify(config, null, 2) + "\n");

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

    // Zip both packs. The Java pack is also embedded in the plugin jar (the plugin serves/hosts
    // it itself); the Bedrock pack is NOT embedded -- it's copied by hand to
    // Geyser-Velocity/packs/ArchFlags-Bedrock.zip, independently of NxRanks' own Bedrock pack.
    const javaZipPath = join(OUT_DIR, "ArchFlags-Java.zip");
    const bedrockZipPath = join(OUT_DIR, "ArchFlags-Bedrock.zip");
    await zipDirectory(JAVA_PACK_DIR, javaZipPath);
    await zipDirectory(BEDROCK_PACK_DIR, bedrockZipPath);
    writeFileSync(join(RESOURCES_DIR, "archflags-java-pack.zip"), readFileSync(javaZipPath));

    const sheetList = sortedSheetIndices.map((s) => `glyph_${s.toString(16).toUpperCase()}.png`);
    const report = [
        "# ArchFlags asset generation report",
        "",
        `Generated: ${new Date().toISOString()}`,
        `Countries: ${accepted.length}`,
        `Flag glyph dimensions: ${WIDTH}x${HEIGHT}px`,
        `Codepoint range: U+${START_CODEPOINT.toString(16).toUpperCase()} - U+${(START_CODEPOINT + accepted.length - 1).toString(16).toUpperCase()} (separate from NxRanks' E9xx range)`,
        `Java pack: ${javaZipPath} (pack_format ${config.java.packFormat}, embedded in the plugin jar)`,
        `Bedrock pack: ${bedrockZipPath} (manifest version ${version.join(".")}, ${contentChanged ? "bumped this run" : "unchanged this run"})`,
        `Bedrock sheets: ${sheetList.join(", ")}`,
        `Skipped non-ISO-3166-1 source files: ${skippedNonIso.join(", ") || "(none)"}`,
        "",
        "Both packs are independent -- neither is merged into NxRanks. See README.md for how",
        "the Java pack is self-served by the plugin and where to copy the Bedrock pack."
    ].join("\n");
    writeFileSync(join(OUT_DIR, "GENERATION_REPORT.md"), report + "\n");

    console.log(`Wrote ${accepted.length} flags. Codepoints U+${START_CODEPOINT.toString(16).toUpperCase()}-U+${(START_CODEPOINT + accepted.length - 1).toString(16).toUpperCase()}.`);
    console.log(`Java pack: ${javaZipPath}`);
    console.log(`Bedrock pack: ${bedrockZipPath} (manifest version ${version.join(".")})`);
    console.log("Done.");
}

main().catch((err) => {
    console.error(err);
    process.exit(1);
});
