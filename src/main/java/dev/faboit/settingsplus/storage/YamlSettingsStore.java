package dev.faboit.settingsplus.storage;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Stores each player's values in {@code plugins/SettingsPlus/playerdata/<uuid>.yml}. */
public final class YamlSettingsStore implements SettingsStore {

    private final Path directory;

    public YamlSettingsStore(Path directory) {
        this.directory = directory;
    }

    @Override
    public Map<String, String> read(UUID uuid) throws Exception {
        Path file = fileFor(uuid);
        if (!Files.isRegularFile(file)) {
            return Map.of();
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        Map<String, String> values = new HashMap<>();
        for (String key : yaml.getKeys(true)) {
            if (yaml.isString(key)) {
                values.put(key, yaml.getString(key));
            }
        }
        return values;
    }

    @Override
    public void write(UUID uuid, Map<String, String> values) throws Exception {
        Files.createDirectories(directory);
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            yaml.set(entry.getKey(), entry.getValue());
        }

        // Write to a sibling temp file then move it into place, so a crash mid-write cannot leave
        // a player with a half-written (and therefore unparseable) settings file.
        Path target = fileFor(uuid);
        Path temp = directory.resolve(uuid + ".yml.tmp");
        yaml.save(temp.toFile());
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path fileFor(UUID uuid) {
        return directory.resolve(uuid + ".yml");
    }

    /** @return the backing directory, created on demand. */
    public File directory() {
        return directory.toFile();
    }
}
