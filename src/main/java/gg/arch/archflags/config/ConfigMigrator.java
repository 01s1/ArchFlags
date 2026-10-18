package gg.arch.archflags.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * One-time, additive upgrade of an existing config.yml to the bundled layout. Bukkit never copies
 * new default keys into a file that already exists, so a config created by an older ArchFlags
 * version silently lacks every key added since (e.g. 1.0.0 configs have no
 * {@code messages.flag-status-on}, {@code resourcepack} or {@code bedrock-detection} sections).
 * <p>
 * Only missing keys are added (with their bundled comments); existing values are never changed or
 * removed. The original file is backed up first and never overwritten.
 */
public final class ConfigMigrator {

    /** Bump when the bundled config.yml gains keys that existing files should receive. */
    public static final int CURRENT_VERSION = 2;
    public static final String VERSION_KEY = "config-version";

    private static final DateTimeFormatter BACKUP_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private ConfigMigrator() {
    }

    /** Result of a migration attempt, for logging and tests. */
    public record Result(int fromVersion, List<String> addedKeys, File backup) {
        public boolean migrated() {
            return backup != null;
        }
    }

    /**
     * Migrates {@code configFile} in place if its {@code config-version} is below
     * {@link #CURRENT_VERSION}. A missing file is left alone (Bukkit's saveDefaultConfig creates it).
     */
    public static Result migrate(File configFile, YamlConfiguration defaults, Logger logger)
            throws IOException, InvalidConfigurationException {
        if (!configFile.isFile()) {
            return new Result(CURRENT_VERSION, List.of(), null);
        }
        YamlConfiguration current = new YamlConfiguration();
        current.load(configFile);

        int fromVersion = current.getInt(VERSION_KEY, 1);
        if (fromVersion >= CURRENT_VERSION) {
            return new Result(fromVersion, List.of(), null);
        }

        File backup = backupFile(configFile, fromVersion);
        Files.copy(configFile.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);

        List<String> added = addMissingKeys(current, defaults);
        current.set(VERSION_KEY, CURRENT_VERSION);
        current.setComments(VERSION_KEY, defaults.getComments(VERSION_KEY));
        current.save(configFile);

        logger.info("Migrated config.yml from version " + fromVersion + " to " + CURRENT_VERSION
                + "; backup saved as " + backup.getName() + ". Added " + added.size() + " missing key(s)"
                + (added.isEmpty() ? "." : ": " + String.join(", ", added)));
        return new Result(fromVersion, added, backup);
    }

    /**
     * Copies every leaf key present in {@code defaults} but absent from {@code target}, together
     * with its comments. Never overwrites a value the target already has, including a section the
     * target replaced with a scalar. Returns the added paths in document order.
     */
    public static List<String> addMissingKeys(YamlConfiguration target, YamlConfiguration defaults) {
        List<String> added = new ArrayList<>();
        for (String path : defaults.getKeys(true)) {
            if (path.equals(VERSION_KEY) || target.contains(path, true)) {
                continue;
            }
            if (defaults.isConfigurationSection(path)) {
                // Create the section explicitly so its own header comment survives; its leaf keys
                // follow later in getKeys(true) order.
                if (hasScalarAncestor(target, path)) {
                    continue;
                }
                target.createSection(path);
                target.setComments(path, defaults.getComments(path));
                continue;
            }
            if (hasScalarAncestor(target, path)) {
                continue;
            }
            target.set(path, defaults.get(path));
            target.setComments(path, defaults.getComments(path));
            added.add(path);
        }
        return added;
    }

    private static boolean hasScalarAncestor(YamlConfiguration target, String path) {
        int dot = path.lastIndexOf('.');
        while (dot > 0) {
            String parent = path.substring(0, dot);
            if (target.contains(parent, true) && !target.isConfigurationSection(parent)) {
                return true;
            }
            dot = parent.lastIndexOf('.');
        }
        return false;
    }

    private static File backupFile(File configFile, int fromVersion) {
        String base = configFile.getName() + ".v" + fromVersion + "-" + LocalDateTime.now().format(BACKUP_STAMP) + ".bak";
        File candidate = new File(configFile.getParentFile(), base);
        int n = 1;
        while (candidate.exists()) {
            candidate = new File(configFile.getParentFile(), base + "." + n++);
        }
        return candidate;
    }
}
