package dev.faboit.settingsplus.config;

/**
 * What the client does after a dialog button is pressed. Mirrors Paper's
 * {@code DialogBase.DialogAfterAction} so the config model stays independent of the server API.
 */
public enum AfterAction {
    /** Close the dialog. The server may then send a new one, which flashes the world in between. */
    CLOSE,
    /**
     * Leave the dialog on screen. The replacement page swaps in when it arrives, so the player
     * never sees an intermediate screen - the right choice for a menu that redraws itself.
     */
    NONE,
    /**
     * Freeze on a "waiting for server" screen until the next dialog arrives. Honest about the round
     * trip, but on a fast redraw it reads as a flicker.
     */
    WAIT_FOR_RESPONSE
}
