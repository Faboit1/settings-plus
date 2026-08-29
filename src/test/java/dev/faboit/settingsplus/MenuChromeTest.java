package dev.faboit.settingsplus;

import dev.faboit.settingsplus.config.AfterAction;
import dev.faboit.settingsplus.config.ConfigLoader;
import dev.faboit.settingsplus.config.MenuConfig;
import dev.faboit.settingsplus.config.SettingDefinition;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The menu's own chrome: the reset button, the redraw behaviour and slider captions. */
class MenuChromeTest {

    private static YamlConfiguration yaml(String text) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(new StringReader(text));
        } catch (Exception ex) {
            throw new AssertionError("test fixture is not valid YAML", ex);
        }
        return config;
    }

    private static final String BODY = """
            categories:
              c:
                title: "C"
                settings:
                  s: { type: CYCLE, label: "S", options: [a, b] }
            """;

    // ------------------------------------------------------------ reset button

    @Test
    void theResetButtonIsOffUnlessAskedFor() {
        // It throws away a player's choices, so it must never appear by accident.
        MenuConfig menu = new ConfigLoader().load(yaml("menu:\n  title: \"S\"\n" + BODY));
        assertNull(menu.reset(), "no buttons section at all should mean no reset button");

        MenuConfig withButtons = new ConfigLoader().load(yaml("""
                menu:
                  title: "S"
                  buttons:
                    back: { label: "Back" }
                """ + BODY));
        assertNull(withButtons.reset(), "a buttons section that never mentions reset should mean no reset button");
    }

    @Test
    void theResetButtonAppearsWhenEnabled() {
        MenuConfig menu = new ConfigLoader().load(yaml("""
                menu:
                  title: "S"
                  buttons:
                    reset: { enabled: true, label: "<gold>Reset page" }
                """ + BODY));
        assertNotNull(menu.reset());
        assertEquals("<gold>Reset page", menu.reset().label());
    }

    @Test
    void enabledFalseHidesItEvenWithAFullyConfiguredButton() {
        MenuConfig menu = new ConfigLoader().load(yaml("""
                menu:
                  title: "S"
                  buttons:
                    reset: { enabled: false, label: "Reset", tooltip: "t", width: 120 }
                """ + BODY));
        assertNull(menu.reset());
    }

    @Test
    void aCategoryCanOptOutOfTheResetButton() {
        MenuConfig menu = new ConfigLoader().load(yaml("""
                menu:
                  title: "S"
                  buttons:
                    reset: { enabled: true }
                categories:
                  keeps:
                    title: "K"
                    settings:
                      s: { type: CYCLE, label: "S", options: [a, b] }
                  hides:
                    title: "H"
                    show-reset: false
                    settings:
                      s: { type: CYCLE, label: "S", options: [a, b] }
                """));
        assertNotNull(menu.reset(), "the button is enabled menu-wide");
        assertTrue(menu.category("keeps").showReset());
        assertFalse(menu.category("hides").showReset());
    }

    // ------------------------------------------------------------ redraw

    @Test
    void redrawsLeaveThePageUpByDefault() {
        // WAIT_FOR_RESPONSE shows a "waiting for server" screen on every toggle, which reads as a
        // flicker; NONE keeps the current page until the redraw arrives.
        MenuConfig menu = new ConfigLoader().load(yaml("menu:\n  title: \"S\"\n" + BODY));
        assertEquals(AfterAction.NONE, menu.afterAction());
    }

    @Test
    void theRedrawBehaviourStaysConfigurable() {
        MenuConfig menu = new ConfigLoader().load(yaml("""
                menu:
                  title: "S"
                  after-action: WAIT_FOR_RESPONSE
                """ + BODY));
        assertEquals(AfterAction.WAIT_FOR_RESPONSE, menu.afterAction());
    }

    @Test
    void pauseIsOffByDefaultSoTheDefaultRedrawIsLegal() {
        // The client rejects a paused dialog whose after_action never unpauses, so the shipped
        // pairing of NONE + pause:false has to hold.
        MenuConfig menu = new ConfigLoader().load(yaml("menu:\n  title: \"S\"\n" + BODY));
        assertEquals(AfterAction.NONE, menu.afterAction());
        assertFalse(menu.pause());
    }

    @Test
    void anIllegalPausePairingIsCorrectedRatherThanSentToTheClient() {
        ConfigLoader loader = new ConfigLoader();
        MenuConfig menu = loader.load(yaml("""
                menu:
                  title: "S"
                  after-action: NONE
                  pause: true
                """ + BODY));
        assertFalse(menu.pause(), "the combination the client refuses must not survive parsing");
        assertTrue(loader.warnings().stream().anyMatch(w -> w.contains("unpause")),
                loader.warnings().toString());
    }

    @Test
    void pauseIsAllowedAlongsideAnAfterActionThatUnpauses() {
        ConfigLoader loader = new ConfigLoader();
        MenuConfig menu = loader.load(yaml("""
                menu:
                  title: "S"
                  after-action: CLOSE
                  pause: true
                """ + BODY));
        assertTrue(menu.pause());
        assertEquals(0, loader.warnings().size(), loader.warnings().toString());
    }

    // ------------------------------------------------------------ slider captions

    private static SettingDefinition slider(ConfigLoader loader, String format) {
        return loader.load(yaml("""
                menu:
                  title: "S"
                categories:
                  c:
                    title: "C"
                    settings:
                      volume:
                        type: SLIDER
                        label: "Volume"
                        min: 0
                        max: 100
                        slider-format: "%FORMAT%"
                """.replace("%FORMAT%", format))).category("c").setting("volume");
    }

    @Test
    void aCaptionWithOnlyOnePlaceholderIsCorrected() {
        // Minecraft passes the caption two arguments, label then value. With one placeholder the
        // label consumes it and the number is never drawn - the caption reads "Volume: Volume%".
        ConfigLoader loader = new ConfigLoader();
        assertEquals("%s: %s", slider(loader, "Volume: %s%%").sliderFormat());
        assertTrue(loader.warnings().stream().anyMatch(w -> w.contains("slider-format")),
                "the correction should be reported: " + loader.warnings());
    }

    @Test
    void aCaptionWithBothPlaceholdersIsKept() {
        ConfigLoader loader = new ConfigLoader();
        assertEquals("%s: %s%%", slider(loader, "%s: %s%%").sliderFormat());
        assertEquals(0, loader.warnings().size(), loader.warnings().toString());
    }

    @Test
    void positionalPlaceholdersAreAccepted() {
        ConfigLoader loader = new ConfigLoader();
        assertEquals("%2$s (%1$s)", slider(loader, "%2$s (%1$s)").sliderFormat());
        assertEquals(0, loader.warnings().size(), loader.warnings().toString());
    }

    @Test
    void escapedPercentsDoNotCountAsPlaceholders() {
        // "100%% done" has no arguments at all, only an escaped percent sign.
        ConfigLoader loader = new ConfigLoader();
        assertEquals("%s: %s", slider(loader, "100%% done").sliderFormat());
        assertTrue(loader.warnings().stream().anyMatch(w -> w.contains("slider-format")));
    }

    @Test
    void theDefaultCaptionShowsTheValue() {
        ConfigLoader loader = new ConfigLoader();
        MenuConfig menu = loader.load(yaml("""
                menu:
                  title: "S"
                categories:
                  c:
                    title: "C"
                    settings:
                      volume: { type: SLIDER, label: "Volume", min: 0, max: 10 }
                """));
        assertEquals("%s: %s", menu.category("c").setting("volume").sliderFormat());
        assertEquals(0, loader.warnings().size());
    }
}
