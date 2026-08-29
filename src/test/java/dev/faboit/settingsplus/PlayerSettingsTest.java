package dev.faboit.settingsplus;

import dev.faboit.settingsplus.storage.PlayerSettings;
import dev.faboit.settingsplus.storage.YamlSettingsStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerSettingsTest {

    @Test
    void tracksDirtinessOnlyForRealChanges() {
        PlayerSettings settings = new PlayerSettings(UUID.randomUUID());
        assertFalse(settings.isDirty());

        settings.set("chat.show-chat", "friends");
        assertTrue(settings.isDirty());

        assertTrue(settings.consumeDirty());
        assertFalse(settings.isDirty(), "consuming the flag should clear it");

        settings.set("chat.show-chat", "friends");
        assertFalse(settings.isDirty(), "re-writing the same value is not a change");

        settings.set("chat.show-chat", "nobody");
        assertTrue(settings.isDirty());
    }

    @Test
    void clearCategoryOnlyTouchesThatCategory() {
        PlayerSettings settings = new PlayerSettings(UUID.randomUUID());
        settings.set("chat.show-chat", "friends");
        settings.set("chat.filter", "strict");
        settings.set("hud.scale", "3");

        settings.clearCategory("chat");

        assertNull(settings.get("chat.show-chat"));
        assertNull(settings.get("chat.filter"));
        assertEquals("3", settings.get("hud.scale"));
    }

    @Test
    void clearCategoryDoesNotMatchAPrefixOfAnotherName() {
        PlayerSettings settings = new PlayerSettings(UUID.randomUUID());
        settings.set("chat.a", "1");
        settings.set("chatting.a", "2");

        settings.clearCategory("chat");

        assertNull(settings.get("chat.a"));
        assertEquals("2", settings.get("chatting.a"), "'chatting' is a different category");
    }

    @Test
    void loadingFromDiskDoesNotMarkThePlayerDirty() {
        PlayerSettings settings = new PlayerSettings(UUID.randomUUID());
        settings.replaceAll(Map.of("chat.show-chat", "nobody"));
        assertFalse(settings.isDirty(), "a fresh load has nothing to write back");
        assertEquals("nobody", settings.get("chat.show-chat"));
    }

    @Test
    void valuesSurviveARoundTripThroughDisk(@TempDir Path directory) throws Exception {
        YamlSettingsStore store = new YamlSettingsStore(directory);
        UUID uuid = UUID.randomUUID();

        assertEquals(Map.of(), store.read(uuid), "an unknown player reads as empty, not as an error");

        Map<String, String> written = Map.of(
                "chat.show-chat", "friends",
                "notifications.afk-message", "Back soon! <not a tag>");
        store.write(uuid, written);
        assertEquals(written, store.read(uuid));

        // A second write replaces rather than merges.
        store.write(uuid, Map.of("chat.show-chat", "nobody"));
        assertEquals(Map.of("chat.show-chat", "nobody"), store.read(uuid));
    }
}
