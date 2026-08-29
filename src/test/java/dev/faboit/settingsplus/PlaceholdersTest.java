package dev.faboit.settingsplus;

import dev.faboit.settingsplus.util.Placeholders;
import dev.faboit.settingsplus.util.Text;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PlaceholdersTest {

    /** The shape {@code ActionExecutor} builds for a cycling setting mid-render. */
    private static Placeholders forShowChat() {
        return new Placeholders(
                Map.of("option", "<green><bold>ON</bold>", "setting_label", "<white>Show chat"),
                Map.of("player", "Steve", "value", "everyone", "option_id", "everyone",
                        "option_plain", "ON"));
    }

    @Test
    void optionLabelsKeepTheirFormattingInAButtonFace() {
        // The bug this guards: `option` living in the raw map made every option label render as
        // the literal text "<green><bold>ON</bold>" on the button.
        Placeholders placeholders = forShowChat()
                .withParsed("label", "<white>Show chat");

        Component rendered = Text.mm("%label% <dark_gray>»</dark_gray> %option%",
                placeholders.parsed(), placeholders.raw());

        String plain = Text.plain(rendered);
        assertEquals("Show chat » ON", plain);
        assertFalse(plain.contains("<green>"), "MiniMessage tags must not survive into the label");
    }

    @Test
    void playerSuppliedValuesAreStillNeverParsed() {
        Placeholders placeholders = new Placeholders(Map.of(), Map.of("value", "<red>oops"));
        assertEquals("Note: <red>oops",
                Text.plain(Text.mm("Note: %value%", placeholders.parsed(), placeholders.raw())));
    }

    @Test
    void commandsGetPlainTextForFormattedNames() {
        // A command line is not MiniMessage, so %option% has to collapse to "ON".
        Map<String, String> forCommands = forShowChat().forCommands();
        assertEquals("ON", forCommands.get("option"));
        assertEquals("Show chat", forCommands.get("setting_label"));
        assertEquals("everyone", forCommands.get("value"));

        assertEquals("showchatfrom everyone", Text.fill("showchatfrom %value%", forCommands));
    }

    @Test
    void noNameIsDefinedInBothMaps() {
        Placeholders placeholders = forShowChat();
        for (String name : placeholders.parsed().keySet()) {
            assertFalse(placeholders.raw().containsKey(name),
                    "'" + name + "' is in both maps, so which meaning wins is undefined");
        }
    }

    @Test
    void withParsedDoesNotMutateTheOriginal() {
        Placeholders original = forShowChat();
        original.withParsed("label", "x");
        assertFalse(original.parsed().containsKey("label"));
    }
}
