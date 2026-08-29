package dev.faboit.settingsplus.util;

import java.util.HashMap;
import java.util.Map;

/**
 * The placeholder values available to one rendering or action, split by how they may be inserted.
 *
 * <p>The split matters. {@code %option%} is an operator-authored MiniMessage snippet like
 * {@code <green><bold>ON</bold>}, which has to be parsed or the player sees the tags as text.
 * {@code %player%} and a player's own typed {@code %value%} must never be parsed, or someone whose
 * away message is {@code <red>oops} would recolour the menu. Holding both in one map made the two
 * meanings collide, so they are kept apart here and a name never appears in both.</p>
 *
 * @param parsed names whose values are themselves MiniMessage
 * @param raw    names whose values are inserted literally
 */
public record Placeholders(Map<String, String> parsed, Map<String, String> raw) {

    public Placeholders {
        parsed = Map.copyOf(parsed);
        raw = Map.copyOf(raw);
    }

    /** @return a copy with one extra parsed placeholder, for values known only at render time. */
    public Placeholders withParsed(String name, String value) {
        Map<String, String> extended = new HashMap<>(parsed);
        extended.put(name, value == null ? "" : value);
        return new Placeholders(extended, raw);
    }

    /**
     * Flattens both maps for use in a command line, which is plain text rather than MiniMessage.
     *
     * <p>Formatted names collapse to their plain-text form, so {@code %option%} in a command
     * becomes {@code ON} rather than {@code <green><bold>ON</bold>}.</p>
     */
    public Map<String, String> forCommands() {
        Map<String, String> flat = new HashMap<>(raw);
        for (Map.Entry<String, String> entry : parsed.entrySet()) {
            flat.put(entry.getKey(), Text.plain(Text.mm(entry.getValue())));
        }
        return flat;
    }
}
