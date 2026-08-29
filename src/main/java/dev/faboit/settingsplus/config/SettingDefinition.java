package dev.faboit.settingsplus.config;

import java.util.List;

/**
 * A single configurable setting inside a {@link Category}.
 *
 * <p>Not every field applies to every {@link SettingType}: the slider fields are only read for
 * {@link SettingType#SLIDER}, the text fields only for {@link SettingType#TEXT}, and
 * {@link #options()} only for {@code TOGGLE} and {@code CYCLE}.</p>
 */
public record SettingDefinition(
        String key,
        SettingType type,
        RenderMode render,
        /** MiniMessage name of the setting, e.g. {@code <white>Show Chat}. */
        String label,
        /** MiniMessage template for a BUTTON's face. {@code %label%} and {@code %option%} are filled in. */
        String buttonFormat,
        /** MiniMessage hover shown when the option itself defines none, or {@code null}. */
        String tooltip,
        /** Button width in pixels, 1-1024. */
        int width,
        /** Permission required to see and change this setting, or {@code null}. */
        String permission,
        /** Shown instead of the value when the player lacks {@link #permission()}; hidden if {@code null}. */
        String lockedLabel,
        /** MiniMessage hover shown when the setting is locked. */
        String lockedTooltip,
        /** Stored value used until the player changes it. */
        String defaultValue,
        List<SettingOption> options,
        /** Actions run after any change, in addition to the chosen option's own actions. */
        List<ActionSpec> onChange,
        /** Re-apply this setting's actions when the player joins, so server-side state persists. */
        boolean applyOnJoin,
        float sliderMin,
        float sliderMax,
        float sliderStep,
        /** Slider caption, e.g. {@code "Volume: %s"}; {@code %s} is replaced by the value. */
        String sliderFormat,
        int textMaxLength,
        String textPlaceholder,
        int textMultilineLines,
        int textMultilineHeight
) {
    public SettingDefinition {
        options = List.copyOf(options);
        onChange = List.copyOf(onChange);
    }

    /** @return the option with this id, or {@code null} when it is not one of the configured options. */
    public SettingOption option(String id) {
        if (id == null) {
            return null;
        }
        for (SettingOption option : options) {
            if (option.id().equals(id)) {
                return option;
            }
        }
        return null;
    }

    /** @return the index of the option with this id, or {@code -1}. */
    public int indexOf(String id) {
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    /** @return {@code true} when this setting stores a value (as opposed to only running actions). */
    public boolean storesValue() {
        return type != SettingType.ACTION && type != SettingType.SUBMENU;
    }
}
