package dev.faboit.settingsplus;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Loads a player's values when they join and writes them back when they leave. */
public final class PlayerListener implements Listener {

    private final SettingsPlus plugin;

    public PlayerListener(SettingsPlus plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        plugin.settings().loadAsync(event.getPlayer(), plugin.applyOnJoin(), plugin.applyOnJoinDelayTicks());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.dialogs().forget(event.getPlayer().getUniqueId());
        plugin.settings().unload(event.getPlayer().getUniqueId());
    }
}
