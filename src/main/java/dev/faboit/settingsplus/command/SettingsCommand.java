package dev.faboit.settingsplus.command;

import dev.faboit.settingsplus.SettingsPlus;
import dev.faboit.settingsplus.config.Category;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** {@code /settings [category]} - opens the menu for the player who ran it. */
public final class SettingsCommand implements CommandExecutor, TabCompleter {

    private final SettingsPlus plugin;

    public SettingsCommand(SettingsPlus plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "players-only");
            return true;
        }
        plugin.dialogs().open(player, args.length > 0 ? args[0] : null);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length != 1 || !(sender instanceof Player player)) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (Category category : plugin.settings().menu().categories()) {
            if (category.permission() != null && !player.hasPermission(category.permission())) {
                continue;
            }
            if (category.id().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                matches.add(category.id());
            }
        }
        return matches;
    }
}
