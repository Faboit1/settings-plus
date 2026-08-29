package dev.faboit.settingsplus.config;

/**
 * What the client does after a dialog button is pressed. Mirrors Paper's
 * {@code DialogBase.DialogAfterAction} so the config model stays independent of the server API.
 */
public enum AfterAction {
    /** Close the dialog. The server may then send a new one, which flashes the world in between. */
    CLOSE,
    /** Leave the dialog open, unchanged. */
    NONE,
    /** Freeze on a waiting screen until the server sends the next dialog - the smoothest re-render. */
    WAIT_FOR_RESPONSE
}
