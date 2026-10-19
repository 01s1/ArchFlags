# ArchFlags 1.2.2 TEST plan

Run on the TEST server only. Restart the server for every JAR or config change; never use
`/reload` or PlugMan. Test with at least one Java client and one Bedrock client.

## Before installing

1. Stop TEST. Back up the old ArchFlags JAR and the whole `plugins/ArchFlags/` folder.
2. Install the new JAR. Keep the existing `config.yml` (it is migrated on start).

## Proposed TEST configuration

The flag font is merged into the NxRanks Java pack, and ArchFlags does not send its own Java pack
(README "Java pack", merged mode). Everything else stays at its current value.

```yaml
# plugins/ArchFlags/config.yml
display:
  mode: CUSTOM_GLYPH
resourcepack:
  java:
    enabled: false
```

- NxRanks: `resource-pack.enabled: true`, with `resource-pack.url` pointing at the merged pack
  (the current NxRanks Java pack plus `assets/archflags/`).
- Geyser: exactly one Bedrock pack, the one that already contains `glyph_E2.png`.
- TAB `groups.yml`: `tagsuffix` ends with `%archflags_nametag_suffix%` in `_DEFAULT_` and in
  every group that overrides `tagsuffix`. TAB `components.minimessage-support: true`.

## Checklist

| # | Area | Steps | Expected |
|---|---|---|---|
| 1 | Config migration | Start with a 1.2.1 (or older) `config.yml`. | Console: "Migrated config.yml from version 1 to 2", the backup name and the added keys. `config.yml.v1-<time>.bak` equals the old file. Existing values unchanged. |
| 2 | Migration is one-time | Restart again. | No migration line, no new `.bak`. |
| 3 | Status | `/archflags status` | GeoIP loaded with type and build date; Java pack delivery mode `disabled`; no NxRanks warning. |
| 4 | Java flag | Join with Java. | Only NxRanks' pack prompt. Flag after the name in the nametag; rank glyphs intact. |
| 5 | Java persistence | Press F3+T; `/server` HUB → SMP → SURVIVAL. | Flag and rank glyphs still render everywhere. |
| 6 | Bedrock flag | Join with Bedrock. | Flag glyph and rank glyphs render; no Java pack is sent. |
| 7 | `/flag` | `/flag off`, `/flag on`, `/flag toggle`, `/flag status`, `/flag banana`, `/flags`, tab completion. | Nametag updates immediately for Java and Bedrock viewers; status shows ON/OFF; `banana` shows usage. |
| 8 | No-op toggle | `/flag on` while already on. | Success message, nothing written to LuckPerms (`/lp user <name> permission info` unchanged). |
| 9 | Network-wide | `/flag off` on HUB, then switch to SMP. | Still off on SMP. |
| 10 | GeoIP recovery | Move the `.mmdb` away, `/archflags reload`; put it back, `/archflags reload`. | First reload: warning, players show Unknown. Second: players resolve without relogging. |
| 11 | GeoIP age | Check the console after loading. | Build date logged; a warning if the file is older than 30 days. |
| 12 | Lookup | `/archflags lookup <online player>`, then a name that never joined. | Country shown for the first; instant "Player not found" for the second (no lag spike). |
| 13 | ArchSettings | Toggle the flag setting in the ArchSettings menu. | Same effect as `/flag`. |
| 14 | Conflict warning | Temporarily set `resourcepack.java.enabled: true` (with a valid URL) and restart. | One tick after start: warning that NxRanks removes the separate pack; the same warning in `/archflags status`. Set it back to `false`. |

## Rollback

Stop TEST, restore the old JAR and the backed-up `plugins/ArchFlags/` folder (or the
`config.yml.v1-*.bak` file as `config.yml`), and start again.
