package net.dvmn2.commanderplugin.model;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Server events that can trigger a {@link Rule}, with the placeholders each one provides. */
public enum TriggerEvent {
    JOIN("join", true),
    FIRST_JOIN("first-join", true),
    QUIT("quit", true),
    KICK("kick", true),
    DEATH("death", true, Placeholder.KILLER),
    RESPAWN("respawn", true),
    WORLD_CHANGE("world-change", true, Placeholder.FROM_WORLD, Placeholder.TO_WORLD),
    ADVANCEMENT("advancement", true, Placeholder.ADVANCEMENT),
    SERVER_START("server-start", false);

    private final String key;
    private final boolean playerBased;
    private final Set<Placeholder> placeholders;

    TriggerEvent(String key, boolean playerBased, Placeholder... extra) {
        this.key = key;
        this.playerBased = playerBased;
        EnumSet<Placeholder> set = EnumSet.noneOf(Placeholder.class);
        if (playerBased) {
            set.addAll(List.of(Placeholder.PLAYER, Placeholder.UUID, Placeholder.WORLD,
                    Placeholder.X, Placeholder.Y, Placeholder.Z));
        }
        set.addAll(List.of(extra));
        this.placeholders = Collections.unmodifiableSet(set);
    }

    /** Returns the kebab-case name used in commands and in {@code rules.json}. */
    public String getKey() {
        return key;
    }

    /** Returns true if the event has a player, so permission and world conditions apply. */
    public boolean isPlayerBased() {
        return playerBased;
    }

    /** Returns true if the placeholder has a value for this event. */
    public boolean supports(Placeholder placeholder) {
        return placeholders.contains(placeholder);
    }

    /** Finds an event by its key (case-insensitive), or returns null. */
    @Nullable
    public static TriggerEvent fromKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        for (TriggerEvent event : values()) {
            if (event.key.equals(normalized)) {
                return event;
            }
        }
        return null;
    }
}
