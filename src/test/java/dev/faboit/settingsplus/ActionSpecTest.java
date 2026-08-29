package dev.faboit.settingsplus;

import dev.faboit.settingsplus.config.ActionSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ActionSpecTest {

    @Test
    void parsesEachPrefix() {
        assertEquals(ActionSpec.Kind.PLAYER_COMMAND, ActionSpec.parse("player: spawn").kind());
        assertEquals(ActionSpec.Kind.CONSOLE_COMMAND, ActionSpec.parse("console: say hi").kind());
        assertEquals(ActionSpec.Kind.MESSAGE, ActionSpec.parse("message: <green>hi").kind());
        assertEquals(ActionSpec.Kind.ACTIONBAR, ActionSpec.parse("actionbar: hi").kind());
        assertEquals(ActionSpec.Kind.BROADCAST, ActionSpec.parse("broadcast: hi").kind());
        assertEquals(ActionSpec.Kind.SOUND, ActionSpec.parse("sound: minecraft:ui.button.click").kind());
        assertEquals(ActionSpec.Kind.OPEN, ActionSpec.parse("open: chat").kind());
        assertEquals(ActionSpec.Kind.CLOSE, ActionSpec.parse("close:").kind());
        assertEquals(ActionSpec.Kind.REFRESH, ActionSpec.parse("refresh:").kind());
        assertEquals(ActionSpec.Kind.RESET, ActionSpec.parse("reset: chat").kind());
    }

    @Test
    void prefixesAreCaseAndDashInsensitive() {
        assertEquals(ActionSpec.Kind.ACTIONBAR, ActionSpec.parse("ACTION-BAR: hi").kind());
        assertEquals(ActionSpec.Kind.CONSOLE_COMMAND, ActionSpec.parse("Console_Command: say hi").kind());
    }

    @Test
    void stripsThePrefixFromTheArgument() {
        assertEquals("showchatfrom everyone", ActionSpec.parse("player: showchatfrom everyone").argument());
    }

    @Test
    void aBareLineIsAPlayerCommand() {
        ActionSpec spec = ActionSpec.parse("spawn");
        assertEquals(ActionSpec.Kind.PLAYER_COMMAND, spec.kind());
        assertEquals("spawn", spec.argument());
    }

    @Test
    void aLeadingSlashIsAccepted() {
        assertEquals("spawn", ActionSpec.parse("/spawn").argument());
    }

    @Test
    void aCommandContainingAColonIsNotMistakenForAPrefix() {
        ActionSpec spec = ActionSpec.parse("say welcome: enjoy your stay");
        assertEquals(ActionSpec.Kind.PLAYER_COMMAND, spec.kind());
        assertEquals("say welcome: enjoy your stay", spec.argument());
    }

    @Test
    void namespacedCommandsSurviveIntact() {
        ActionSpec spec = ActionSpec.parse("minecraft:tp %player% 0 64 0");
        assertEquals(ActionSpec.Kind.PLAYER_COMMAND, spec.kind());
        assertEquals("minecraft:tp %player% 0 64 0", spec.argument());
    }

    @Test
    void blankLinesAreDropped() {
        assertNull(ActionSpec.parse(null));
        assertNull(ActionSpec.parse("   "));
    }
}
