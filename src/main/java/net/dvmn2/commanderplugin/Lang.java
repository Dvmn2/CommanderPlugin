package net.dvmn2.commanderplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/** Localised MiniMessage texts (RU and EN). The only class with static mutable state: the current language. */
public final class Lang {

    public enum Key {
        RELOADED,
        STATE_ON,
        STATE_OFF,
        VALUE_NONE,
        VALUE_ALL,
        LIST_HEADER,
        LIST_EMPTY,
        LIST_ENTRY,
        INFO_HEADER,
        INFO_EVENTS,
        INFO_DELAY,
        INFO_PERMISSION,
        INFO_WORLDS,
        INFO_COMMANDS_HEADER,
        INFO_COMMAND,
        RULE_CREATED,
        RULE_DELETED,
        RULE_ENABLED,
        RULE_DISABLED,
        DELAY_SET,
        PERMISSION_SET,
        PERMISSION_CLEARED,
        EVENT_ADDED,
        EVENT_REMOVED,
        WORLD_ADDED,
        WORLD_REMOVED,
        COMMAND_ADDED,
        COMMAND_REMOVED,
        ERR_NOT_FOUND,
        ERR_ALREADY_EXISTS,
        ERR_INVALID_NAME,
        ERR_INVALID_VALUE,
        ERR_INVALID_INDEX,
        ERR_UNSUPPORTED_PLACEHOLDER,
        ERR_ALREADY_PRESENT,
        ERR_NOT_PRESENT,
        ERR_LIMIT_REACHED,
        ERR_UNKNOWN_EVENT
    }

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Map<Key, String> RU = new EnumMap<>(Key.class);
    private static final Map<Key, String> EN = new EnumMap<>(Key.class);

    private static volatile String language = "auto";

    static {
        put(Key.RELOADED,
                "<green>Конфигурация и правила перезагружены. Правил: <count>.",
                "<green>Configuration and rules reloaded. Rules: <count>.");
        put(Key.STATE_ON, "<green>[вкл]", "<green>[on]");
        put(Key.STATE_OFF, "<red>[выкл]", "<red>[off]");
        put(Key.VALUE_NONE, "<gray>нет", "<gray>none");
        put(Key.VALUE_ALL, "<gray>все", "<gray>all");

        put(Key.LIST_HEADER,
                "<gold>Правила (<count>):",
                "<gold>Rules (<count>):");
        put(Key.LIST_EMPTY,
                "<gray>Правил пока нет. Создайте первое командой /commander create.",
                "<gray>There are no rules yet. Create one with /commander create.");
        put(Key.LIST_ENTRY,
                "<state> <yellow><name></yellow> <gray>- событий: <events>, команд: <commands>",
                "<state> <yellow><name></yellow> <gray>- events: <events>, commands: <commands>");

        put(Key.INFO_HEADER,
                "<gold>Правило <yellow><name></yellow> <state>",
                "<gold>Rule <yellow><name></yellow> <state>");
        put(Key.INFO_EVENTS,
                "<gray>События: <white><value>",
                "<gray>Events: <white><value>");
        put(Key.INFO_DELAY,
                "<gray>Задержка: <white><value> с",
                "<gray>Delay: <white><value> s");
        put(Key.INFO_PERMISSION,
                "<gray>Требуемое право: <white><value>",
                "<gray>Required permission: <white><value>");
        put(Key.INFO_WORLDS,
                "<gray>Миры: <white><value>",
                "<gray>Worlds: <white><value>");
        put(Key.INFO_COMMANDS_HEADER,
                "<gray>Команды (от имени консоли):",
                "<gray>Commands (run as the console):");
        put(Key.INFO_COMMAND,
                "<gray><index>. <white>/<command>",
                "<gray><index>. <white>/<command>");

        put(Key.RULE_CREATED,
                "<green>Правило <yellow><name></yellow> создано.",
                "<green>Rule <yellow><name></yellow> created.");
        put(Key.RULE_DELETED,
                "<green>Правило <yellow><name></yellow> удалено.",
                "<green>Rule <yellow><name></yellow> deleted.");
        put(Key.RULE_ENABLED,
                "<green>Правило <yellow><name></yellow> включено.",
                "<green>Rule <yellow><name></yellow> enabled.");
        put(Key.RULE_DISABLED,
                "<green>Правило <yellow><name></yellow> выключено.",
                "<green>Rule <yellow><name></yellow> disabled.");
        put(Key.DELAY_SET,
                "<green>Задержка правила <yellow><name></yellow>: <seconds> с.",
                "<green>Delay of rule <yellow><name></yellow>: <seconds> s.");
        put(Key.PERMISSION_SET,
                "<green>Правило <yellow><name></yellow> теперь требует право <yellow><permission></yellow>.",
                "<green>Rule <yellow><name></yellow> now requires permission <yellow><permission></yellow>.");
        put(Key.PERMISSION_CLEARED,
                "<green>Требование права для правила <yellow><name></yellow> снято.",
                "<green>Permission requirement of rule <yellow><name></yellow> removed.");
        put(Key.EVENT_ADDED,
                "<green>Событие <yellow><event></yellow> добавлено в правило <yellow><name></yellow>.",
                "<green>Event <yellow><event></yellow> added to rule <yellow><name></yellow>.");
        put(Key.EVENT_REMOVED,
                "<green>Событие <yellow><event></yellow> убрано из правила <yellow><name></yellow>.",
                "<green>Event <yellow><event></yellow> removed from rule <yellow><name></yellow>.");
        put(Key.WORLD_ADDED,
                "<green>Мир <yellow><world></yellow> добавлен в правило <yellow><name></yellow>.",
                "<green>World <yellow><world></yellow> added to rule <yellow><name></yellow>.");
        put(Key.WORLD_REMOVED,
                "<green>Мир <yellow><world></yellow> убран из правила <yellow><name></yellow>.",
                "<green>World <yellow><world></yellow> removed from rule <yellow><name></yellow>.");
        put(Key.COMMAND_ADDED,
                "<green>Команда №<index> добавлена в правило <yellow><name></yellow>.",
                "<green>Command #<index> added to rule <yellow><name></yellow>.");
        put(Key.COMMAND_REMOVED,
                "<green>Команда №<index> удалена из правила <yellow><name></yellow>.",
                "<green>Command #<index> removed from rule <yellow><name></yellow>.");

        put(Key.ERR_NOT_FOUND,
                "<red>Такого правила нет.",
                "<red>No such rule.");
        put(Key.ERR_ALREADY_EXISTS,
                "<red>Правило с таким именем уже есть.",
                "<red>A rule with this name already exists.");
        put(Key.ERR_INVALID_NAME,
                "<red>Имя правила: от 1 до 32 символов из a-z, 0-9, _ и -.",
                "<red>Rule name: 1 to 32 characters from a-z, 0-9, _ and -.");
        put(Key.ERR_INVALID_VALUE,
                "<red>Недопустимое значение.",
                "<red>Invalid value.");
        put(Key.ERR_INVALID_INDEX,
                "<red>Команды с таким номером нет.",
                "<red>There is no command with this number.");
        put(Key.ERR_UNSUPPORTED_PLACEHOLDER,
                "<red>Команда использует неизвестный плейсхолдер или плейсхолдер, недоступный для событий правила.",
                "<red>The command uses an unknown placeholder or one that the rule's events do not provide.");
        put(Key.ERR_ALREADY_PRESENT,
                "<red>Это уже добавлено.",
                "<red>This is already added.");
        put(Key.ERR_NOT_PRESENT,
                "<red>Этого нет в правиле.",
                "<red>This is not in the rule.");
        put(Key.ERR_LIMIT_REACHED,
                "<red>Достигнут лимит (см. max-rules и max-commands-per-rule в config.yml).",
                "<red>Limit reached (see max-rules and max-commands-per-rule in config.yml).");
        put(Key.ERR_UNKNOWN_EVENT,
                "<red>Неизвестное событие. Доступные: <events>.",
                "<red>Unknown event. Available: <events>.");
        verify();
    }

    private Lang() {
    }

    private static void put(Key key, String ru, String en) {
        RU.put(key, ru);
        EN.put(key, en);
    }

    private static void verify() {
        for (Key key : Key.values()) {
            if (!RU.containsKey(key) || !EN.containsKey(key)) {
                throw new IllegalStateException("Missing translation for " + key);
            }
        }
    }

    public static void setLanguage(String value) {
        language = value == null || value.isBlank() ? "auto" : value.toLowerCase(Locale.ROOT);
    }

    public static Component get(Key key, CommandSender sender, TagResolver... resolvers) {
        return MM.deserialize(table(sender).get(key), resolvers);
    }

    private static Map<Key, String> table(CommandSender sender) {
        return switch (language) {
            case "ru" -> RU;
            case "en" -> EN;
            default -> sender instanceof Player player
                    && "ru".equalsIgnoreCase(player.locale().getLanguage()) ? RU : EN;
        };
    }
}
