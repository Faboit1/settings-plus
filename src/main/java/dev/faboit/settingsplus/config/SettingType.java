package dev.faboit.settingsplus.config;

/** The kind of value a setting holds, which decides how it is rendered and stored. */
public enum SettingType {
    /** Two options, stored as the chosen option's id. Rendered as a cycling button or a checkbox. */
    TOGGLE,
    /** Any number of options, stored as the chosen option's id. */
    CYCLE,
    /** A number between a minimum and a maximum, stored as the number. Always a native input. */
    SLIDER,
    /** Free text typed by the player, stored verbatim. Always a native input. */
    TEXT,
    /** Stores nothing - a button that just runs its actions. */
    ACTION,
    /** Stores nothing - a button that opens another category. */
    SUBMENU
}
