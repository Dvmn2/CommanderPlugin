package net.dvmn2.commanderplugin;

import net.dvmn2.commanderplugin.model.Placeholder;
import net.dvmn2.commanderplugin.model.TriggerContext;
import net.dvmn2.commanderplugin.model.TriggerEvent;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.server.ServerLoadEvent;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Translates Bukkit events into {@link TriggerContext}s. Main thread only.
 *
 * <p>Handlers only collect data and call {@link ExecutionManager}. {@code kick} fires together with
 * {@code quit} (the quit reason tells that the player was kicked), so it reports the final outcome even
 * if another plugin cancelled the kick.
 */
public final class CommanderListener implements Listener {

    /** Advancements under this path are recipe unlocks, not real advancements. */
    private static final String RECIPE_ADVANCEMENT_PREFIX = "recipes/";

    private final ExecutionManager executions;

    public CommanderListener(ExecutionManager executions) {
        this.executions = executions;
    }

    @EventHandler(ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        firePlain(TriggerEvent.JOIN, player);
        if (!player.hasPlayedBefore()) {
            firePlain(TriggerEvent.FIRST_JOIN, player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        firePlain(TriggerEvent.QUIT, player);
        if (event.getReason() == PlayerQuitEvent.QuitReason.KICKED) {
            firePlain(TriggerEvent.KICK, player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Map<Placeholder, String> extra = new EnumMap<>(Placeholder.class);
        Player killer = player.getKiller();
        if (killer != null) {
            extra.put(Placeholder.KILLER, killer.getName());
        }
        executions.fire(playerContext(TriggerEvent.DEATH, player, player.getLocation(), extra));
    }

    @EventHandler(ignoreCancelled = true)
    public void onRespawn(PlayerRespawnEvent event) {
        executions.fire(playerContext(TriggerEvent.RESPAWN, event.getPlayer(), event.getRespawnLocation(),
                new EnumMap<>(Placeholder.class)));
    }

    @EventHandler(ignoreCancelled = true)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        Map<Placeholder, String> extra = new EnumMap<>(Placeholder.class);
        extra.put(Placeholder.FROM_WORLD, event.getFrom().getName());
        extra.put(Placeholder.TO_WORLD, player.getWorld().getName());
        executions.fire(playerContext(TriggerEvent.WORLD_CHANGE, player, player.getLocation(), extra));
    }

    @EventHandler(ignoreCancelled = true)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        NamespacedKey key = event.getAdvancement().getKey();
        if (key.getKey().startsWith(RECIPE_ADVANCEMENT_PREFIX)) {
            return;
        }
        Player player = event.getPlayer();
        Map<Placeholder, String> extra = new EnumMap<>(Placeholder.class);
        extra.put(Placeholder.ADVANCEMENT, key.asString());
        executions.fire(playerContext(TriggerEvent.ADVANCEMENT, player, player.getLocation(), extra));
    }

    @EventHandler(ignoreCancelled = true)
    public void onServerLoad(ServerLoadEvent event) {
        if (event.getType() == ServerLoadEvent.LoadType.STARTUP) {
            executions.fire(new TriggerContext(TriggerEvent.SERVER_START, null, null,
                    new EnumMap<>(Placeholder.class)));
        }
    }

    private void firePlain(TriggerEvent event, Player player) {
        executions.fire(playerContext(event, player, player.getLocation(), new EnumMap<>(Placeholder.class)));
    }

    private static TriggerContext playerContext(
            TriggerEvent event, Player player, Location location, Map<Placeholder, String> extra) {
        String worldName = Objects.requireNonNullElse(location.getWorld(), player.getWorld()).getName();
        Map<Placeholder, String> values = new EnumMap<>(Placeholder.class);
        values.put(Placeholder.PLAYER, player.getName());
        values.put(Placeholder.UUID, player.getUniqueId().toString());
        values.put(Placeholder.WORLD, worldName);
        values.put(Placeholder.X, String.valueOf(location.getBlockX()));
        values.put(Placeholder.Y, String.valueOf(location.getBlockY()));
        values.put(Placeholder.Z, String.valueOf(location.getBlockZ()));
        values.putAll(extra);
        return new TriggerContext(event, player, worldName, values);
    }
}
