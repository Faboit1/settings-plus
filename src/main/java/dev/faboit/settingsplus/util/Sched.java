package dev.faboit.settingsplus.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;

/**
 * Scheduling helpers that behave correctly on both Paper and Folia.
 *
 * <p>Folia splits the server into independently ticking regions, so there is no single "main
 * thread". Every one of these APIs ({@code getAsyncScheduler}, {@code getGlobalRegionScheduler},
 * {@code Entity#getScheduler}) is present in plain {@code paper-api} and works identically on
 * Paper, which is why the plugin never needs a Folia-specific code path or a reflective shim.</p>
 */
public final class Sched {

    private static final boolean FOLIA = detectFolia();

    private Sched() {
    }

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    /** @return {@code true} when running on a Folia server. */
    public static boolean isFolia() {
        return FOLIA;
    }

    /**
     * Runs a task on the thread owning the given entity's region. On Folia this is the only thread
     * allowed to touch that entity; on Paper it is the main thread.
     *
     * <p>When the caller already holds that region the task runs inline rather than being queued.
     * That keeps a sequence of actions in the order the operator wrote them: re-queuing each one
     * would let a later {@code open:} be overtaken by work scheduled after it.</p>
     *
     * @param retired run instead if the entity is removed before the task fires
     */
    public static void entity(Plugin plugin, Entity entity, Runnable task, Runnable retired) {
        if (Bukkit.isOwnedByCurrentRegion(entity)) {
            task.run();
            return;
        }
        entity.getScheduler().run(plugin, ignored -> task.run(), retired);
    }

    /** Runs a task on the entity's region after a delay in ticks (minimum 1). */
    public static void entityLater(Plugin plugin, Entity entity, Runnable task, long delayTicks) {
        entity.getScheduler().runDelayed(plugin, ignored -> task.run(), null, Math.max(1L, delayTicks));
    }

    /** Runs a task on the global region - for console commands and other world-independent work. */
    public static void global(Plugin plugin, Runnable task) {
        Bukkit.getGlobalRegionScheduler().run(plugin, ignored -> task.run());
    }

    /** Runs a task off the server threads, for disk I/O. */
    public static void async(Plugin plugin, Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plugin, ignored -> task.run());
    }

    /** Repeats a task off the server threads. */
    public static void asyncTimer(Plugin plugin, Runnable task, long periodSeconds) {
        long period = Math.max(1L, periodSeconds);
        Bukkit.getAsyncScheduler().runAtFixedRate(
                plugin, ignored -> task.run(), period, period, TimeUnit.SECONDS);
    }

    /** Cancels every task this plugin scheduled. Safe to call from {@code onDisable}. */
    public static void cancelAll(Plugin plugin) {
        Bukkit.getAsyncScheduler().cancelTasks(plugin);
        Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
    }
}
