package dev.faboit.settingsplus.config;

import java.util.Locale;

/**
 * One entry of an {@code actions:} list, written in config as {@code "type: argument"}.
 *
 * <p>Example: {@code "player: showchatfrom %value%"} dispatches {@code /showchatfrom everyone} as
 * the player. A bare string with no recognised prefix is treated as a player command, so
 * {@code "spawn"} and {@code "player: spawn"} mean the same thing.</p>
 */
public record ActionSpec(Kind kind, String argument) {

    public enum Kind {
        /** Dispatch the argument as a command run by the player, on the player's region thread. */
        PLAYER_COMMAND,
        /** Dispatch the argument as a console command, on the global region thread. */
        CONSOLE_COMMAND,
        /** Send the argument to the player as a MiniMessage chat message. */
        MESSAGE,
        /** Send the argument to the player as a MiniMessage action bar. */
        ACTIONBAR,
        /** Send the argument to every online player as a MiniMessage chat message. */
        BROADCAST,
        /** Play a sound: {@code "sound: minecraft:ui.button.click 1.0 1.2"} (volume and pitch optional). */
        SOUND,
        /** Open a category by id, or the root menu when the argument is empty or {@code root}. */
        OPEN,
        /** Close the dialog. */
        CLOSE,
        /** Re-send the current dialog so its labels refresh. */
        REFRESH,
        /** Reset a category to defaults by id, or the current category when the argument is empty. */
        RESET
    }

    /** Parses one config line into an action, or {@code null} if the line is blank. */
    public static ActionSpec parse(String raw) {
        if (raw == null) {
            return null;
        }
        String line = raw.strip();
        if (line.isEmpty()) {
            return null;
        }
        int colon = line.indexOf(':');
        if (colon > 0) {
            String prefix = line.substring(0, colon).strip().toUpperCase(Locale.ROOT).replace('-', '_');
            String argument = line.substring(colon + 1).strip();
            Kind kind = switch (prefix) {
                case "PLAYER", "PLAYER_COMMAND", "RUN" -> Kind.PLAYER_COMMAND;
                case "CONSOLE", "CONSOLE_COMMAND", "SERVER" -> Kind.CONSOLE_COMMAND;
                case "MESSAGE", "MSG", "TELL" -> Kind.MESSAGE;
                case "ACTIONBAR", "ACTION_BAR" -> Kind.ACTIONBAR;
                case "BROADCAST" -> Kind.BROADCAST;
                case "SOUND" -> Kind.SOUND;
                case "OPEN", "MENU", "CATEGORY" -> Kind.OPEN;
                case "CLOSE" -> Kind.CLOSE;
                case "REFRESH" -> Kind.REFRESH;
                case "RESET" -> Kind.RESET;
                default -> null;
            };
            if (kind != null) {
                return new ActionSpec(kind, argument);
            }
        }
        // No recognised prefix: the whole line is a player command. Tolerate a leading slash.
        return new ActionSpec(Kind.PLAYER_COMMAND, line.startsWith("/") ? line.substring(1) : line);
    }
}
