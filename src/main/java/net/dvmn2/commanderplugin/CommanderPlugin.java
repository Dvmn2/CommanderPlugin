package net.dvmn2.commanderplugin;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.dvmn2.commanderplugin.command.CommanderCommand;
import net.dvmn2.commanderplugin.storage.RuleStore;
import org.bukkit.plugin.java.JavaPlugin;

/** Composition root of CommanderPlugin: wiring and enable/disable order. */
public final class CommanderPlugin extends JavaPlugin {

    private volatile CommanderSettings settings;
    private RuleManager ruleManager;
    private ExecutionManager executionManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadSettings();

        ruleManager = new RuleManager(new RuleStore(getDataFolder().toPath(), getLogger()), () -> settings);
        ruleManager.load();
        executionManager = new ExecutionManager(this, ruleManager);

        getServer().getPluginManager().registerEvents(new CommanderListener(executionManager), this);

        CommanderCommand command = new CommanderCommand(this, ruleManager);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(command.build(), "Manage rules that run commands on server events"));

        getLogger().info("Enabled with " + ruleManager.getRegistry().size() + " rule(s).");
    }

    @Override
    public void onDisable() {
        if (executionManager != null) {
            executionManager.shutdown();
        }
        if (ruleManager != null) {
            ruleManager.shutdown();
        }
    }

    /** Re-reads config.yml and rules.json without restarting the plugin. */
    public void reloadAll() {
        reloadConfig();
        reloadSettings();
        ruleManager.load();
        executionManager.onReload();
    }

    private void reloadSettings() {
        settings = CommanderSettings.parse(getConfig(), getLogger());
        Lang.setLanguage(settings.language());
    }
}
