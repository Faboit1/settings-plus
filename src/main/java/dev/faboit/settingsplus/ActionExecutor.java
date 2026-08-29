package dev.faboit.settingsplus;

import dev.faboit.settingsplus.config.ActionSpec;
import dev.faboit.settingsplus.config.Category;
import dev.faboit.settingsplus.config.SettingDefinition;
import dev.faboit.settingsplus.config.SettingOption;
import dev.faboit.settingsplus.util.Placeholders;
import dev.faboit.settingsplus.util.Sched;
import dev.faboit.settingsplus.util.Text;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * Runs the {@code actions:} lists configured against options, settings and buttons.
 *
 * <p>Thread placement matters here: on Folia a player command must run on that player's region
 * thread and a console command on the global region thread, so every dispatch is routed through
 * {@link Sched} rather than executed inline on whichever thread handled the dialog click.</p>
 */
public final class ActionExecutor {

    private final SettingsPlus plugin;

    public ActionExecutor(SettingsPlus plugin) {
        this.plugin = plugin;
    }

    /**
     * Runs the actions attached to a setting's current value: the chosen option's own actions
     * first, then the setting's {@code on-change} list.
     */
    public void runForValue(Player player, Category category, SettingDefinition setting, String value) {
        if (value == null) {
            // A TEXT or SLIDER setting with no default and nothing stored has nothing to apply;
            // running its actions anyway would pass a literal "%value%" to the command.
            return;
        }
        SettingOption option = setting.option(value);
        List<ActionSpec> actions = new ArrayList<>();
        if (option != null) {
            actions.addAll(option.actions());
        }
        actions.addAll(setting.onChange());
        if (actions.isEmpty()) {
            return;
        }
        run(player, actions, placeholders(player, category, setting, value, option));
    }

    /** Builds the placeholders available for a setting in a given state. */
    public Placeholders placeholders(Player player, @Nullable Category category,
                                     @Nullable SettingDefinition setting,
                                     @Nullable String value, @Nullable SettingOption option) {
        Map<String, String> raw = new HashMap<>();
        Map<String, String> parsed = new HashMap<>();

        raw.put("player", player.getName());
        raw.put("uuid", player.getUniqueId().toString());
        raw.put("world", player.getWorld().getName());
        if (category != null) {
            raw.put("category", category.id());
            parsed.put("category_label", category.label());
        }
        if (setting != null) {
            raw.put("setting", setting.key());
            parsed.put("setting_label", setting.label());
        }
        if (value != null) {
            // A player's own typed text can land here, so it is never parsed.
            raw.put("value", value);
            raw.put("value_upper", value.toUpperCase(Locale.ROOT));
        }
        if (option != null) {
            parsed.put("option", option.label());
            raw.put("option_id", option.id());
            raw.put("option_plain", Text.plain(Text.mm(option.label())));
        }
        return new Placeholders(parsed, raw);
    }

    /**
     * Runs a list of actions for a player.
     *
     * @return {@code true} if any action navigated the dialog (opened a page, refreshed or closed),
     *         which tells the caller not to send a page of its own on top
     */
    public boolean run(Player player, List<ActionSpec> actions, Placeholders placeholders) {
        boolean navigated = false;
        for (ActionSpec action : actions) {
            try {
                navigated |= run(player, action, placeholders);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING,
                        "Action '" + action.kind() + ": " + action.argument() + "' failed for "
                                + player.getName(), ex);
            }
        }
        return navigated;
    }

    /** @return {@code true} if this action navigated the dialog. */
    private boolean run(Player player, ActionSpec action, Placeholders placeholders) {
        String argument = Text.fill(action.argument(), placeholders.forCommands());
        switch (action.kind()) {
            case PLAYER_COMMAND -> {
                String command = stripSlash(argument);
                if (!command.isEmpty()) {
                    // performCommand must happen on the thread that owns the player.
                    Sched.entity(plugin, player, () -> player.performCommand(command), null);
                }
            }
            case CONSOLE_COMMAND -> {
                String command = stripSlash(argument);
                if (!command.isEmpty()) {
                    Sched.global(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
                }
            }
            case MESSAGE -> player.sendMessage(Text.mm(argument, placeholders.parsed(), placeholders.raw()));
            case ACTIONBAR -> player.sendActionBar(Text.mm(argument, placeholders.parsed(), placeholders.raw()));
            case BROADCAST -> Bukkit.broadcast(Text.mm(argument, placeholders.parsed(), placeholders.raw()));
            case SOUND -> playSound(player, argument);
            case OPEN -> {
                plugin.dialogs().open(player, argument.isEmpty() ? null : argument);
                return true;
            }
            case CLOSE -> {
                player.closeDialog();
                return true;
            }
            case REFRESH -> {
                plugin.dialogs().reopen(player);
                return true;
            }
            case RESET -> {
                Category target = argument.isEmpty()
                        ? plugin.dialogs().currentCategory(player)
                        : plugin.settings().menu().category(argument);
                if (target != null) {
                    plugin.settings().resetCategory(player, target);
                }
            }
        }
        return false;
    }

    /** Parses {@code "<key> [volume] [pitch]"} and plays it to the player only. */
    private void playSound(Player player, String argument) {
        String[] parts = argument.split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) {
            return;
        }
        Key key;
        try {
            key = Key.key(parts[0]);
        } catch (Exception ex) {
            plugin.getLogger().warning("Invalid sound key in config: '" + parts[0] + "'");
            return;
        }
        float volume = parts.length > 1 ? parseFloat(parts[1], 1.0F) : 1.0F;
        float pitch = parts.length > 2 ? parseFloat(parts[2], 1.0F) : 1.0F;
        player.playSound(Sound.sound(key, Sound.Source.MASTER, volume, pitch));
    }

    private static float parseFloat(String raw, float fallback) {
        try {
            return Float.parseFloat(raw);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static String stripSlash(String command) {
        String trimmed = command.strip();
        return trimmed.startsWith("/") ? trimmed.substring(1).strip() : trimmed;
    }
}
