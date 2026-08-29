package dev.faboit.settingsplus.command;

import dev.faboit.settingsplus.SettingsPlus;
import dev.faboit.settingsplus.config.Category;
import dev.faboit.settingsplus.config.SettingDefinition;
import dev.faboit.settingsplus.config.SettingOption;
import dev.faboit.settingsplus.util.Sched;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** {@code /settingsplus} - operator tooling: reload, verify, inspect and edit stored values. */
public final class SettingsPlusCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS =
            List.of("reload", "verify", "get", "set", "reset", "open", "info");

    private final SettingsPlus plugin;

    public SettingsPlusCommand(SettingsPlus plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            usage(sender, label);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> reload(sender);
            case "verify" -> verify(sender);
            case "info" -> info(sender);
            case "get" -> get(sender, args, label);
            case "set" -> set(sender, args, label);
            case "reset" -> reset(sender, args, label);
            case "open" -> open(sender, args, label);
            default -> usage(sender, label);
        }
        return true;
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("settingsplus.admin.reload")) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        long started = System.nanoTime();
        try {
            plugin.reload();
        } catch (RuntimeException ex) {
            plugin.messages().send(sender, "reload-failed", Map.of("error", String.valueOf(ex.getMessage())));
            return;
        }
        long millis = (System.nanoTime() - started) / 1_000_000L;
        plugin.messages().send(sender, "reloaded", Map.of(
                "categories", String.valueOf(plugin.settings().menu().categories().size()),
                "settings", String.valueOf(plugin.settings().settingCount()),
                "ms", String.valueOf(millis)));
    }

    /**
     * Builds every dialog for the sender without showing them, surfacing any malformed page as a
     * message instead of as a frozen client.
     */
    private void verify(CommandSender sender) {
        if (!sender.hasPermission("settingsplus.admin.reload")) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "players-only");
            return;
        }
        try {
            int built = plugin.dialogs().verify(player);
            sender.sendMessage(plugin.messages().get("reloaded", Map.of(
                    "categories", String.valueOf(built - 1),
                    "settings", String.valueOf(plugin.settings().settingCount()),
                    "ms", "0")));
        } catch (RuntimeException ex) {
            plugin.messages().send(sender, "reload-failed", Map.of("error", String.valueOf(ex.getMessage())));
        }
    }

    private void info(CommandSender sender) {
        if (!sender.hasPermission("settingsplus.admin.reload")) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        sender.sendMessage(plugin.messages().get("usage", Map.of("usage",
                "SettingsPlus " + plugin.getPluginMeta().getVersion()
                        + " | scheduler: " + (Sched.isFolia() ? "Folia" : "Paper")
                        + " | categories: " + plugin.settings().menu().categories().size()
                        + " | settings: " + plugin.settings().settingCount())));
    }

    private void get(CommandSender sender, String[] args, String label) {
        if (!sender.hasPermission("settingsplus.admin.edit")) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        if (args.length < 3) {
            plugin.messages().send(sender, "usage",
                    Map.of("usage", "/" + label + " get <player> <category.setting>"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.messages().send(sender, "unknown-player", Map.of("player", args[1]));
            return;
        }
        Located located = locate(args[2]);
        if (located == null) {
            plugin.messages().send(sender, "unknown-setting", Map.of("setting", args[2]));
            return;
        }
        plugin.messages().send(sender, "value-get", Map.of(
                "setting", args[2],
                "value", plugin.settings().value(target, located.category(), located.setting()),
                "player", target.getName()));
    }

    private void set(CommandSender sender, String[] args, String label) {
        if (!sender.hasPermission("settingsplus.admin.edit")) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        if (args.length < 4) {
            plugin.messages().send(sender, "usage",
                    Map.of("usage", "/" + label + " set <player> <category.setting> <value>"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.messages().send(sender, "unknown-player", Map.of("player", args[1]));
            return;
        }
        Located located = locate(args[2]);
        if (located == null) {
            plugin.messages().send(sender, "unknown-setting", Map.of("setting", args[2]));
            return;
        }
        String value = String.join(" ", List.of(args).subList(3, args.length));
        // The change (and the commands it triggers) must run on the target's region thread.
        Sched.entity(plugin, target, () -> {
            boolean applied = plugin.settings().change(target, located.category(), located.setting(), value, true);
            if (applied) {
                plugin.messages().send(sender, "value-set", Map.of(
                        "setting", args[2], "value", value, "player", target.getName()));
            } else {
                plugin.messages().send(sender, "unknown-value",
                        Map.of("value", value, "setting", args[2]));
            }
        }, null);
    }

    private void reset(CommandSender sender, String[] args, String label) {
        if (!sender.hasPermission("settingsplus.admin.edit")) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        if (args.length < 3) {
            plugin.messages().send(sender, "usage",
                    Map.of("usage", "/" + label + " reset <player> <category>"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.messages().send(sender, "unknown-player", Map.of("player", args[1]));
            return;
        }
        Category category = plugin.settings().menu().category(args[2]);
        if (category == null) {
            plugin.messages().send(sender, "unknown-category", Map.of("category", args[2]));
            return;
        }
        Sched.entity(plugin, target, () -> {
            plugin.settings().resetCategory(target, category);
            plugin.messages().send(sender, "category-reset", Map.of("category", category.id()));
        }, null);
    }

    private void open(CommandSender sender, String[] args, String label) {
        if (!sender.hasPermission("settingsplus.admin.open")) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        if (args.length < 2) {
            plugin.messages().send(sender, "usage",
                    Map.of("usage", "/" + label + " open <player> [category]"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.messages().send(sender, "unknown-player", Map.of("player", args[1]));
            return;
        }
        plugin.dialogs().open(target, args.length > 2 ? args[2] : null);
    }

    /** Resolves {@code category.setting} into the objects it names. */
    private @Nullable Located locate(String path) {
        int dot = path.indexOf('.');
        if (dot <= 0 || dot == path.length() - 1) {
            return null;
        }
        Category category = plugin.settings().menu().category(path.substring(0, dot));
        if (category == null) {
            return null;
        }
        SettingDefinition setting = category.setting(path.substring(dot + 1));
        if (setting == null || !setting.storesValue()) {
            return null;
        }
        return new Located(category, setting);
    }

    private void usage(CommandSender sender, String label) {
        plugin.messages().send(sender, "usage", Map.of("usage",
                "/" + label + " <" + String.join("|", SUBCOMMANDS) + ">"));
    }

    private record Located(Category category, SettingDefinition setting) {
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return prefixed(SUBCOMMANDS, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && List.of("get", "set", "reset", "open").contains(sub)) {
            List<String> names = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                names.add(online.getName());
            }
            return prefixed(names, args[1]);
        }
        if (args.length == 3) {
            if (sub.equals("reset") || sub.equals("open")) {
                return prefixed(categoryIds(), args[2]);
            }
            if (sub.equals("get") || sub.equals("set")) {
                return prefixed(settingPaths(), args[2]);
            }
        }
        if (args.length == 4 && sub.equals("set")) {
            Located located = locate(args[2]);
            if (located != null) {
                List<String> ids = new ArrayList<>();
                for (SettingOption option : located.setting().options()) {
                    ids.add(option.id());
                }
                return prefixed(ids, args[3]);
            }
        }
        return List.of();
    }

    private List<String> categoryIds() {
        List<String> ids = new ArrayList<>();
        for (Category category : plugin.settings().menu().categories()) {
            ids.add(category.id());
        }
        return ids;
    }

    private List<String> settingPaths() {
        List<String> paths = new ArrayList<>();
        for (Category category : plugin.settings().menu().categories()) {
            for (SettingDefinition setting : category.settings()) {
                if (setting.storesValue()) {
                    paths.add(category.id() + "." + setting.key());
                }
            }
        }
        return paths;
    }

    private static List<String> prefixed(List<String> candidates, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(lower)) {
                matches.add(candidate);
            }
        }
        return matches;
    }
}
