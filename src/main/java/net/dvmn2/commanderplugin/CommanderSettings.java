package net.dvmn2.commanderplugin;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Immutable snapshot of {@code config.yml}.
 *
 * @param language           message language: {@code ru}, {@code en} or {@code auto}
 * @param maxRules           maximum number of rules that can be created by command
 * @param maxCommandsPerRule maximum number of commands per rule when adding by command
 */
public record CommanderSettings(String language, int maxRules, int maxCommandsPerRule) {

    private static final String ROOT = "settings.";
    private static final String DEFAULT_LANGUAGE = "auto";
    private static final Set<String> LANGUAGES = Set.of("ru", "en", "auto");
    private static final int DEFAULT_MAX_RULES = 100;
    private static final int MAX_RULES_LIMIT = 1000;
    private static final int DEFAULT_MAX_COMMANDS = 20;
    private static final int MAX_COMMANDS_LIMIT = 100;

    /** Reads the snapshot; invalid values are logged and replaced with defaults. */
    public static CommanderSettings parse(ConfigurationSection config, Logger log) {
        String language = config.getString(ROOT + "language", DEFAULT_LANGUAGE).toLowerCase(Locale.ROOT);
        if (!LANGUAGES.contains(language)) {
            log.warning(ROOT + "language must be ru, en or auto, got '" + language
                    + "'; using " + DEFAULT_LANGUAGE);
            language = DEFAULT_LANGUAGE;
        }
        return new CommanderSettings(
                language,
                readRange(config, log, "max-rules", DEFAULT_MAX_RULES, MAX_RULES_LIMIT),
                readRange(config, log, "max-commands-per-rule", DEFAULT_MAX_COMMANDS, MAX_COMMANDS_LIMIT));
    }

    private static int readRange(ConfigurationSection config, Logger log, String key, int fallback, int max) {
        int value = config.getInt(ROOT + key, fallback);
        if (value < 1 || value > max) {
            log.warning(ROOT + key + " must be in 1.." + max + ", got " + value + "; using " + fallback);
            return fallback;
        }
        return value;
    }
}
