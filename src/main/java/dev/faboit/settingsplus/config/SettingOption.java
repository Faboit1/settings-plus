package dev.faboit.settingsplus.config;

import java.util.List;

/**
 * One selectable value of a {@link SettingType#TOGGLE} or {@link SettingType#CYCLE} setting.
 *
 * @param id       the stored value, e.g. {@code everyone}
 * @param label    MiniMessage shown in the button, e.g. {@code <green>ON}
 * @param tooltip  MiniMessage hover text shown while this option is selected, or {@code null}
 * @param permission permission required to select this option, or {@code null} for everyone
 * @param actions  actions run when the player switches to this option
 */
public record SettingOption(
        String id,
        String label,
        String tooltip,
        String permission,
        List<ActionSpec> actions
) {
    public SettingOption {
        actions = List.copyOf(actions);
    }
}
