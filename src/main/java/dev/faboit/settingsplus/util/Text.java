package dev.faboit.settingsplus.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MiniMessage rendering helper.
 *
 * <p>Two placeholder flavours are supported so that operator-authored text stays expressive while
 * player-authored text can never inject formatting:</p>
 * <ul>
 *   <li><b>parsed</b> placeholders come from the config (labels, option names) and may contain tags;</li>
 *   <li><b>raw</b> placeholders come from players (text inputs, names) and are inserted literally.</li>
 * </ul>
 *
 * <p>Both {@code %name%} and MiniMessage's native {@code <name>} syntax resolve to the same value:
 * {@code %name%} is rewritten to {@code <name>} before parsing.</p>
 */
public final class Text {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    /** Matches {@code %placeholder_name%} - letters, digits, underscore and dash only. */
    private static final Pattern PERCENT = Pattern.compile("%([a-zA-Z0-9_-]+)%");

    private Text() {
    }

    /** Parses MiniMessage with no placeholders. */
    public static Component mm(@Nullable String input) {
        return mm(input, Map.of(), Map.of());
    }

    /** Parses MiniMessage, resolving {@code parsed} placeholders as MiniMessage snippets. */
    public static Component mm(@Nullable String input, Map<String, String> parsed) {
        return mm(input, parsed, Map.of());
    }

    /**
     * Parses MiniMessage.
     *
     * @param input  the template, or {@code null} for an empty component
     * @param parsed placeholders whose values are themselves parsed as MiniMessage
     * @param raw    placeholders whose values are inserted verbatim, never parsed
     */
    public static Component mm(@Nullable String input, Map<String, String> parsed, Map<String, String> raw) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        String template = rewritePercentPlaceholders(input, parsed, raw);
        List<TagResolver> resolvers = new ArrayList<>(parsed.size() + raw.size());
        for (Map.Entry<String, String> e : parsed.entrySet()) {
            resolvers.add(Placeholder.parsed(e.getKey(), e.getValue() == null ? "" : e.getValue()));
        }
        for (Map.Entry<String, String> e : raw.entrySet()) {
            resolvers.add(Placeholder.unparsed(e.getKey(), e.getValue() == null ? "" : e.getValue()));
        }
        try {
            return MINI.deserialize(template, TagResolver.resolver(resolvers));
        } catch (RuntimeException ex) {
            // A malformed template must never take a menu down; show the raw text instead.
            return Component.text(input);
        }
    }

    /**
     * Rewrites {@code %name%} to {@code <name>} for every known placeholder. Unknown {@code %...%}
     * sequences are left untouched so other plugins' placeholders survive round-tripping.
     */
    private static String rewritePercentPlaceholders(String input, Map<String, String> parsed, Map<String, String> raw) {
        if (input.indexOf('%') < 0) {
            return input;
        }
        Matcher matcher = PERCENT.matcher(input);
        StringBuilder out = new StringBuilder(input.length());
        while (matcher.find()) {
            String name = matcher.group(1);
            String replacement = (parsed.containsKey(name) || raw.containsKey(name))
                    ? "<" + name + ">"
                    : matcher.group();
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /**
     * Applies {@code %name%} substitution to a plain (non-MiniMessage) string, such as a command
     * line. Values are inserted verbatim - commands are not MiniMessage.
     */
    public static String fill(@Nullable String input, Map<String, String> values) {
        if (input == null || input.isEmpty() || input.indexOf('%') < 0) {
            return input == null ? "" : input;
        }
        Matcher matcher = PERCENT.matcher(input);
        StringBuilder out = new StringBuilder(input.length());
        while (matcher.find()) {
            String value = values.get(matcher.group(1));
            matcher.appendReplacement(out, Matcher.quoteReplacement(value != null ? value : matcher.group()));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** Strips all formatting, for logs and console output. */
    public static String plain(Component component) {
        return PLAIN.serialize(component);
    }
}
