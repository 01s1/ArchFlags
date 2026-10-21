package gg.arch.archflags.data;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * LuckPerms is a softdepend: ArchFlags must start and keep working without it. The LuckPerms API is
 * compileOnly, so it is NOT on this test's runtime classpath - exactly like a server without LuckPerms.
 * 1.2.2-rc1 failed here with NoClassDefFoundError: net/luckperms/api/node/Node.
 */
class FlagVisibilityStoreTest {

    @Test
    void luckPermsIsAbsentFromTheTestClasspath() {
        assertThrows(ClassNotFoundException.class, () -> Class.forName("net.luckperms.api.node.Node"));
    }

    @Test
    void storeLoadsAndWorksWithoutLuckPerms() {
        FlagVisibilityStore store = assertDoesNotThrow(() -> new FlagVisibilityStore(() -> "archflags.visible"));
        UUID player = UUID.randomUUID();

        assertFalse(store.isLuckPermsAvailable());
        assertTrue(store.isVisible(player), "flags are visible by default");

        store.setVisible(player, false).join();
        assertFalse(store.isVisible(player));
        assertFalse(store.isVisibleAsync(player).join());

        store.setVisible(player, true).join();
        assertTrue(store.isVisible(player));
    }

    @Test
    void onlyTheLuckPermsBackendNamesLuckPermsTypes() throws IOException {
        Path data = Path.of("src/main/java/gg/arch/archflags/data");
        try (var files = Files.list(data)) {
            for (Path file : files.toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                boolean namesLuckPerms = source.contains("net.luckperms");
                boolean isBackend = file.getFileName().toString().equals("LuckPermsVisibility.java");
                assertTrue(!namesLuckPerms || isBackend, file + " must not reference LuckPerms types;"
                        + " only LuckPermsVisibility (loaded after LuckPerms is found) may");
            }
        }
    }
}
