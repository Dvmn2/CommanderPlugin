package net.dvmn2.commanderplugin.model;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Data of one event occurrence, captured on the main thread when the event fires.
 *
 * @param event     the event
 * @param player    the player, or null for events without a player
 * @param worldName world used for the world condition, or null
 * @param values    raw placeholder values; validated before use
 */
public record TriggerContext(
        TriggerEvent event,
        @Nullable Player player,
        @Nullable String worldName,
        Map<Placeholder, String> values) {

    public TriggerContext {
        values = Map.copyOf(values);
    }
}
