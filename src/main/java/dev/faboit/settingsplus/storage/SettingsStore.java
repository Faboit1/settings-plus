package dev.faboit.settingsplus.storage;

import java.util.Map;
import java.util.UUID;

/** Persistence for player values. Implementations are called from the async scheduler only. */
public interface SettingsStore {

    /** Reads a player's stored values, returning an empty map when they have none. */
    Map<String, String> read(UUID uuid) throws Exception;

    /** Writes a player's values, replacing whatever was there. */
    void write(UUID uuid, Map<String, String> values) throws Exception;
}
