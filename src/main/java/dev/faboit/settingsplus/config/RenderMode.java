package dev.faboit.settingsplus.config;

/** How a setting is drawn inside its category dialog. */
public enum RenderMode {
    /**
     * A clickable button showing {@code label: value}. Clicking advances to the next option and
     * applies it immediately. This is the only mode that supports a per-option hover tooltip.
     */
    BUTTON,
    /**
     * A native dialog input widget (checkbox, dropdown, slider or text field). Values are committed
     * when any button in the dialog is pressed. Native widgets have no per-option tooltip.
     */
    INPUT
}
