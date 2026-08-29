package dev.faboit.settingsplus;

import dev.faboit.settingsplus.config.ActionSpec;
import dev.faboit.settingsplus.config.Category;
import dev.faboit.settingsplus.config.ConfigException;
import dev.faboit.settingsplus.config.ConfigLoader;
import dev.faboit.settingsplus.config.MenuConfig;
import dev.faboit.settingsplus.config.RenderMode;
import dev.faboit.settingsplus.config.SettingDefinition;
import dev.faboit.settingsplus.config.SettingOption;
import dev.faboit.settingsplus.config.SettingType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the config parser against Bukkit's real {@code YamlConfiguration}, which needs no
 * running server - so the shipped {@code settings.yml} is checked on every build.
 */
class ConfigLoaderTest {

    private static YamlConfiguration yaml(String text) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(new StringReader(text));
        } catch (Exception ex) {
            throw new AssertionError("test fixture is not valid YAML", ex);
        }
        return config;
    }

    private static MenuConfig load(String text) {
        return new ConfigLoader().load(yaml(text));
    }

    private static final String MINIMAL_MENU = """
            menu:
              title: "Settings"
            categories:
              chat:
                title: "Chat"
                settings:
                  show-chat:
                    type: CYCLE
                    label: "Show chat"
                    default: everyone
                    options:
                      - id: everyone
                        label: "<green>ON"
                        tooltip: "<green>Everyone"
                        actions:
                          - "player: showchatfrom everyone"
                      - id: friends
                        label: "<yellow>FRIENDS ONLY"
                        actions:
                          - "player: showchatfrom friends"
                      - id: nobody
                        label: "<red>OFF"
                        actions:
                          - "player: showchatfrom nobody"
            """;

    @Test
    void parsesTheShowChatExample() {
        MenuConfig menu = load(MINIMAL_MENU);

        assertEquals(1, menu.categories().size());
        Category chat = menu.category("chat");
        assertNotNull(chat);

        SettingDefinition showChat = chat.setting("show-chat");
        assertNotNull(showChat);
        assertEquals(SettingType.CYCLE, showChat.type());
        assertEquals(RenderMode.BUTTON, showChat.render(), "cycles default to buttons so options can have hovers");
        assertEquals("everyone", showChat.defaultValue());
        assertEquals(3, showChat.options().size());

        SettingOption friends = showChat.option("friends");
        assertNotNull(friends);
        assertEquals(1, friends.actions().size());
        assertEquals(ActionSpec.Kind.PLAYER_COMMAND, friends.actions().get(0).kind());
        assertEquals("showchatfrom friends", friends.actions().get(0).argument());
    }

    @Test
    void keepsOptionOrderSoCyclingIsPredictable() {
        SettingDefinition setting = load(MINIMAL_MENU).category("chat").setting("show-chat");
        assertEquals(List.of("everyone", "friends", "nobody"),
                setting.options().stream().map(SettingOption::id).toList());
        assertEquals(0, setting.indexOf("everyone"));
        assertEquals(2, setting.indexOf("nobody"));
        assertEquals(-1, setting.indexOf("not-an-option"));
    }

    @Test
    void keepsCategoryOrderFromTheFile() {
        MenuConfig menu = load("""
                menu:
                  title: "S"
                categories:
                  zulu:
                    title: "Z"
                    settings:
                      a: { type: ACTION, label: "A" }
                  alpha:
                    title: "A"
                    settings:
                      a: { type: ACTION, label: "A" }
                """);
        assertEquals(List.of("zulu", "alpha"), menu.categories().stream().map(Category::id).toList());
    }

    @Test
    void slidersAndTextAlwaysRenderAsNativeInputs() {
        MenuConfig menu = load("""
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
                        step: 5
                      note:
                        type: TEXT
                        label: "Note"
                """);
        Category category = menu.category("c");
        assertEquals(RenderMode.INPUT, category.setting("volume").render());
        assertEquals(RenderMode.INPUT, category.setting("note").render());
        assertEquals(5.0F, category.setting("volume").sliderStep());
    }

    @Test
    void aSliderAskedToRenderAsAButtonIsCorrectedWithAWarning() {
        ConfigLoader loader = new ConfigLoader();
        MenuConfig menu = loader.load(yaml("""
                menu:
                  title: "S"
                categories:
                  c:
                    title: "C"
                    settings:
                      volume:
                        type: SLIDER
                        render: BUTTON
                        label: "Volume"
                """));
        assertEquals(RenderMode.INPUT, menu.category("c").setting("volume").render());
        assertTrue(loader.warnings().stream().anyMatch(w -> w.contains("render: INPUT")),
                "expected a warning about the corrected render mode, got " + loader.warnings());
    }

    @Test
    void aDefaultThatIsNotAnOptionFallsBackToTheFirstOne() {
        ConfigLoader loader = new ConfigLoader();
        MenuConfig menu = loader.load(yaml("""
                menu:
                  title: "S"
                categories:
                  c:
                    title: "C"
                    settings:
                      s:
                        type: CYCLE
                        label: "S"
                        default: typo
                        options:
                          - id: a
                          - id: b
                """));
        assertEquals("a", menu.category("c").setting("s").defaultValue());
        assertTrue(loader.warnings().stream().anyMatch(w -> w.contains("typo")));
    }

    @Test
    void aMissingDefaultBecomesTheFirstOption() {
        MenuConfig menu = load("""
                menu:
                  title: "S"
                categories:
                  c:
                    title: "C"
                    settings:
                      s:
                        type: CYCLE
                        label: "S"
                        options:
                          - id: first
                          - id: second
                """);
        assertEquals("first", menu.category("c").setting("s").defaultValue());
    }

    @Test
    void widthsAndColumnsAreClampedRatherThanSentToTheClient() {
        ConfigLoader loader = new ConfigLoader();
        MenuConfig menu = loader.load(yaml("""
                menu:
                  title: "S"
                  columns: 99
                categories:
                  c:
                    title: "C"
                    width: 99999
                    settings:
                      s: { type: ACTION, label: "S", width: 0 }
                """));
        assertEquals(8, menu.columns());
        assertEquals(1024, menu.category("c").width());
        assertEquals(1, menu.category("c").setting("s").width());
        assertEquals(3, loader.warnings().size(), "each clamp should be reported: " + loader.warnings());
    }

    @Test
    void shorthandStringOptionsBecomeIdAndLabel() {
        SettingDefinition setting = load("""
                menu:
                  title: "S"
                categories:
                  c:
                    title: "C"
                    settings:
                      s:
                        type: CYCLE
                        label: "S"
                        options: [low, high]
                """).category("c").setting("s");
        assertEquals(List.of("low", "high"), setting.options().stream().map(SettingOption::id).toList());
        assertEquals("low", setting.option("low").label());
        assertNull(setting.option("low").tooltip());
    }

    @Test
    void optionsMayAlsoBeWrittenAsAKeyedSection() {
        SettingDefinition setting = load("""
                menu:
                  title: "S"
                categories:
                  c:
                    title: "C"
                    settings:
                      s:
                        type: CYCLE
                        label: "S"
                        options:
                          everyone:
                            label: "<green>ON"
                            actions: ["player: showchatfrom everyone"]
                          nobody:
                            label: "<red>OFF"
                """).category("c").setting("s");
        assertEquals(List.of("everyone", "nobody"), setting.options().stream().map(SettingOption::id).toList());
        assertEquals(1, setting.option("everyone").actions().size());
    }

    @Test
    void aTypoInATypeNamesTheConfigPath() {
        ConfigException thrown = assertThrows(ConfigException.class, () -> load("""
                menu:
                  title: "S"
                categories:
                  chat:
                    title: "C"
                    settings:
                      show-chat:
                        type: CYLE
                        label: "S"
                """));
        assertTrue(thrown.getMessage().contains("categories.chat.settings.show-chat.type"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("CYLE"), thrown.getMessage());
    }

    @Test
    void aCycleWithNoOptionsIsRejected() {
        ConfigException thrown = assertThrows(ConfigException.class, () -> load("""
                menu:
                  title: "S"
                categories:
                  c:
                    title: "C"
                    settings:
                      s:
                        type: CYCLE
                        label: "S"
                """));
        assertTrue(thrown.getMessage().contains("options"), thrown.getMessage());
    }

    @Test
    void aSliderWithAnInvertedRangeIsRejected() {
        ConfigException thrown = assertThrows(ConfigException.class, () -> load("""
                menu:
                  title: "S"
                categories:
                  c:
                    title: "C"
                    settings:
                      s:
                        type: SLIDER
                        label: "S"
                        min: 100
                        max: 10
                """));
        assertTrue(thrown.getMessage().contains("max"), thrown.getMessage());
    }

    @Test
    void aMissingCategoriesSectionIsRejected() {
        assertThrows(ConfigException.class, () -> load("menu:\n  title: \"S\"\n"));
    }

    @Test
    void actionAndSubmenuSettingsHoldNoValue() {
        Category category = load("""
                menu:
                  title: "S"
                categories:
                  c:
                    title: "C"
                    settings:
                      go: { type: SUBMENU, label: "Go", default: other }
                      ping: { type: ACTION, label: "Ping" }
                      real: { type: CYCLE, label: "R", options: [a, b] }
                """).category("c");
        assertFalse(category.setting("go").storesValue());
        assertFalse(category.setting("ping").storesValue());
        assertTrue(category.setting("real").storesValue());
    }

    @Test
    void theShippedSettingsFileParsesCleanly() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        try (var stream = getClass().getClassLoader().getResourceAsStream("settings.yml")) {
            assertNotNull(stream, "settings.yml should be on the test classpath");
            config.load(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8));
        }
        ConfigLoader loader = new ConfigLoader();
        MenuConfig menu = loader.load(config);

        assertEquals(List.of(), loader.warnings(), "the shipped config should produce no warnings");
        assertFalse(menu.categories().isEmpty());

        // The example from the brief must survive edits to the shipped file.
        SettingDefinition showChat = menu.category("chat").setting("show-chat");
        assertNotNull(showChat, "the shipped config should keep the show-chat example");
        assertEquals(List.of("everyone", "friends", "nobody"),
                showChat.options().stream().map(SettingOption::id).toList());
        for (SettingOption option : showChat.options()) {
            assertNotNull(option.tooltip(), "each show-chat option should demonstrate a hover tooltip");
            assertEquals("showchatfrom " + option.id(), option.actions().get(0).argument());
        }
    }
}
