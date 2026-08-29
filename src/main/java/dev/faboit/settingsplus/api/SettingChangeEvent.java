package dev.faboit.settingsplus.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired before a player's setting changes, whether from the dialog, a command or the API.
 *
 * <p>Cancelling keeps the old value and suppresses the option's actions. The event is called on the
 * player's region thread, so listeners may touch the player directly.</p>
 */
public class SettingChangeEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String categoryId;
    private final String settingKey;
    private final String oldValue;
    private String newValue;
    private boolean cancelled;

    public SettingChangeEvent(Player player, String categoryId, String settingKey,
                              @Nullable String oldValue, String newValue) {
        super(player);
        this.categoryId = categoryId;
        this.settingKey = settingKey;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    /** @return the id of the category holding the setting. */
    public String getCategoryId() {
        return categoryId;
    }

    /** @return the setting's key within its category. */
    public String getSettingKey() {
        return settingKey;
    }

    /** @return the fully qualified key, {@code categoryId.settingKey}. */
    public String getFullKey() {
        return categoryId + "." + settingKey;
    }

    /** @return the value being replaced, or {@code null} if the player was on the default. */
    public @Nullable String getOldValue() {
        return oldValue;
    }

    /** @return the value about to be stored. */
    public String getNewValue() {
        return newValue;
    }

    /**
     * Substitutes a different value. For a TOGGLE or CYCLE setting this must be a configured option
     * id, otherwise the change is dropped.
     */
    public void setNewValue(String newValue) {
        this.newValue = newValue;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
