package dev.faboit.settingsplus;

import dev.faboit.settingsplus.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextTest {

    @Test
    void parsesMiniMessage() {
        Component parsed = Text.mm("<green>ON");
        assertEquals("ON", Text.plain(parsed));
        assertEquals(NamedTextColor.GREEN, parsed.color());
    }

    @Test
    void percentPlaceholdersResolveLikeTags() {
        assertEquals("Show chat: ON", Text.plain(Text.mm(
                "%label%: %option%",
                Map.of("label", "Show chat", "option", "<green>ON"))));
    }

    @Test
    void angleBracketPlaceholdersWorkToo() {
        assertEquals("Show chat: ON", Text.plain(Text.mm(
                "<label>: <option>",
                Map.of("label", "Show chat", "option", "<green>ON"))));
    }

    @Test
    void parsedPlaceholdersKeepTheirFormatting() {
        Component component = Text.mm("%option%", Map.of("option", "<red>OFF"));
        assertEquals(NamedTextColor.RED, component.children().isEmpty()
                ? component.color() : component.children().get(0).color());
    }

    @Test
    void rawPlaceholdersCannotInjectFormatting() {
        // A player typing "<red>oops" into a text field must not colour the menu.
        String rendered = Text.plain(Text.mm("Note: %note%", Map.of(), Map.of("note", "<red>oops")));
        assertEquals("Note: <red>oops", rendered);
    }

    @Test
    void unknownPlaceholdersAreLeftAlone() {
        // Another plugin's placeholder should survive rather than being eaten.
        assertEquals("Balance: %vault_eco_balance%",
                Text.plain(Text.mm("Balance: %vault_eco_balance%", Map.of("player", "Steve"))));
    }

    @Test
    void aMalformedTemplateFallsBackToItsOwnText() {
        // A broken tag must not throw and take a whole menu down with it.
        String rendered = Text.plain(Text.mm("<not_a_real_tag:>broken"));
        assertTrue(rendered.contains("broken"), rendered);
    }

    @Test
    void fillSubstitutesPlainStringsForCommands() {
        assertEquals("showchatfrom friends",
                Text.fill("showchatfrom %value%", Map.of("value", "friends")));
    }

    @Test
    void fillLeavesUnknownPlaceholdersForOtherPlugins() {
        assertEquals("pay %vault_balance% Steve",
                Text.fill("pay %vault_balance% %player%", Map.of("player", "Steve")));
    }

    @Test
    void fillHandlesDollarAndBackslashSafely() {
        // These are regex replacement metacharacters; they must not corrupt the output.
        assertEquals("say $1 \\o/", Text.fill("say %a% %b%", Map.of("a", "$1", "b", "\\o/")));
    }
}
