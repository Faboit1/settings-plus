package dev.faboit.settingsplus.config;

import java.util.List;

/** The fully parsed {@code settings.yml}: the root menu plus every category. */
public record MenuConfig(
        String title,
        String externalTitle,
        List<String> body,
        int columns,
        boolean canCloseWithEscape,
        AfterAction afterAction,
        /** Button that leaves the root menu. */
        Button exit,
        /** Button that returns from a category to the root menu. */
        Button back,
        /** Button that resets a whole category, or {@code null} to hide it. */
        Button reset,
        List<Category> categories
) {
    public MenuConfig {
        body = List.copyOf(body);
        categories = List.copyOf(categories);
    }

    /** A plain labelled button used for menu navigation. */
    public record Button(String label, String tooltip, int width) {
    }

    /** @return the category with this id, or {@code null}. */
    public Category category(String id) {
        if (id == null) {
            return null;
        }
        for (Category category : categories) {
            if (category.id().equals(id)) {
                return category;
            }
        }
        return null;
    }
}
