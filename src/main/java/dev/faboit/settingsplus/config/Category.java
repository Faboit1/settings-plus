package dev.faboit.settingsplus.config;

import java.util.List;

/**
 * A page of the settings menu. Categories appear as buttons on the root dialog and each opens its
 * own dialog holding that category's settings.
 */
public record Category(
        String id,
        /** MiniMessage title of the category's own dialog. */
        String title,
        /** MiniMessage title shown on the pause-screen entry, or {@code null} to reuse the title. */
        String externalTitle,
        /** MiniMessage label of the button that opens this category from the root menu. */
        String label,
        /** MiniMessage hover of that button. */
        String tooltip,
        /** MiniMessage lines shown at the top of this category's dialog. */
        List<String> body,
        /** Material name of an item shown beside the body, or {@code null} for no icon. */
        String icon,
        /** Permission required to see this category, or {@code null}. */
        String permission,
        /** Number of button columns, 1-8. */
        int columns,
        /** Width of the root-menu button that opens this category. */
        int width,
        boolean canCloseWithEscape,
        List<SettingDefinition> settings
) {
    public Category {
        body = List.copyOf(body);
        settings = List.copyOf(settings);
    }

    /** @return the setting with this key, or {@code null}. */
    public SettingDefinition setting(String key) {
        for (SettingDefinition setting : settings) {
            if (setting.key().equals(key)) {
                return setting;
            }
        }
        return null;
    }
}
