package net.dvmn2.commanderplugin.util;

import net.dvmn2.commanderplugin.model.Placeholder;
import net.dvmn2.commanderplugin.model.TriggerEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds and substitutes {@code {token}} placeholders in command templates. Stateless. */
public final class Placeholders {

    private static final Pattern TOKEN = Pattern.compile("\\{([a-z-]+)\\}");

    private Placeholders() {
    }

    /**
     * Finds a token that is unknown or not provided by at least one of the events.
     *
     * @return the offending token including braces, or null if the command is fine
     */
    @Nullable
    public static String firstUnsupported(String command, Set<TriggerEvent> events) {
        Matcher matcher = TOKEN.matcher(command);
        while (matcher.find()) {
            Placeholder placeholder = Placeholder.fromKey(matcher.group(1));
            if (placeholder == null) {
                return matcher.group();
            }
            for (TriggerEvent event : events) {
                if (!event.supports(placeholder)) {
                    return matcher.group();
                }
            }
        }
        return null;
    }

    /**
     * Replaces every token with its value.
     *
     * @return the command, or null if some token has no value
     */
    @Nullable
    public static String substitute(String command, Map<Placeholder, String> values) {
        Matcher matcher = TOKEN.matcher(command);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            Placeholder placeholder = Placeholder.fromKey(matcher.group(1));
            String value = placeholder == null ? null : values.get(placeholder);
            if (value == null) {
                return null;
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
