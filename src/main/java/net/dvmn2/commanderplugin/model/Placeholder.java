package net.dvmn2.commanderplugin.model;

import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * A {@code {token}} that can be used inside rule commands.
 *
 * <p>Every value is validated against a strict pattern before substitution, so event data can never
 * inject extra arguments or commands.
 */
public enum Placeholder {
    PLAYER("player", Patterns.PLAYER_NAME),
    UUID("uuid", Patterns.UUID),
    WORLD("world", Patterns.WORLD_NAME),
    X("x", Patterns.INTEGER),
    Y("y", Patterns.INTEGER),
    Z("z", Patterns.INTEGER),
    KILLER("killer", Patterns.PLAYER_NAME),
    FROM_WORLD("from-world", Patterns.WORLD_NAME),
    TO_WORLD("to-world", Patterns.WORLD_NAME),
    ADVANCEMENT("advancement", Patterns.RESOURCE_ID);

    private final String key;
    private final Pattern valuePattern;

    Placeholder(String key, Pattern valuePattern) {
        this.key = key;
        this.valuePattern = valuePattern;
    }

    /** Returns the token name without braces, e.g. {@code from-world}. */
    public String getKey() {
        return key;
    }

    /** Returns true if the value is safe to substitute into a command. */
    public boolean isValid(String value) {
        return valuePattern.matcher(value).matches();
    }

    /** Finds a placeholder by its token name, or returns null. */
    @Nullable
    public static Placeholder fromKey(String key) {
        for (Placeholder placeholder : values()) {
            if (placeholder.key.equals(key)) {
                return placeholder;
            }
        }
        return null;
    }
}
