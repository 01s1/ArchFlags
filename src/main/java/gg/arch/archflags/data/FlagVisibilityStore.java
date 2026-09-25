package gg.arch.archflags.data;

import gg.arch.archflags.ArchFlagsPlugin;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.PermissionNode;
import org.bukkit.Bukkit;

/**
 * Persists flag visibility as a plain LuckPerms permission node on the player (default node key
 * "archflags.visible", configurable) -- NOT a group, prefix, suffix, or new database. LuckPerms'
 * own MySQL storage, already shared across Hub/Survival/SMP, is what makes this follow the player
 * network-wide. Only nodes with exactly this key are ever touched; nothing else on the user is
 * read or written.
 * <p>
 * Falls back to an in-memory-only (per-server, non-persistent) default of "visible" if LuckPerms
 * is not installed, so the rest of the plugin keeps working.
 */
public final class FlagVisibilityStore {

    private final ArchFlagsPlugin plugin;
    private final ConcurrentHashMap<UUID, Boolean> fallbackCache = new ConcurrentHashMap<>();
    private LuckPerms luckPerms;

    public FlagVisibilityStore(ArchFlagsPlugin plugin) {
        this.plugin = plugin;
    }

    public void hookLuckPerms() {
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
            luckPerms = null;
            return;
        }
        try {
            luckPerms = LuckPermsProvider.get();
        } catch (IllegalStateException ex) {
            luckPerms = null;
        }
    }

    public boolean isLuckPermsAvailable() {
        return luckPerms != null;
    }

    private String nodeKey() {
        return plugin.archConfig().luckPermsPreferenceNode();
    }

    /** Reads visibility for an already-loaded (online) player. Defaults to true (visible). */
    public boolean isVisible(UUID uuid) {
        if (luckPerms == null) {
            return fallbackCache.getOrDefault(uuid, true);
        }
        User user = luckPerms.getUserManager().getUser(uuid);
        if (user == null) {
            return fallbackCache.getOrDefault(uuid, true);
        }
        return readVisibility(user);
    }

    private boolean readVisibility(User user) {
        String key = nodeKey();
        Boolean found = null;
        for (Node node : user.getNodes(NodeType.PERMISSION)) {
            if (node.getKey().equalsIgnoreCase(key)) {
                found = node.getValue();
            }
        }
        return found == null || found;
    }

    /** Async lookup that also works for offline players (used by /archflags lookup). */
    public CompletableFuture<Boolean> isVisibleAsync(UUID uuid) {
        if (luckPerms == null) {
            return CompletableFuture.completedFuture(fallbackCache.getOrDefault(uuid, true));
        }
        UserManager userManager = luckPerms.getUserManager();
        User online = userManager.getUser(uuid);
        if (online != null) {
            return CompletableFuture.completedFuture(readVisibility(online));
        }
        return userManager.loadUser(uuid).thenApply(this::readVisibility);
    }

    /** Sets and persists visibility. Completes once the write has been saved to LuckPerms' storage. */
    public CompletableFuture<Void> setVisible(UUID uuid, boolean visible) {
        if (luckPerms == null) {
            fallbackCache.put(uuid, visible);
            return CompletableFuture.completedFuture(null);
        }

        UserManager userManager = luckPerms.getUserManager();
        User cached = userManager.getUser(uuid);
        if (cached != null) {
            applyAndSave(userManager, cached, visible);
            return CompletableFuture.completedFuture(null);
        }
        return userManager.loadUser(uuid).thenAccept(user -> applyAndSave(userManager, user, visible));
    }

    private void applyAndSave(UserManager userManager, User user, boolean visible) {
        String key = nodeKey();
        user.data().clear(node -> node.getType() == NodeType.PERMISSION
                && ((PermissionNode) node).getPermission().equalsIgnoreCase(key));
        user.data().add(PermissionNode.builder(key).value(visible).build());
        userManager.saveUser(user);
    }
}
