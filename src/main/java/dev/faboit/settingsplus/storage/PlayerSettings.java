package dev.faboit.settingsplus.storage;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One player's stored values, keyed by {@code categoryId.settingKey}.
 *
 * <p>Backed by a concurrent map because it is written from region threads (when a player clicks)
 * and read from the async scheduler (when it is saved).</p>
 */
public final class PlayerSettings {

    private final UUID uuid;
    private final Map<String, String> values = new ConcurrentHashMap<>();
    private final AtomicBoolean dirty = new AtomicBoolean(false);

    public PlayerSettings(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID uuid() {
        return uuid;
    }

    /** @return the stored value, or {@code null} if the player never set this one. */
    public @Nullable String get(String key) {
        return values.get(key);
    }

    /** Stores a value and marks the player as needing a save. */
    public void set(String key, @Nullable String value) {
        if (value == null) {
            if (values.remove(key) != null) {
                dirty.set(true);
            }
            return;
        }
        String previous = values.put(key, value);
        if (!value.equals(previous)) {
            dirty.set(true);
        }
    }

    /** Removes every value whose key starts with {@code categoryId + "."}. */
    public void clearCategory(String categoryId) {
        String prefix = categoryId + ".";
        if (values.keySet().removeIf(key -> key.startsWith(prefix))) {
            dirty.set(true);
        }
    }

    /** Removes every stored value. */
    public void clearAll() {
        if (!values.isEmpty()) {
            values.clear();
            dirty.set(true);
        }
    }

    /** Replaces all values without marking the player dirty - used when loading from disk. */
    public void replaceAll(Map<String, String> loaded) {
        values.clear();
        values.putAll(loaded);
        dirty.set(false);
    }

    public Map<String, String> view() {
        return Collections.unmodifiableMap(values);
    }

    /** @return {@code true} if there are unsaved changes, atomically clearing the flag. */
    public boolean consumeDirty() {
        return dirty.compareAndSet(true, false);
    }

    public boolean isDirty() {
        return dirty.get();
    }

    /** Re-marks the player dirty after a failed save, so the next flush retries. */
    public void markDirty() {
        dirty.set(true);
    }
}
