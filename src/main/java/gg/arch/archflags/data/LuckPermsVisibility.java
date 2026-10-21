package gg.arch.archflags.data;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.PermissionNode;

/**
 * Flag visibility as a plain LuckPerms permission node on the player (default key "archflags.visible",
 * configurable) -- NOT a group, prefix, suffix, or new database. LuckPerms' own storage, shared across
 * the backends, makes it follow the player network-wide. Only nodes with exactly this key are touched.
 * <p>
 * The only class that names LuckPerms types: {@link FlagVisibilityStore} reaches it through
 * {@link #connect} after checking that LuckPerms is enabled, so the JVM never loads it without LuckPerms.
 */
final class LuckPermsVisibility implements VisibilityBackend {

    private final LuckPerms luckPerms;
    private final Supplier<String> nodeKey;

    private LuckPermsVisibility(LuckPerms luckPerms, Supplier<String> nodeKey) {
        this.luckPerms = luckPerms;
        this.nodeKey = nodeKey;
    }

    /** The backend, or null when LuckPerms is installed but its API is not ready. */
    static VisibilityBackend connect(Supplier<String> nodeKey) {
        try {
            return new LuckPermsVisibility(LuckPermsProvider.get(), nodeKey);
        } catch (IllegalStateException ex) {
            return null;
        }
    }

    @Override
    public Boolean visibleIfLoaded(UUID uuid) {
        User user = luckPerms.getUserManager().getUser(uuid);
        return user == null ? null : read(user);
    }

    @Override
    public CompletableFuture<Boolean> visibleAsync(UUID uuid) {
        UserManager userManager = luckPerms.getUserManager();
        User online = userManager.getUser(uuid);
        if (online != null) {
            return CompletableFuture.completedFuture(read(online));
        }
        return userManager.loadUser(uuid).thenApply(this::read);
    }

    @Override
    public CompletableFuture<Void> setVisible(UUID uuid, boolean visible) {
        UserManager userManager = luckPerms.getUserManager();
        User cached = userManager.getUser(uuid);
        if (cached != null) {
            return applyAndSave(userManager, cached, visible);
        }
        return userManager.loadUser(uuid).thenCompose(user -> applyAndSave(userManager, user, visible));
    }

    private boolean read(User user) {
        String key = nodeKey.get();
        Boolean found = null;
        for (Node node : user.getNodes(NodeType.PERMISSION)) {
            if (node.getKey().equalsIgnoreCase(key)) {
                found = node.getValue();
            }
        }
        return found == null || found;
    }

    private CompletableFuture<Void> applyAndSave(UserManager userManager, User user, boolean visible) {
        String key = nodeKey.get();
        user.data().clear(node -> node.getType() == NodeType.PERMISSION
                && ((PermissionNode) node).getPermission().equalsIgnoreCase(key));
        user.data().add(PermissionNode.builder(key).value(visible).build());
        return userManager.saveUser(user);
    }
}
