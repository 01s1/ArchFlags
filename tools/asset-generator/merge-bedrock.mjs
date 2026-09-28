#!/usr/bin/env node
// Merges ArchFlags' generated Bedrock glyph sheet(s) (output/bedrock/textures/font/glyph_EN.png,
// produced by generate.mjs) INTO an existing, already-known-good Bedrock resource pack -- instead
// of shipping ArchFlags as a second, separately-active Bedrock pack.
//
// Why: running ArchFlags' glyphs as its own pack alongside the network's existing pack broke
// custom glyph rendering for BOTH packs in practice on this network's Geyser setup. Only ONE
// Bedrock pack should ever be active in Geyser-Velocity/packs/.
//
// This script only ever touches:
//   - textures/font/glyph_EN.png for each sheet ArchFlags generated (added or replaced)
//   - manifest.json's version numbers (bumped only if content actually changed)
//   - a small archflags/.merge-state.json marker (tracks what WE last wrote, so re-running this
//     doesn't confuse "we updated our own glyphs" with "someone else's file is already there")
// Every other file in the target pack -- including NxRanks' own E9xx-range glyph sheet(s) -- is
// left completely untouched, byte-for-byte.
//
// Usage:
//   node merge-bedrock.mjs --pack /path/to/arch-bedrock-corrected.zip [--out <path>] [--in-place]
//
// By default writes a new file (<name>-merged.zip) next to the input rather than overwriting it,
// so you can verify the result before replacing the real pack. Pass --in-place to overwrite the
// input file directly instead.

import { readFileSync, writeFileSync, readdirSync, existsSync } from "node:fs";
import { join, dirname, basename, extname } from "node:path";
import { fileURLToPath } from "node:url";
import { createHash } from "node:crypto";
import AdmZip from "adm-zip";

const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT = join(__dirname, "..", "..");
const OUT_DIR = join(ROOT, "output");
const BEDROCK_FONT_DIR = join(OUT_DIR, "bedrock", "textures", "font");
const MERGE_STATE_PATH = "archflags/.merge-state.json";

function sha256(buffer) {
    return createHash("sha256").update(buffer).digest("hex");
}

function parseArgs(argv) {
    const args = { pack: null, out: null, inPlace: false };
    for (let i = 0; i < argv.length; i++) {
        const a = argv[i];
        if (a === "--pack") args.pack = argv[++i];
        else if (a === "--out") args.out = argv[++i];
        else if (a === "--in-place") args.inPlace = true;
        else if (a === "--help" || a === "-h") args.help = true;
    }
    return args;
}

function printUsageAndExit(code) {
    console.log("Usage: node merge-bedrock.mjs --pack <path-to-existing-bedrock-pack.zip> [--out <path>] [--in-place]");
    process.exit(code);
}

function loadSheets() {
    if (!existsSync(BEDROCK_FONT_DIR)) {
        console.error(`No generated Bedrock glyph sheets found at ${BEDROCK_FONT_DIR}.`);
        console.error("Run `node generate.mjs` first.");
        process.exit(1);
    }
    const files = readdirSync(BEDROCK_FONT_DIR).filter((f) => /^glyph_[0-9A-Fa-f]+\.png$/.test(f));
    if (files.length === 0) {
        console.error(`No glyph_EN.png sheets found in ${BEDROCK_FONT_DIR}. Run \`node generate.mjs\` first.`);
        process.exit(1);
    }
    return files.map((f) => ({
        entryPath: `textures/font/${f}`,
        bytes: readFileSync(join(BEDROCK_FONT_DIR, f))
    }));
}

function bumpVersion(version, label, warnings) {
    if (!Array.isArray(version) || version.length !== 3 || version.some((n) => typeof n !== "number")) {
        warnings.push(`${label} has an unexpected version format (${JSON.stringify(version)}) -- leaving it as-is. Bump it by hand.`);
        return version;
    }
    return [version[0], version[1], version[2] + 1];
}

function main() {
    const args = parseArgs(process.argv.slice(2));
    if (args.help || !args.pack) {
        printUsageAndExit(args.help ? 0 : 1);
    }
    if (!existsSync(args.pack)) {
        console.error(`Pack not found: ${args.pack}`);
        process.exit(1);
    }

    const sheets = loadSheets();
    const zip = new AdmZip(args.pack);

    const manifestEntry = zip.getEntry("manifest.json");
    if (!manifestEntry) {
        console.error(`${args.pack} has no manifest.json at its root -- this doesn't look like a valid Bedrock resource pack.`);
        process.exit(1);
    }
    let manifest;
    try {
        manifest = JSON.parse(zip.readAsText(manifestEntry));
    } catch (err) {
        console.error(`Failed to parse manifest.json in ${args.pack}: ${err.message}`);
        process.exit(1);
    }

    const stateEntry = zip.getEntry(MERGE_STATE_PATH);
    let mergeState = { glyphSheets: {} };
    if (stateEntry) {
        try {
            mergeState = JSON.parse(zip.readAsText(stateEntry));
            if (!mergeState.glyphSheets) mergeState.glyphSheets = {};
        } catch {
            mergeState = { glyphSheets: {} };
        }
    }

    const warnings = [];
    const added = [];
    const updated = [];
    let anyChanged = false;

    for (const sheet of sheets) {
        const newHash = sha256(sheet.bytes);
        const existingEntry = zip.getEntry(sheet.entryPath);
        const priorHash = mergeState.glyphSheets[sheet.entryPath];

        if (!existingEntry) {
            zip.addFile(sheet.entryPath, sheet.bytes);
            added.push(sheet.entryPath);
            anyChanged = true;
        } else {
            const existingHash = sha256(existingEntry.getData());
            if (existingHash === newHash) {
                // Already up to date, nothing to do.
            } else {
                if (priorHash === undefined) {
                    warnings.push(`${sheet.entryPath} already existed in ${basename(args.pack)} and wasn't written by a previous `
                        + `ArchFlags merge -- overwriting it anyway (ArchFlags owns this codepoint range), but double-check nothing `
                        + `else is using it.`);
                } else if (priorHash !== existingHash) {
                    warnings.push(`${sheet.entryPath} was modified since the last ArchFlags merge (doesn't match what ArchFlags `
                        + `last wrote) -- overwriting with the newly generated version.`);
                }
                zip.updateFile(sheet.entryPath, sheet.bytes);
                updated.push(sheet.entryPath);
                anyChanged = true;
            }
        }
        mergeState.glyphSheets[sheet.entryPath] = newHash;
    }

    let oldVersion = null;
    let newVersion = null;
    if (anyChanged) {
        oldVersion = manifest.header?.version;
        newVersion = bumpVersion(manifest.header?.version, "header.version", warnings);
        if (manifest.header) manifest.header.version = newVersion;
        if (Array.isArray(manifest.modules)) {
            for (const mod of manifest.modules) {
                mod.version = bumpVersion(mod.version, `module "${mod.type ?? "?"}".version`, warnings);
            }
        }
        zip.updateFile("manifest.json", Buffer.from(JSON.stringify(manifest, null, 2)));
    }

    mergeState.lastMergedAt = new Date().toISOString();
    mergeState.codepointRange = "E200-E2F9"; // informational only -- see config.yml glyphs.start-codepoint
    if (stateEntry) {
        zip.updateFile(MERGE_STATE_PATH, Buffer.from(JSON.stringify(mergeState, null, 2)));
    } else {
        zip.addFile(MERGE_STATE_PATH, Buffer.from(JSON.stringify(mergeState, null, 2)));
    }

    const ext = extname(args.pack);
    const defaultOut = join(dirname(args.pack), `${basename(args.pack, ext)}-merged${ext}`);
    const outPath = args.inPlace ? args.pack : (args.out || defaultOut);
    zip.writeZip(outPath);

    console.log(`Merged ${sheets.length} ArchFlags glyph sheet(s) into ${basename(args.pack)}.`);
    if (added.length) console.log(`  Added:   ${added.join(", ")}`);
    if (updated.length) console.log(`  Updated: ${updated.join(", ")}`);
    if (!anyChanged) console.log("  No content changes -- glyph sheets already matched, manifest version left as-is.");
    if (anyChanged && newVersion) console.log(`  Manifest version: ${JSON.stringify(oldVersion)} -> ${JSON.stringify(newVersion)}`);
    for (const w of warnings) console.log(`  WARNING: ${w}`);
    console.log(`Wrote: ${outPath}`);
    if (!args.inPlace) {
        console.log("This is a NEW file -- your original pack was not modified. Verify it, then copy/rename it into");
        console.log("Geyser-Velocity/packs/ as your single active Bedrock pack (remove any separate ArchFlags-Bedrock.zip");
        console.log("from an older ArchFlags version if one is still there).");
    }
}

main();
