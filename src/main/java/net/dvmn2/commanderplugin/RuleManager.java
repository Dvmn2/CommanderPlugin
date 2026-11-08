package net.dvmn2.commanderplugin;

import net.dvmn2.commanderplugin.model.EditResult;
import net.dvmn2.commanderplugin.model.Rule;
import net.dvmn2.commanderplugin.model.RuleRegistry;
import net.dvmn2.commanderplugin.model.TriggerEvent;
import net.dvmn2.commanderplugin.storage.RuleStore;
import net.dvmn2.commanderplugin.util.Placeholders;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Owns the current set of rules and persists every edit.
 *
 * <p>Edit methods MUST be called from the server main thread. {@link #getRegistry()} returns an immutable
 * snapshot that is safe to read from any thread. Every edit is written to disk asynchronously.
 */
public final class RuleManager {

    private final RuleStore store;
    private final Supplier<CommanderSettings> settings;

    private volatile RuleRegistry registry = RuleRegistry.empty();

    public RuleManager(RuleStore store, Supplier<CommanderSettings> settings) {
        this.store = store;
        this.settings = settings;
    }

    // ---------- Lifecycle ----------

    /** Reads {@code rules.json}. On a read failure the current rules are kept. */
    public void load() {
        List<Rule> loaded = store.load();
        if (loaded != null) {
            registry = RuleRegistry.of(loaded);
        }
    }

    /** Waits for pending writes and saves the final state. */
    public void shutdown() {
        store.shutdown(registry.all());
    }

    public RuleRegistry getRegistry() {
        return registry;
    }

    // ---------- Rules ----------

    public EditResult create(String rawName) {
        String name = normalize(rawName);
        if (!Rule.isValidName(name)) {
            return EditResult.INVALID_NAME;
        }
        if (registry.find(name) != null) {
            return EditResult.ALREADY_EXISTS;
        }
        if (registry.size() >= settings.get().maxRules()) {
            return EditResult.LIMIT_REACHED;
        }
        commit(registry.with(Rule.create(name)));
        return EditResult.SUCCESS;
    }

    public EditResult delete(String rawName) {
        String name = normalize(rawName);
        if (registry.find(name) == null) {
            return EditResult.NOT_FOUND;
        }
        commit(registry.without(name));
        return EditResult.SUCCESS;
    }

    public EditResult setEnabled(String rawName, boolean enabled) {
        Rule rule = registry.find(normalize(rawName));
        if (rule == null) {
            return EditResult.NOT_FOUND;
        }
        commit(rule.withEnabled(enabled));
        return EditResult.SUCCESS;
    }

    public EditResult setDelay(String rawName, double seconds) {
        Rule rule = registry.find(normalize(rawName));
        if (rule == null) {
            return EditResult.NOT_FOUND;
        }
        if (Double.isNaN(seconds) || seconds < 0 || seconds > Rule.MAX_DELAY_SECONDS) {
            return EditResult.INVALID_VALUE;
        }
        commit(rule.withDelaySeconds(seconds));
        return EditResult.SUCCESS;
    }

    /** Sets the required permission; an empty value clears it. */
    public EditResult setPermission(String rawName, String permission) {
        Rule rule = registry.find(normalize(rawName));
        if (rule == null) {
            return EditResult.NOT_FOUND;
        }
        if (!permission.isEmpty() && !Rule.isValidPermission(permission)) {
            return EditResult.INVALID_VALUE;
        }
        commit(rule.withPermission(permission));
        return EditResult.SUCCESS;
    }

    // ---------- Events ----------

    public EditResult addEvent(String rawName, TriggerEvent event) {
        Rule rule = registry.find(normalize(rawName));
        if (rule == null) {
            return EditResult.NOT_FOUND;
        }
        if (rule.events().contains(event)) {
            return EditResult.ALREADY_PRESENT;
        }
        EnumSet<TriggerEvent> events = EnumSet.noneOf(TriggerEvent.class);
        events.addAll(rule.events());
        events.add(event);
        for (String command : rule.commands()) {
            if (Placeholders.firstUnsupported(command, events) != null) {
                return EditResult.UNSUPPORTED_PLACEHOLDER;
            }
        }
        commit(rule.withEvents(events));
        return EditResult.SUCCESS;
    }

    public EditResult removeEvent(String rawName, TriggerEvent event) {
        Rule rule = registry.find(normalize(rawName));
        if (rule == null) {
            return EditResult.NOT_FOUND;
        }
        if (!rule.events().contains(event)) {
            return EditResult.NOT_PRESENT;
        }
        EnumSet<TriggerEvent> events = EnumSet.noneOf(TriggerEvent.class);
        events.addAll(rule.events());
        events.remove(event);
        commit(rule.withEvents(events));
        return EditResult.SUCCESS;
    }

    // ---------- Worlds ----------

    public EditResult addWorld(String rawName, String world) {
        Rule rule = registry.find(normalize(rawName));
        if (rule == null) {
            return EditResult.NOT_FOUND;
        }
        if (!Rule.isValidWorld(world)) {
            return EditResult.INVALID_VALUE;
        }
        if (indexOfIgnoreCase(rule.worlds(), world) >= 0) {
            return EditResult.ALREADY_PRESENT;
        }
        List<String> worlds = new ArrayList<>(rule.worlds());
        worlds.add(world);
        commit(rule.withWorlds(worlds));
        return EditResult.SUCCESS;
    }

    public EditResult removeWorld(String rawName, String world) {
        Rule rule = registry.find(normalize(rawName));
        if (rule == null) {
            return EditResult.NOT_FOUND;
        }
        int index = indexOfIgnoreCase(rule.worlds(), world);
        if (index < 0) {
            return EditResult.NOT_PRESENT;
        }
        List<String> worlds = new ArrayList<>(rule.worlds());
        worlds.remove(index);
        commit(rule.withWorlds(worlds));
        return EditResult.SUCCESS;
    }

    // ---------- Commands ----------

    public EditResult addCommand(String rawName, String rawCommand) {
        Rule rule = registry.find(normalize(rawName));
        if (rule == null) {
            return EditResult.NOT_FOUND;
        }
        String command = Rule.normalizeCommand(rawCommand);
        if (command == null) {
            return EditResult.INVALID_VALUE;
        }
        if (rule.commands().size() >= settings.get().maxCommandsPerRule()) {
            return EditResult.LIMIT_REACHED;
        }
        if (Placeholders.firstUnsupported(command, rule.events()) != null) {
            return EditResult.UNSUPPORTED_PLACEHOLDER;
        }
        List<String> commands = new ArrayList<>(rule.commands());
        commands.add(command);
        commit(rule.withCommands(commands));
        return EditResult.SUCCESS;
    }

    /** Removes a command by its 1-based position. */
    public EditResult removeCommand(String rawName, int position) {
        Rule rule = registry.find(normalize(rawName));
        if (rule == null) {
            return EditResult.NOT_FOUND;
        }
        if (position < 1 || position > rule.commands().size()) {
            return EditResult.INVALID_INDEX;
        }
        List<String> commands = new ArrayList<>(rule.commands());
        commands.remove(position - 1);
        commit(rule.withCommands(commands));
        return EditResult.SUCCESS;
    }

    // ---------- Internals ----------

    private void commit(Rule rule) {
        commit(registry.with(rule));
    }

    private void commit(RuleRegistry next) {
        registry = next;
        store.saveAsync(next.all());
    }

    private static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private static int indexOfIgnoreCase(List<String> values, String value) {
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i).equalsIgnoreCase(value)) {
                return i;
            }
        }
        return -1;
    }
}
