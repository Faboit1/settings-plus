package dev.faboit.settingsplus.config;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import dev.faboit.settingsplus.util.Text;

import java.util.HashMap;
import java.util.Map;

/** Operator-facing strings, all MiniMessage, all overridable in {@code config.yml}. */
public final class Messages {

    private static final Map<String, String> DEFAULTS = new HashMap<>();

    static {
        DEFAULTS.put("prefix", "<gradient:#8be9fd:#50fa7b><bold>Settings</bold></gradient> <dark_gray>|</dark_gray> ");
        DEFAULTS.put("no-permission", "<red>You do not have permission to do that.");
        DEFAULTS.put("players-only", "<red>Only players can open the settings menu.");
        DEFAULTS.put("unsupported-server", "<red>This server build has no dialog support. SettingsPlus needs Paper 1.21.8 or newer.");
        DEFAULTS.put("unknown-category", "<red>There is no settings category called <white>%category%</white>.");
        DEFAULTS.put("unknown-setting", "<red>There is no setting called <white>%setting%</white>.");
        DEFAULTS.put("unknown-value", "<red><white>%value%</white> is not a valid value for <white>%setting%</white>.");
        DEFAULTS.put("unknown-player", "<red>No player called <white>%player%</white> is online.");
        DEFAULTS.put("option-locked", "<red>You have not unlocked that option yet.");
        DEFAULTS.put("reloaded", "<green>Reloaded <white>%categories%</white> categories and <white>%settings%</white> settings in <white>%ms%ms</white>.");
        DEFAULTS.put("dialog-error", "<red>Something went wrong with that menu: <white>%error%</white>.");
        DEFAULTS.put("reload-failed", "<red>Reload failed: <white>%error%</white>. The previous configuration is still active.");
        DEFAULTS.put("category-reset", "<green>Reset <white>%category%</white> to its defaults.");
        DEFAULTS.put("value-set", "<green>Set <white>%setting%</white> to <white>%value%</white> for <white>%player%</white>.");
        DEFAULTS.put("value-get", "<gray><white>%setting%</white> is <white>%value%</white> for <white>%player%</white>.");
        DEFAULTS.put("usage", "<gray>Usage: <white>%usage%");
    }

    private final Map<String, String> values = new HashMap<>(DEFAULTS);
    private String prefix = DEFAULTS.get("prefix");

    /** Overlays any keys present in the config section on top of the built-in defaults. */
    public void load(ConfigurationSection section) {
        values.clear();
        values.putAll(DEFAULTS);
        if (section != null) {
            for (String key : section.getKeys(false)) {
                if (section.isString(key)) {
                    values.put(key, section.getString(key));
                }
            }
        }
        prefix = values.getOrDefault("prefix", "");
    }

    /** @return the raw MiniMessage template for a key. */
    public String raw(String key) {
        return values.getOrDefault(key, key);
    }

    /** Renders a message with the prefix applied. */
    public Component get(String key, Map<String, String> placeholders) {
        return Text.mm(prefix + raw(key), Map.of(), placeholders);
    }

    /** Renders a message with the prefix applied and no placeholders. */
    public Component get(String key) {
        return get(key, Map.of());
    }

    /** Sends a prefixed message to a sender. */
    public void send(CommandSender to, String key, Map<String, String> placeholders) {
        to.sendMessage(get(key, placeholders));
    }

    /** Sends a prefixed message to a sender. */
    public void send(CommandSender to, String key) {
        to.sendMessage(get(key, Map.of()));
    }
}
