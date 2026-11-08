package net.dvmn2.commanderplugin.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.dvmn2.commanderplugin.CommanderPlugin;
import net.dvmn2.commanderplugin.Lang;
import net.dvmn2.commanderplugin.Permissions;
import net.dvmn2.commanderplugin.RuleManager;
import net.dvmn2.commanderplugin.model.EditResult;
import net.dvmn2.commanderplugin.model.Rule;
import net.dvmn2.commanderplugin.model.TriggerEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/**
 * The {@code /commander} command. Main thread only.
 *
 * <pre>
 * /commander
 *   reload                                        commander.reload
 *   list
 *   info &lt;rule&gt;
 *   create &lt;rule&gt;                                commander.edit
 *   delete &lt;rule&gt;
 *   enable &lt;rule&gt;
 *   disable &lt;rule&gt;
 *   delay &lt;rule&gt; &lt;seconds&gt;
 *   permission &lt;rule&gt; clear | &lt;permission&gt;
 *   event add|remove &lt;rule&gt; &lt;event&gt;
 *   world add|remove &lt;rule&gt; &lt;world&gt;
 *   command add &lt;rule&gt; &lt;command...&gt;
 *   command remove &lt;rule&gt; &lt;number&gt;
 * </pre>
 *
 * <p>The root and {@code list}/{@code info} require {@code commander.use}.
 */
public final class CommanderCommand {

    private static final String ARG_RULE = "rule";
    private static final String ARG_EVENT = "event";
    private static final String ARG_WORLD = "world";
    private static final String ARG_PERMISSION = "permission";
    private static final String ARG_SECONDS = "seconds";
    private static final String ARG_COMMAND = "command";
    private static final String ARG_NUMBER = "number";

    private final CommanderPlugin plugin;
    private final RuleManager rules;

    public CommanderCommand(CommanderPlugin plugin, RuleManager rules) {
        this.plugin = plugin;
        this.rules = rules;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("commander")
                .requires(has(Permissions.USE))
                .then(Commands.literal("reload")
                        .requires(has(Permissions.RELOAD))
                        .executes(this::reload))
                .then(Commands.literal("list")
                        .executes(this::list))
                .then(Commands.literal("info")
                        .then(ruleArgument().executes(this::info)))
                .then(Commands.literal("create")
                        .requires(has(Permissions.EDIT))
                        .then(Commands.argument(ARG_RULE, StringArgumentType.word())
                                .executes(this::create)))
                .then(Commands.literal("delete")
                        .requires(has(Permissions.EDIT))
                        .then(ruleArgument().executes(this::delete)))
                .then(Commands.literal("enable")
                        .requires(has(Permissions.EDIT))
                        .then(ruleArgument().executes(ctx -> setEnabled(ctx, true))))
                .then(Commands.literal("disable")
                        .requires(has(Permissions.EDIT))
                        .then(ruleArgument().executes(ctx -> setEnabled(ctx, false))))
                .then(Commands.literal("delay")
                        .requires(has(Permissions.EDIT))
                        .then(ruleArgument()
                                .then(Commands.argument(ARG_SECONDS,
                                                DoubleArgumentType.doubleArg(0, Rule.MAX_DELAY_SECONDS))
                                        .executes(this::delay))))
                .then(Commands.literal("permission")
                        .requires(has(Permissions.EDIT))
                        .then(ruleArgument()
                                .then(Commands.literal("clear")
                                        .executes(this::clearPermission))
                                .then(Commands.argument(ARG_PERMISSION, StringArgumentType.word())
                                        .executes(this::setPermission))))
                .then(Commands.literal("event")
                        .requires(has(Permissions.EDIT))
                        .then(Commands.literal("add")
                                .then(ruleArgument()
                                        .then(eventArgument(false)
                                                .executes(ctx -> changeEvent(ctx, true)))))
                        .then(Commands.literal("remove")
                                .then(ruleArgument()
                                        .then(eventArgument(true)
                                                .executes(ctx -> changeEvent(ctx, false))))))
                .then(Commands.literal("world")
                        .requires(has(Permissions.EDIT))
                        .then(Commands.literal("add")
                                .then(ruleArgument()
                                        .then(worldArgument(false)
                                                .executes(ctx -> changeWorld(ctx, true)))))
                        .then(Commands.literal("remove")
                                .then(ruleArgument()
                                        .then(worldArgument(true)
                                                .executes(ctx -> changeWorld(ctx, false))))))
                .then(Commands.literal("command")
                        .requires(has(Permissions.EDIT))
                        .then(Commands.literal("add")
                                .then(ruleArgument()
                                        .then(Commands.argument(ARG_COMMAND, StringArgumentType.greedyString())
                                                .executes(this::addCommand))))
                        .then(Commands.literal("remove")
                                .then(ruleArgument()
                                        .then(Commands.argument(ARG_NUMBER, IntegerArgumentType.integer(1))
                                                .executes(this::removeCommand)))))
                .build();
    }

    // ---------- Handlers ----------

    private int reload(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        plugin.reloadAll();
        send(sender, Lang.Key.RELOADED,
                Placeholder.unparsed("count", String.valueOf(rules.getRegistry().size())));
        return Command.SINGLE_SUCCESS;
    }

    private int list(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        List<Rule> all = rules.getRegistry().all();
        if (all.isEmpty()) {
            send(sender, Lang.Key.LIST_EMPTY);
            return Command.SINGLE_SUCCESS;
        }
        send(sender, Lang.Key.LIST_HEADER, Placeholder.unparsed("count", String.valueOf(all.size())));
        for (Rule rule : all) {
            send(sender, Lang.Key.LIST_ENTRY,
                    Placeholder.component("state", state(rule, sender)),
                    Placeholder.unparsed("name", rule.name()),
                    Placeholder.unparsed("events", String.valueOf(rule.events().size())),
                    Placeholder.unparsed("commands", String.valueOf(rule.commands().size())));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int info(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Rule rule = rules.getRegistry().find(ruleName(ctx));
        if (rule == null) {
            send(sender, Lang.Key.ERR_NOT_FOUND);
            return 0;
        }
        List<String> eventKeys = new ArrayList<>();
        for (TriggerEvent event : rule.events()) {
            eventKeys.add(event.getKey());
        }
        send(sender, Lang.Key.INFO_HEADER,
                Placeholder.unparsed("name", rule.name()),
                Placeholder.component("state", state(rule, sender)));
        send(sender, Lang.Key.INFO_EVENTS,
                Placeholder.component("value", joined(eventKeys, Lang.Key.VALUE_NONE, sender)));
        send(sender, Lang.Key.INFO_DELAY,
                Placeholder.unparsed("value", formatSeconds(rule.delaySeconds())));
        send(sender, Lang.Key.INFO_PERMISSION,
                Placeholder.component("value", rule.permission().isEmpty()
                        ? Lang.get(Lang.Key.VALUE_NONE, sender)
                        : Component.text(rule.permission())));
        send(sender, Lang.Key.INFO_WORLDS,
                Placeholder.component("value", joined(rule.worlds(), Lang.Key.VALUE_ALL, sender)));
        send(sender, Lang.Key.INFO_COMMANDS_HEADER);
        List<String> commands = rule.commands();
        for (int i = 0; i < commands.size(); i++) {
            send(sender, Lang.Key.INFO_COMMAND,
                    Placeholder.unparsed("index", String.valueOf(i + 1)),
                    Placeholder.unparsed("command", commands.get(i)));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int create(CommandContext<CommandSourceStack> ctx) {
        return respond(ctx, rules.create(ruleName(ctx)), Lang.Key.RULE_CREATED, nameTag(ctx));
    }

    private int delete(CommandContext<CommandSourceStack> ctx) {
        return respond(ctx, rules.delete(ruleName(ctx)), Lang.Key.RULE_DELETED, nameTag(ctx));
    }

    private int setEnabled(CommandContext<CommandSourceStack> ctx, boolean enabled) {
        return respond(ctx, rules.setEnabled(ruleName(ctx), enabled),
                enabled ? Lang.Key.RULE_ENABLED : Lang.Key.RULE_DISABLED, nameTag(ctx));
    }

    private int delay(CommandContext<CommandSourceStack> ctx) {
        double seconds = DoubleArgumentType.getDouble(ctx, ARG_SECONDS);
        return respond(ctx, rules.setDelay(ruleName(ctx), seconds), Lang.Key.DELAY_SET,
                nameTag(ctx), Placeholder.unparsed("seconds", formatSeconds(seconds)));
    }

    private int setPermission(CommandContext<CommandSourceStack> ctx) {
        String permission = StringArgumentType.getString(ctx, ARG_PERMISSION);
        return respond(ctx, rules.setPermission(ruleName(ctx), permission), Lang.Key.PERMISSION_SET,
                nameTag(ctx), Placeholder.unparsed("permission", permission));
    }

    private int clearPermission(CommandContext<CommandSourceStack> ctx) {
        return respond(ctx, rules.setPermission(ruleName(ctx), ""), Lang.Key.PERMISSION_CLEARED, nameTag(ctx));
    }

    private int changeEvent(CommandContext<CommandSourceStack> ctx, boolean add) {
        TriggerEvent event = TriggerEvent.fromKey(StringArgumentType.getString(ctx, ARG_EVENT));
        if (event == null) {
            CommandSender sender = ctx.getSource().getSender();
            List<String> keys = new ArrayList<>();
            for (TriggerEvent known : TriggerEvent.values()) {
                keys.add(known.getKey());
            }
            send(sender, Lang.Key.ERR_UNKNOWN_EVENT, Placeholder.unparsed("events", String.join(", ", keys)));
            return 0;
        }
        EditResult result = add ? rules.addEvent(ruleName(ctx), event) : rules.removeEvent(ruleName(ctx), event);
        return respond(ctx, result, add ? Lang.Key.EVENT_ADDED : Lang.Key.EVENT_REMOVED,
                nameTag(ctx), Placeholder.unparsed("event", event.getKey()));
    }

    private int changeWorld(CommandContext<CommandSourceStack> ctx, boolean add) {
        String world = StringArgumentType.getString(ctx, ARG_WORLD);
        EditResult result = add ? rules.addWorld(ruleName(ctx), world) : rules.removeWorld(ruleName(ctx), world);
        return respond(ctx, result, add ? Lang.Key.WORLD_ADDED : Lang.Key.WORLD_REMOVED,
                nameTag(ctx), Placeholder.unparsed("world", world));
    }

    private int addCommand(CommandContext<CommandSourceStack> ctx) {
        String name = ruleName(ctx);
        EditResult result = rules.addCommand(name, StringArgumentType.getString(ctx, ARG_COMMAND));
        Rule rule = rules.getRegistry().find(name);
        int number = rule == null ? 0 : rule.commands().size();
        return respond(ctx, result, Lang.Key.COMMAND_ADDED,
                nameTag(ctx), Placeholder.unparsed("index", String.valueOf(number)));
    }

    private int removeCommand(CommandContext<CommandSourceStack> ctx) {
        int number = IntegerArgumentType.getInteger(ctx, ARG_NUMBER);
        return respond(ctx, rules.removeCommand(ruleName(ctx), number), Lang.Key.COMMAND_REMOVED,
                nameTag(ctx), Placeholder.unparsed("index", String.valueOf(number)));
    }

    // ---------- Arguments and suggestions ----------

    private RequiredArgumentBuilder<CommandSourceStack, String> ruleArgument() {
        return Commands.argument(ARG_RULE, StringArgumentType.word()).suggests(this::suggestRules);
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> eventArgument(boolean existingOnly) {
        return Commands.argument(ARG_EVENT, StringArgumentType.word())
                .suggests((ctx, builder) -> suggestEvents(ctx, builder, existingOnly));
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> worldArgument(boolean existingOnly) {
        return Commands.argument(ARG_WORLD, StringArgumentType.word())
                .suggests((ctx, builder) -> suggestWorlds(ctx, builder, existingOnly));
    }

    private CompletableFuture<Suggestions> suggestRules(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String prefix = builder.getRemainingLowerCase();
        for (Rule rule : rules.getRegistry().all()) {
            if (rule.name().startsWith(prefix)) {
                builder.suggest(rule.name());
            }
        }
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestEvents(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder, boolean existingOnly) {
        String prefix = builder.getRemainingLowerCase();
        Rule rule = existingOnly ? rules.getRegistry().find(ruleName(ctx)) : null;
        for (TriggerEvent event : TriggerEvent.values()) {
            boolean offered = !existingOnly || (rule != null && rule.events().contains(event));
            if (offered && event.getKey().startsWith(prefix)) {
                builder.suggest(event.getKey());
            }
        }
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestWorlds(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder, boolean existingOnly) {
        String prefix = builder.getRemainingLowerCase();
        List<String> names = new ArrayList<>();
        if (existingOnly) {
            Rule rule = rules.getRegistry().find(ruleName(ctx));
            if (rule != null) {
                names.addAll(rule.worlds());
            }
        } else {
            for (World world : plugin.getServer().getWorlds()) {
                names.add(world.getName());
            }
        }
        for (String name : names) {
            if (name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    }

    // ---------- Helpers ----------

    private static Predicate<CommandSourceStack> has(String permission) {
        return src -> src.getSender().hasPermission(permission);
    }

    private static String ruleName(CommandContext<CommandSourceStack> ctx) {
        return StringArgumentType.getString(ctx, ARG_RULE).toLowerCase(Locale.ROOT);
    }

    private static TagResolver nameTag(CommandContext<CommandSourceStack> ctx) {
        return Placeholder.unparsed("name", ruleName(ctx));
    }

    private static void send(CommandSender sender, Lang.Key key, TagResolver... resolvers) {
        sender.sendMessage(Lang.get(key, sender, resolvers));
    }

    private static int respond(
            CommandContext<CommandSourceStack> ctx, EditResult result, Lang.Key success, TagResolver... resolvers) {
        CommandSender sender = ctx.getSource().getSender();
        if (result == EditResult.SUCCESS) {
            send(sender, success, resolvers);
            return Command.SINGLE_SUCCESS;
        }
        send(sender, errorKey(result));
        return 0;
    }

    private static Lang.Key errorKey(EditResult result) {
        return switch (result) {
            case NOT_FOUND -> Lang.Key.ERR_NOT_FOUND;
            case ALREADY_EXISTS -> Lang.Key.ERR_ALREADY_EXISTS;
            case INVALID_NAME -> Lang.Key.ERR_INVALID_NAME;
            case INVALID_VALUE -> Lang.Key.ERR_INVALID_VALUE;
            case INVALID_INDEX -> Lang.Key.ERR_INVALID_INDEX;
            case UNSUPPORTED_PLACEHOLDER -> Lang.Key.ERR_UNSUPPORTED_PLACEHOLDER;
            case ALREADY_PRESENT -> Lang.Key.ERR_ALREADY_PRESENT;
            case NOT_PRESENT -> Lang.Key.ERR_NOT_PRESENT;
            case LIMIT_REACHED -> Lang.Key.ERR_LIMIT_REACHED;
            case SUCCESS -> throw new IllegalArgumentException("SUCCESS is not an error");
        };
    }

    private static Component state(Rule rule, CommandSender sender) {
        return Lang.get(rule.enabled() ? Lang.Key.STATE_ON : Lang.Key.STATE_OFF, sender);
    }

    private static Component joined(List<String> values, Lang.Key emptyKey, CommandSender sender) {
        return values.isEmpty() ? Lang.get(emptyKey, sender) : Component.text(String.join(", ", values));
    }

    private static String formatSeconds(double seconds) {
        return seconds == Math.rint(seconds) ? String.valueOf((long) seconds) : String.valueOf(seconds);
    }
}
