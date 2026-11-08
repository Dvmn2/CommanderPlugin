package net.dvmn2.commanderplugin;

import net.dvmn2.commanderplugin.model.Placeholder;
import net.dvmn2.commanderplugin.model.Rule;
import net.dvmn2.commanderplugin.model.TriggerContext;
import net.dvmn2.commanderplugin.util.Placeholders;
import org.bukkit.command.CommandException;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Finds the rules matching an event and runs their commands as the console.
 *
 * <p>Main thread only. Placeholder values are captured and validated when the event fires; the commands run
 * on the next server tick at the earliest (plus the rule's delay), never inside the event handler. A problem
 * with one rule or command is logged once and never stops the others.
 */
public final class ExecutionManager {

    private static final long TICKS_PER_SECOND = 20L;

    private final JavaPlugin plugin;
    private final RuleManager rules;
    private final Logger log;
    /** Keys of problems that were already logged, to avoid repeating them. */
    private final Set<String> warned = new HashSet<>();

    public ExecutionManager(JavaPlugin plugin, RuleManager rules) {
        this.plugin = plugin;
        this.rules = rules;
        this.log = plugin.getLogger();
    }

    /** Runs all enabled rules of the event whose conditions match. */
    public void fire(TriggerContext context) {
        List<Rule> candidates = rules.getRegistry().forEvent(context.event());
        if (candidates.isEmpty()) {
            return;
        }
        Map<Placeholder, String> values = sanitize(context.values());
        for (Rule rule : candidates) {
            if (rule.enabled() && matches(rule, context)) {
                schedule(rule, resolve(rule, values));
            }
        }
    }

    /** Forgets logged problems so they are reported again after a reload. */
    public void onReload() {
        warned.clear();
    }

    /** Cancels commands that are waiting for their delay. */
    public void shutdown() {
        plugin.getServer().getScheduler().cancelTasks(plugin);
    }

    private boolean matches(Rule rule, TriggerContext context) {
        if (!context.event().isPlayerBased()) {
            return true;
        }
        Player player = context.player();
        if (!rule.permission().isEmpty() && (player == null || !player.hasPermission(rule.permission()))) {
            return false;
        }
        if (rule.worlds().isEmpty()) {
            return true;
        }
        String world = context.worldName();
        if (world == null) {
            return false;
        }
        for (String allowed : rule.worlds()) {
            if (allowed.equalsIgnoreCase(world)) {
                return true;
            }
        }
        return false;
    }

    private Map<Placeholder, String> sanitize(Map<Placeholder, String> raw) {
        Map<Placeholder, String> safe = new EnumMap<>(Placeholder.class);
        for (Map.Entry<Placeholder, String> entry : raw.entrySet()) {
            Placeholder placeholder = entry.getKey();
            if (placeholder.isValid(entry.getValue())) {
                safe.put(placeholder, entry.getValue());
            } else {
                warnOnce("value:" + placeholder.getKey(), "The value of {" + placeholder.getKey()
                        + "} contains unsupported characters; commands using it are skipped");
            }
        }
        return safe;
    }

    private List<PendingCommand> resolve(Rule rule, Map<Placeholder, String> values) {
        List<PendingCommand> pending = new ArrayList<>();
        List<String> templates = rule.commands();
        for (int i = 0; i < templates.size(); i++) {
            String command = Placeholders.substitute(templates.get(i), values);
            if (command == null) {
                log.fine("Rule '" + rule.name() + "': command #" + (i + 1) + " skipped, a placeholder has no value");
            } else {
                pending.add(new PendingCommand(i + 1, templates.get(i), command));
            }
        }
        return pending;
    }

    private void schedule(Rule rule, List<PendingCommand> pending) {
        if (pending.isEmpty()) {
            return;
        }
        String ruleName = rule.name();
        long delayTicks = Math.round(rule.delaySeconds() * TICKS_PER_SECOND);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> execute(ruleName, pending), delayTicks);
    }

    private void execute(String ruleName, List<PendingCommand> pending) {
        for (PendingCommand command : pending) {
            String problem = "Command #" + command.position() + " of rule '" + ruleName + "' ";
            try {
                boolean handled = plugin.getServer()
                        .dispatchCommand(plugin.getServer().getConsoleSender(), command.command());
                if (!handled) {
                    warnOnce(ruleName + "#" + command.position(), problem
                            + "was not executed (unknown command or it failed): /" + command.template());
                }
            } catch (CommandException ex) {
                warnOnce(ruleName + "#" + command.position(), problem + "threw an exception: /"
                        + command.template() + " (" + ex.getMessage() + ")");
            }
        }
    }

    private void warnOnce(String key, String message) {
        if (warned.add(key)) {
            log.warning(message);
        }
    }

    /**
     * A command with its placeholders already substituted.
     *
     * @param position 1-based position inside the rule
     * @param template command as stored in the rule, used for log messages
     * @param command  command ready to dispatch
     */
    private record PendingCommand(int position, String template, String command) {
    }
}
