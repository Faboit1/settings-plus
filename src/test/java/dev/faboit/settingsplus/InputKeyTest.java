package dev.faboit.settingsplus;

import dev.faboit.settingsplus.config.RenderMode;
import dev.faboit.settingsplus.config.SettingDefinition;
import dev.faboit.settingsplus.config.SettingType;
import dev.faboit.settingsplus.dialog.DialogManager;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dialog input names are validated by the server as Minecraft macro variable names. A name the
 * server rejects throws while the page is being built, which means the player sees nothing at all -
 * so this rule is worth pinning down in a test.
 */
class InputKeyTest {

    /**
     * The same check the server applies, transcribed from
     * {@code net.minecraft.commands.functions.StringTemplate#isValidVariableName}: every character
     * must be a letter, a digit or an underscore.
     */
    private static boolean isValidInputName(String name) {
        if (name.isEmpty()) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char character = name.charAt(i);
            if (!Character.isLetterOrDigit(character) && character != '_') {
                return false;
            }
        }
        return true;
    }

    private static SettingDefinition setting(String key) {
        return new SettingDefinition(key, SettingType.TEXT, RenderMode.INPUT, key, "%label%", null,
                200, null, null, null, "", List.of(), List.of(), true,
                0F, 100F, 1F, "%s", 64, "", 0, 0);
    }

    @Test
    void hyphenatedKeysAreMadeValid() {
        // These are the keys the shipped settings.yml uses; all three are rejected verbatim.
        for (String key : List.of("chat-filter", "notification-volume", "afk-message")) {
            assertTrue(isValidInputName(DialogManager.inputKey(setting(key), 0)),
                    key + " should sanitise to a valid input name");
        }
    }

    @Test
    void awkwardKeysAreMadeValid() {
        for (String key : List.of("a.b", "a b", "a/b", "a:b", "a!b", "-", "...", "é-ü")) {
            String sanitised = DialogManager.inputKey(setting(key), 3);
            assertTrue(isValidInputName(sanitised), key + " sanitised to '" + sanitised + "'");
        }
    }

    @Test
    void ordinaryKeysStayReadable() {
        assertEquals("show_chat_0", DialogManager.inputKey(setting("show-chat"), 0));
        assertEquals("volume_7", DialogManager.inputKey(setting("volume"), 7));
    }

    @Test
    void keysThatSanitiseIdenticallyStayDistinct() {
        // "a-b" and "a_b" both sanitise to "a_b"; the index keeps them apart.
        assertNotEquals(DialogManager.inputKey(setting("a-b"), 0), DialogManager.inputKey(setting("a_b"), 1));
    }

    @Test
    void everyKeyInACategoryIsUnique() {
        List<String> keys = List.of("a-b", "a_b", "a.b", "a b", "ab");
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < keys.size(); i++) {
            assertTrue(seen.add(DialogManager.inputKey(setting(keys.get(i)), i)),
                    "duplicate input name for " + keys.get(i));
        }
    }

    @Test
    void theRuleRejectsWhatWeExpectItTo() {
        // Guards the transcribed rule itself, so this test cannot quietly pass by being too lax.
        assertTrue(isValidInputName("show_chat_0"));
        assertTrue(!isValidInputName("show-chat"));
        assertTrue(!isValidInputName("a.b"));
        assertTrue(!isValidInputName(""));
    }
}
