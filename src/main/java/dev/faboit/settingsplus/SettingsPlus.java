package dev.faboit.settingsplus;

import dev.faboit.settingsplus.command.SettingsCommand;
import dev.faboit.settingsplus.command.SettingsPlusCommand;
import dev.faboit.settingsplus.config.ConfigLoader;
import dev.faboit.settingsplus.config.MenuConfig;
import dev.faboit.settingsplus.config.Messages;
import dev.faboit.settingsplus.dialog.DialogManager;
import dev.faboit.settingsplus.storage.YamlSettingsStore;
import dev.faboit.settingsplus.util.Sched;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.logging.Level;

/**
 * SettingsPlus - fully config-driven player settings menus built on Paper's dialog API.
 *
 * <p>Runs unmodified on Paper and Folia: all scheduling goes through {@link Sched}, which uses the
 * region-aware schedulers that ship in plain {@code paper-api}.</p>
 */
public final class SettingsPlus extends JavaPlugin {

    private Messages messages;
    private SettingsManager settings;
    private ActionExecutor actions;
    private DialogManager dialogs;

    private boolean applyOnJoin;
    private long applyOnJoinDelayTicks;
    private long callbackLifetimeMinutes;

    @Override
    public void onEnable() {
        if (!hasDialogSupport()) {
            getLogger().severe("This server does not provide Paper's dialog API.");
            getLogger().severe("SettingsPlus needs Paper (or Folia) 1.21.8 or newer. Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();
        saveResourceIfMissing("settings.yml");

        this.messages = new Messages();
        this.settings = new SettingsManager(this,
                new YamlSettingsStore(new File(getDataFolder(), "playerdata").toPath()));
        this.actions = new ActionExecutor(this);
        this.dialogs = new DialogManager(this);

        try {
            reload();
        } catch (RuntimeException ex) {
            getLogger().log(Level.SEVERE, "settings.yml could not be loaded, disabling: " + ex.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        register("settings", new SettingsCommand(this));
        register("settingsplus", new SettingsPlusCommand(this));

        long saveInterval = getConfig().getLong("storage.save-interval-seconds", 300L);
        if (saveInterval > 0) {
            Sched.asyncTimer(this, () -> settings.flushAll(false), saveInterval);
        }

        getLogger().info("Loaded " + settings.menu().categories().size() + " categories and "
                + settings.settingCount() + " settings (" + (Sched.isFolia() ? "Folia" : "Paper") + ").");
    }

    @Override
    public void onDisable() {
        if (settings != null) {
            // Synchronous on purpose: the async scheduler stops accepting work during shutdown.
            settings.flushAll(false);
        }
        Sched.cancelAll(this);
    }

    /**
     * Re-reads {@code config.yml} and {@code settings.yml}.
     *
     * <p>The new menu is only installed once it has parsed cleanly, so a broken edit leaves the
     * running configuration untouched instead of emptying every player's menu.</p>
     */
    public void reload() {
        reloadConfig();

        File file = new File(getDataFolder(), "settings.yml");
        FileConfiguration menuConfig = YamlConfiguration.loadConfiguration(file);

        ConfigLoader loader = new ConfigLoader();
        MenuConfig parsed = loader.load(menuConfig);
        for (String warning : loader.warnings()) {
            getLogger().warning("settings.yml: " + warning);
        }

        settings.setMenu(parsed);
        messages.load(getConfig().getConfigurationSection("messages"));

        applyOnJoin = getConfig().getBoolean("apply-on-join", true);
        applyOnJoinDelayTicks = getConfig().getLong("apply-on-join-delay-ticks", 40L);
        callbackLifetimeMinutes = Math.max(1L, getConfig().getLong("dialog.callback-lifetime-minutes", 60L));
    }

    /** Copies a bundled resource into the data folder only if the operator has not created it. */
    private void saveResourceIfMissing(String name) {
        if (!new File(getDataFolder(), name).exists()) {
            saveResource(name, false);
        }
    }

    private void register(String name, Object executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("Command '" + name + "' is missing from plugin.yml.");
            return;
        }
        command.setExecutor((org.bukkit.command.CommandExecutor) executor);
        command.setTabCompleter((org.bukkit.command.TabCompleter) executor);
    }

    /** @return {@code true} when the server exposes the dialog API this plugin is built on. */
    private static boolean hasDialogSupport() {
        try {
            Class.forName("io.papermc.paper.dialog.Dialog");
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }

    public Messages messages() {
        return messages;
    }

    public SettingsManager settings() {
        return settings;
    }

    public ActionExecutor actions() {
        return actions;
    }

    public DialogManager dialogs() {
        return dialogs;
    }

    public boolean applyOnJoin() {
        return applyOnJoin;
    }

    public long applyOnJoinDelayTicks() {
        return applyOnJoinDelayTicks;
    }

    public long callbackLifetimeMinutes() {
        return callbackLifetimeMinutes;
    }
}
