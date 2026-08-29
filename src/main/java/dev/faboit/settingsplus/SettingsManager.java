package dev.faboit.settingsplus;

import dev.faboit.settingsplus.api.SettingChangeEvent;
import dev.faboit.settingsplus.config.Category;
import dev.faboit.settingsplus.config.MenuConfig;
import dev.faboit.settingsplus.config.SettingDefinition;
import dev.faboit.settingsplus.config.SettingOption;
import dev.faboit.settingsplus.config.SettingType;
import dev.faboit.settingsplus.storage.PlayerSettings;
import dev.faboit.settingsplus.storage.SettingsStore;
import dev.faboit.settingsplus.util.Sched;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Owns the loaded menu, the per-player value cache and the rules for changing a value.
 *
 * <p>Every value read goes through {@link #value}, which falls back to the setting's configured
 * default, so a freshly joined player and a player whose file was deleted behave identically.</p>
 */
public final class SettingsManager {

    private final SettingsPlus plugin;
    private final SettingsStore store;
    private final Map<UUID, PlayerSettings> cache = new ConcurrentHashMap<>();

    private volatile MenuConfig menu;

    public SettingsManager(SettingsPlus plugin, SettingsStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    public MenuConfig menu() {
        return menu;
    }

    public void setMenu(MenuConfig menu) {
        this.menu = menu;
    }

    // ---------------------------------------------------------------- cache

    /** @return the cached values for a player, creating an empty holder if none is loaded yet. */
    public PlayerSettings settings(UUID uuid) {
        return cache.computeIfAbsent(uuid, PlayerSettings::new);
    }

    /** Loads a player's values off-thread, then optionally re-applies them on their region thread. */
    public void loadAsync(Player player, boolean applyOnJoin, long applyDelayTicks) {
        UUID uuid = player.getUniqueId();
        PlayerSettings holder = settings(uuid);
        Sched.async(plugin, () -> {
            try {
                Map<String, String> values = store.read(uuid);
                holder.replaceAll(values);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Could not read settings for " + uuid, ex);
                return;
            }
            if (applyOnJoin && player.isOnline()) {
                Sched.entityLater(plugin, player, () -> applyAll(player), applyDelayTicks);
            }
        });
    }

    /** Saves a player's values if they changed, then drops them from the cache. */
    public void unload(UUID uuid) {
        PlayerSettings holder = cache.remove(uuid);
        if (holder != null && holder.isDirty()) {
            Sched.async(plugin, () -> persist(holder));
        }
    }

    /** Writes every dirty player to disk. Called on a timer and again on shutdown. */
    public void flushAll(boolean async) {
        for (PlayerSettings holder : cache.values()) {
            if (!holder.isDirty()) {
                continue;
            }
            if (async) {
                Sched.async(plugin, () -> persist(holder));
            } else {
                persist(holder);
            }
        }
    }

    private void persist(PlayerSettings holder) {
        if (!holder.consumeDirty()) {
            return;
        }
        try {
            store.write(holder.uuid(), holder.view());
        } catch (Exception ex) {
            // Put the flag back so the next flush retries rather than silently losing the change.
            holder.markDirty();
            plugin.getLogger().log(Level.WARNING, "Could not save settings for " + holder.uuid(), ex);
        }
    }

    // ---------------------------------------------------------------- values

    /** @return the storage key for a setting, {@code categoryId.settingKey}. */
    public static String key(Category category, SettingDefinition setting) {
        return category.id() + "." + setting.key();
    }

    /** @return the player's current value, falling back to the configured default. */
    public String value(Player player, Category category, SettingDefinition setting) {
        String stored = settings(player.getUniqueId()).get(key(category, setting));
        if (stored == null) {
            return setting.defaultValue();
        }
        // A stored value that is no longer one of the configured options (the operator edited the
        // config) must not leave the player stuck on a value they cannot see.
        if (setting.type() == SettingType.TOGGLE || setting.type() == SettingType.CYCLE) {
            if (setting.option(stored) == null) {
                return setting.defaultValue();
            }
        }
        return stored;
    }

    /** @return the currently selected option, or {@code null} for settings that hold no options. */
    public @Nullable SettingOption selectedOption(Player player, Category category, SettingDefinition setting) {
        String value = value(player, category, setting);
        SettingOption option = setting.option(value);
        if (option == null && !setting.options().isEmpty()) {
            return setting.options().get(0);
        }
        return option;
    }

    /**
     * Changes a value, firing {@link SettingChangeEvent} and running the resulting actions.
     *
     * @param runActions whether to run the new option's actions; {@code false} when only storing
     * @return {@code true} if the value was stored
     */
    public boolean change(Player player, Category category, SettingDefinition setting,
                          String newValue, boolean runActions) {
        if (!setting.storesValue()) {
            return false;
        }
        String oldValue = value(player, category, setting);

        SettingChangeEvent event = new SettingChangeEvent(
                player, category.id(), setting.key(), oldValue, newValue);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }
        String finalValue = event.getNewValue();

        if (setting.type() == SettingType.TOGGLE || setting.type() == SettingType.CYCLE) {
            if (setting.option(finalValue) == null) {
                return false;
            }
        }

        settings(player.getUniqueId()).set(key(category, setting), finalValue);

        if (runActions) {
            plugin.actions().runForValue(player, category, setting, finalValue);
        }
        return true;
    }

    /**
     * Advances a TOGGLE or CYCLE setting by {@code direction} steps, skipping options the player
     * has no permission for.
     *
     * @return the option that was selected, or {@code null} if nothing could be selected
     */
    public @Nullable SettingOption cycle(Player player, Category category,
                                         SettingDefinition setting, int direction) {
        int size = setting.options().size();
        if (size == 0) {
            return null;
        }
        int start = setting.indexOf(value(player, category, setting));
        if (start < 0) {
            start = 0;
        }
        // Walk at most one full lap so a set of fully locked options cannot spin forever.
        for (int step = 1; step <= size; step++) {
            int index = Math.floorMod(start + (direction * step), size);
            SettingOption candidate = setting.options().get(index);
            if (candidate.permission() != null && !player.hasPermission(candidate.permission())) {
                continue;
            }
            if (change(player, category, setting, candidate.id(), true)) {
                return candidate;
            }
            return null;
        }
        return null;
    }

    /** Restores every setting in a category to its default and re-runs the resulting actions. */
    public void resetCategory(Player player, Category category) {
        settings(player.getUniqueId()).clearCategory(category.id());
        for (SettingDefinition setting : category.settings()) {
            if (setting.storesValue() && setting.applyOnJoin()) {
                plugin.actions().runForValue(player, category, setting, setting.defaultValue());
            }
        }
    }

    /** Re-runs the actions for every setting marked {@code apply-on-join}. */
    public void applyAll(Player player) {
        MenuConfig current = menu;
        if (current == null) {
            return;
        }
        for (Category category : current.categories()) {
            if (category.permission() != null && !player.hasPermission(category.permission())) {
                continue;
            }
            for (SettingDefinition setting : category.settings()) {
                if (!setting.storesValue() || !setting.applyOnJoin()) {
                    continue;
                }
                if (setting.permission() != null && !player.hasPermission(setting.permission())) {
                    continue;
                }
                plugin.actions().runForValue(player, category, setting, value(player, category, setting));
            }
        }
    }

    /** @return how many settings the loaded menu defines, for the reload message. */
    public int settingCount() {
        MenuConfig current = menu;
        if (current == null) {
            return 0;
        }
        int total = 0;
        for (Category category : current.categories()) {
            total += category.settings().size();
        }
        return total;
    }
}
