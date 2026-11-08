package net.dvmn2.commanderplugin.model;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * One rule: when any of {@code events} happens and the conditions match, run {@code commands} as the console.
 *
 * <p>Immutable. {@code permission} is an empty string and {@code worlds} is an empty list when the
 * condition is not set. Conditions are ignored for events without a player.
 *
 * @param name         unique lowercase name, see {@link #isValidName(String)}
 * @param enabled      disabled rules never run
 * @param events       triggering events
 * @param delaySeconds delay before the commands run, seconds, {@code 0..}{@link #MAX_DELAY_SECONDS}
 * @param permission   permission the player must have, or an empty string
 * @param worlds       worlds the player must be in (any if empty)
 * @param commands     commands without the leading slash, may contain {@link Placeholder} tokens
 */
public record Rule(
        String name,
        boolean enabled,
        Set<TriggerEvent> events,
        double delaySeconds,
        String permission,
        List<String> worlds,
        List<String> commands) {

    /** Upper bound for the delay, seconds (24 hours). */
    public static final double MAX_DELAY_SECONDS = 86_400.0;

    /** Maximum length of one command, characters. */
    public static final int MAX_COMMAND_LENGTH = 256;

    public Rule {
        EnumSet<TriggerEvent> eventCopy = EnumSet.noneOf(TriggerEvent.class);
        eventCopy.addAll(events);
        events = Collections.unmodifiableSet(eventCopy);
        if (Double.isNaN(delaySeconds) || delaySeconds < 0) {
            delaySeconds = 0;
        } else if (delaySeconds > MAX_DELAY_SECONDS) {
            delaySeconds = MAX_DELAY_SECONDS;
        }
        permission = permission.strip();
        worlds = List.copyOf(worlds);
        commands = List.copyOf(commands);
    }

    /** Creates an enabled rule with no events, conditions or commands. */
    public static Rule create(String name) {
        return new Rule(name, true, Set.of(), 0, "", List.of(), List.of());
    }

    public Rule withEnabled(boolean value) {
        return new Rule(name, value, events, delaySeconds, permission, worlds, commands);
    }

    public Rule withEvents(Set<TriggerEvent> value) {
        return new Rule(name, enabled, value, delaySeconds, permission, worlds, commands);
    }

    public Rule withDelaySeconds(double value) {
        return new Rule(name, enabled, events, value, permission, worlds, commands);
    }

    public Rule withPermission(String value) {
        return new Rule(name, enabled, events, delaySeconds, value, worlds, commands);
    }

    public Rule withWorlds(List<String> value) {
        return new Rule(name, enabled, events, delaySeconds, permission, value, commands);
    }

    public Rule withCommands(List<String> value) {
        return new Rule(name, enabled, events, delaySeconds, permission, worlds, value);
    }

    /** Returns true for 1..32 characters from {@code a-z 0-9 _ -}. */
    public static boolean isValidName(String value) {
        return Patterns.RULE_NAME.matcher(value).matches();
    }

    /** Returns true for a plausible world name; the world does not have to exist. */
    public static boolean isValidWorld(String value) {
        return Patterns.WORLD_NAME.matcher(value).matches();
    }

    /** Returns true for a plausible permission node. */
    public static boolean isValidPermission(String value) {
        return Patterns.PERMISSION.matcher(value).matches();
    }

    /**
     * Normalizes a command: trims it and removes leading slashes.
     *
     * @return the command, or null if it is empty, too long or contains control characters
     */
    @Nullable
    public static String normalizeCommand(String raw) {
        String command = raw.strip();
        while (command.startsWith("/")) {
            command = command.substring(1).stripLeading();
        }
        if (command.isEmpty() || command.length() > MAX_COMMAND_LENGTH) {
            return null;
        }
        for (int i = 0; i < command.length(); i++) {
            if (Character.isISOControl(command.charAt(i))) {
                return null;
            }
        }
        return command;
    }
}
