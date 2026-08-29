package dev.faboit.settingsplus.dialog;

import dev.faboit.settingsplus.SettingsPlus;
import dev.faboit.settingsplus.config.AfterAction;
import dev.faboit.settingsplus.config.Category;
import dev.faboit.settingsplus.config.MenuConfig;
import dev.faboit.settingsplus.config.RenderMode;
import dev.faboit.settingsplus.config.SettingDefinition;
import dev.faboit.settingsplus.config.SettingOption;
import dev.faboit.settingsplus.util.Placeholders;
import dev.faboit.settingsplus.util.Sched;
import dev.faboit.settingsplus.util.Text;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Builds and shows the dialogs, and routes every button press back into the settings model.
 *
 * <p>Dialogs are immutable once sent, so a settings menu that reacts to clicks has to be rebuilt
 * and re-sent after every change. That is why the default {@code after-action} is
 * {@code WAIT_FOR_RESPONSE}: the client parks on a waiting screen instead of flashing back to the
 * world between the click and the replacement dialog.</p>
 *
 * <p>Because the client never sends a dialog back, that also makes the response handler a promise
 * the server must keep - every path through {@link #handleClick} ends in either a new dialog or an
 * explicit close, including the failure paths.</p>
 */
public final class DialogManager {

    /** Sentinel stored in the navigation map for "the player is on the root menu". */
    private static final String ROOT = "";

    private final SettingsPlus plugin;
    private final Map<UUID, String> navigation = new ConcurrentHashMap<>();

    public DialogManager(SettingsPlus plugin) {
        this.plugin = plugin;
    }

    /** Forgets a player's page, called when they disconnect. */
    public void forget(UUID uuid) {
        navigation.remove(uuid);
    }

    /** @return the category the player is currently looking at, or {@code null} for the root menu. */
    public @Nullable Category currentCategory(Player player) {
        String id = navigation.get(player.getUniqueId());
        if (id == null || id.equals(ROOT)) {
            return null;
        }
        return plugin.settings().menu().category(id);
    }

    /**
     * Shows the root menu, or a category when {@code categoryId} is set.
     *
     * @param categoryId a category id, {@code "root"}, or {@code null} for the root menu
     */
    public void open(Player player, @Nullable String categoryId) {
        MenuConfig menu = plugin.settings().menu();
        if (menu == null) {
            return;
        }
        if (categoryId == null || categoryId.isEmpty() || categoryId.equalsIgnoreCase("root")) {
            navigation.put(player.getUniqueId(), ROOT);
            show(player, buildRoot(player, menu));
            return;
        }
        Category category = menu.category(categoryId);
        if (category == null) {
            plugin.messages().send(player, "unknown-category", Map.of("category", categoryId));
            player.closeDialog();
            return;
        }
        if (!canSee(player, category)) {
            plugin.messages().send(player, "no-permission");
            player.closeDialog();
            return;
        }
        navigation.put(player.getUniqueId(), category.id());
        show(player, buildCategory(player, menu, category));
    }

    /** Re-sends whatever page the player is on, so its labels pick up new values. */
    public void reopen(Player player) {
        String id = navigation.get(player.getUniqueId());
        open(player, id == null || id.equals(ROOT) ? null : id);
    }

    private void show(Player player, Dialog dialog) {
        Sched.entity(plugin, player, () -> player.showDialog(dialog), null);
    }

    // ---------------------------------------------------------------- building

    private Dialog buildRoot(Player player, MenuConfig menu) {
        Placeholders placeholders = plugin.actions().placeholders(player, null, null, null, null);

        List<ActionButton> buttons = new ArrayList<>();
        for (Category category : menu.categories()) {
            if (!canSee(player, category)) {
                continue;
            }
            buttons.add(ActionButton.builder(Text.mm(category.label(), placeholders.parsed(), placeholders.raw()))
                    .tooltip(tooltipOrNull(category.tooltip(), placeholders))
                    .width(category.width())
                    .action(click(dialogClick -> open(player, category.id())))
                    .build());
        }

        ActionButton exit = ActionButton.builder(Text.mm(menu.exit().label(), placeholders.parsed(), placeholders.raw()))
                .tooltip(tooltipOrNull(menu.exit().tooltip(), placeholders))
                .width(menu.exit().width())
                .action(click(dialogClick -> player.closeDialog()))
                .build();

        DialogBase base = DialogBase.builder(Text.mm(menu.title(), placeholders.parsed(), placeholders.raw()))
                .externalTitle(menu.externalTitle() == null
                        ? null : Text.mm(menu.externalTitle(), placeholders.parsed(), placeholders.raw()))
                .canCloseWithEscape(menu.canCloseWithEscape())
                .afterAction(afterAction(menu.afterAction()))
                .body(bodyLines(menu.body(), placeholders, null))
                .inputs(List.of())
                .build();

        return Dialog.create(factory -> factory.empty()
                .base(base)
                .type(DialogType.multiAction(buttons).columns(menu.columns()).exitAction(exit).build()));
    }

    private Dialog buildCategory(Player player, MenuConfig menu, Category category) {
        Placeholders placeholders = plugin.actions().placeholders(player, category, null, null, null);

        List<DialogInput> inputs = new ArrayList<>();
        List<ActionButton> buttons = new ArrayList<>();

        List<SettingDefinition> all = category.settings();
        for (int index = 0; index < all.size(); index++) {
            SettingDefinition setting = all.get(index);
            if (setting.permission() != null && !player.hasPermission(setting.permission())) {
                if (setting.lockedLabel() == null) {
                    continue;
                }
                buttons.add(lockedButton(setting, placeholders));
                continue;
            }
            if (setting.render() == RenderMode.INPUT && setting.storesValue()) {
                DialogInput input = buildInput(player, category, setting,
                        inputKey(setting, index), placeholders);
                if (input != null) {
                    inputs.add(input);
                    continue;
                }
                // Fall through to a button when the setting cannot be expressed as a native widget.
            }
            buttons.add(buildButton(player, category, setting));
        }

        if (menu.reset() != null) {
            buttons.add(ActionButton.builder(Text.mm(menu.reset().label(), placeholders.parsed(), placeholders.raw()))
                    .tooltip(tooltipOrNull(menu.reset().tooltip(), placeholders))
                    .width(menu.reset().width())
                    .action(click(view -> {
                        plugin.settings().resetCategory(player, category);
                        plugin.messages().send(player, "category-reset",
                                Map.of("category", category.id()));
                        open(player, category.id());
                    }))
                    .build());
        }

        ActionButton back = ActionButton.builder(Text.mm(menu.back().label(), placeholders.parsed(), placeholders.raw()))
                .tooltip(tooltipOrNull(menu.back().tooltip(), placeholders))
                .width(menu.back().width())
                .action(click(view -> {
                    commitInputs(player, category, view);
                    open(player, null);
                }))
                .build();

        DialogBase base = DialogBase.builder(Text.mm(category.title(), placeholders.parsed(), placeholders.raw()))
                .externalTitle(category.externalTitle() == null
                        ? null : Text.mm(category.externalTitle(), placeholders.parsed(), placeholders.raw()))
                .canCloseWithEscape(category.canCloseWithEscape())
                .afterAction(afterAction(menu.afterAction()))
                .body(bodyLines(category.body(), placeholders, category.icon()))
                .inputs(inputs)
                .build();

        return Dialog.create(factory -> factory.empty()
                .base(base)
                .type(DialogType.multiAction(buttons).columns(category.columns()).exitAction(back).build()));
    }

    /** Builds the clickable form of a setting: label, current value, hover and click behaviour. */
    private ActionButton buildButton(Player player, Category category, SettingDefinition setting) {
        switch (setting.type()) {
            case ACTION -> {
                Placeholders placeholders = plugin.actions()
                        .placeholders(player, category, setting, null, null);
                return ActionButton.builder(Text.mm(setting.label(), placeholders.parsed(), placeholders.raw()))
                        .tooltip(tooltipOrNull(setting.tooltip(), placeholders))
                        .width(setting.width())
                        .action(click(view -> {
                            commitInputs(player, category, view);
                            boolean navigated = plugin.actions().run(player, setting.onChange(), placeholders);
                            // The configured actions may have opened another page or closed the
                            // dialog themselves; only send a page of our own when they did not.
                            if (!navigated && player.isOnline()) {
                                reopen(player);
                            }
                        }))
                        .build();
            }
            case SUBMENU -> {
                Placeholders placeholders = plugin.actions()
                        .placeholders(player, category, setting, null, null);
                String target = setting.defaultValue();
                return ActionButton.builder(Text.mm(setting.label(), placeholders.parsed(), placeholders.raw()))
                        .tooltip(tooltipOrNull(setting.tooltip(), placeholders))
                        .width(setting.width())
                        .action(click(view -> {
                            commitInputs(player, category, view);
                            open(player, target);
                        }))
                        .build();
            }
            default -> {
                SettingOption option = plugin.settings().selectedOption(player, category, setting);
                String value = plugin.settings().value(player, category, setting);
                Placeholders placeholders = plugin.actions()
                        .placeholders(player, category, setting, value, option);

                Placeholders buttonPlaceholders = placeholders
                        .withParsed("label", setting.label())
                        .withParsed("option", option == null ? value : option.label());
                Component label = Text.mm(setting.buttonFormat(),
                        buttonPlaceholders.parsed(), buttonPlaceholders.raw());

                String tooltip = option != null && option.tooltip() != null
                        ? option.tooltip() : setting.tooltip();

                return ActionButton.builder(label)
                        .tooltip(tooltipOrNull(tooltip, placeholders))
                        .width(setting.width())
                        .action(click(view -> {
                            commitInputs(player, category, view);
                            plugin.settings().cycle(player, category, setting, 1);
                            open(player, category.id());
                        }))
                        .build();
            }
        }
    }

    private ActionButton lockedButton(SettingDefinition setting, Placeholders placeholders) {
        Placeholders withLabel = placeholders.withParsed("label", setting.label());
        return ActionButton.builder(Text.mm(setting.lockedLabel(), withLabel.parsed(), withLabel.raw()))
                .tooltip(tooltipOrNull(setting.lockedTooltip(), placeholders))
                .width(setting.width())
                .action(click(view -> {
                }))
                .build();
    }

    /** @return a native widget for the setting, or {@code null} when it needs to stay a button. */
    private @Nullable DialogInput buildInput(Player player, Category category, SettingDefinition setting,
                                             String inputKey, Placeholders placeholders) {
        String value = plugin.settings().value(player, category, setting);
        Component label = Text.mm(setting.label(), placeholders.parsed(), placeholders.raw());

        switch (setting.type()) {
            case TOGGLE -> {
                List<SettingOption> options = setting.options();
                if (options.size() != 2) {
                    return singleOption(setting, inputKey, value, label);
                }
                // The first configured option is the unchecked state, the second the checked one.
                return DialogInput.bool(inputKey, label)
                        .initial(options.get(1).id().equals(value))
                        .onTrue(options.get(1).id())
                        .onFalse(options.get(0).id())
                        .build();
            }
            case CYCLE -> {
                return singleOption(setting, inputKey, value, label);
            }
            case SLIDER -> {
                float initial = parseFloat(value, setting.sliderMin());
                float clamped = Math.min(setting.sliderMax(), Math.max(setting.sliderMin(), initial));
                return DialogInput.numberRange(inputKey, label, setting.sliderMin(), setting.sliderMax())
                        .width(setting.width())
                        .labelFormat(setting.sliderFormat())
                        .step(setting.sliderStep())
                        .initial(clamped)
                        .build();
            }
            case TEXT -> {
                TextDialogInput.Builder builder = DialogInput.text(inputKey, label)
                        .width(setting.width())
                        .maxLength(setting.textMaxLength())
                        .initial(value == null ? setting.textPlaceholder() : value);
                if (setting.textMultilineLines() > 0 || setting.textMultilineHeight() > 0) {
                    builder.multiline(TextDialogInput.MultilineOptions.create(
                            setting.textMultilineLines() > 0 ? setting.textMultilineLines() : null,
                            setting.textMultilineHeight() > 0 ? setting.textMultilineHeight() : null));
                }
                return builder.build();
            }
            default -> {
                return null;
            }
        }
    }

    private @Nullable DialogInput singleOption(SettingDefinition setting, String inputKey,
                                               String value, Component label) {
        List<SingleOptionDialogInput.OptionEntry> entries = new ArrayList<>();
        for (SettingOption option : setting.options()) {
            entries.add(SingleOptionDialogInput.OptionEntry.create(
                    option.id(), Text.mm(option.label()), option.id().equals(value)));
        }
        if (entries.isEmpty()) {
            return null;
        }
        // The client requires exactly one initial entry; if the stored value matched nothing, pin
        // the first one so the widget is not sent in an invalid state.
        if (entries.stream().noneMatch(SingleOptionDialogInput.OptionEntry::initial)) {
            SingleOptionDialogInput.OptionEntry first = entries.get(0);
            entries.set(0, SingleOptionDialogInput.OptionEntry.create(first.id(), first.display(), true));
        }
        return DialogInput.singleOption(inputKey, label, entries)
                .width(setting.width())
                .labelVisible(true)
                .build();
    }

    // ---------------------------------------------------------------- clicks

    /**
     * Wraps a click handler so that it always runs on the player's region thread and can never
     * leave the client stranded on the {@code WAIT_FOR_RESPONSE} screen.
     */
    private DialogAction click(ClickHandler handler) {
        return DialogAction.customClick((view, audience) -> {
            if (!(audience instanceof Player player)) {
                return;
            }
            Sched.entity(plugin, player, () -> handleClick(player, view, handler), null);
        }, ClickCallback.Options.builder()
                .uses(ClickCallback.UNLIMITED_USES)
                .lifetime(Duration.ofMinutes(plugin.callbackLifetimeMinutes()))
                .build());
    }

    private void handleClick(Player player, DialogResponseView view, ClickHandler handler) {
        try {
            handler.accept(view);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "Settings dialog click failed for " + player.getName(), ex);
            // WAIT_FOR_RESPONSE means the client is frozen until we answer. Always answer.
            player.closeDialog();
            plugin.messages().send(player, "dialog-error", Map.of("error", String.valueOf(ex.getMessage())));
        }
    }

    /**
     * Reads the values the player typed or dragged into the native widgets of the page they are
     * leaving, and stores any that changed.
     *
     * <p>Every button press carries the whole dialog's input state, so this runs on all of them -
     * cycling one button also commits an edit made to a text field beside it.</p>
     */
    private void commitInputs(Player player, Category category, DialogResponseView view) {
        if (view == null) {
            return;
        }
        List<SettingDefinition> all = category.settings();
        for (int index = 0; index < all.size(); index++) {
            SettingDefinition setting = all.get(index);
            if (setting.render() != RenderMode.INPUT || !setting.storesValue()) {
                continue;
            }
            if (setting.permission() != null && !player.hasPermission(setting.permission())) {
                continue;
            }
            try {
                String submitted = readInput(setting, inputKey(setting, index), view);
                if (submitted == null) {
                    continue;
                }
                if (!submitted.equals(plugin.settings().value(player, category, setting))) {
                    plugin.settings().change(player, category, setting, submitted, true);
                }
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING,
                        "Could not read dialog input '" + setting.key() + "'", ex);
            }
        }
    }

    private @Nullable String readInput(SettingDefinition setting, String inputKey, DialogResponseView view) {
        return switch (setting.type()) {
            case TOGGLE -> {
                if (setting.options().size() != 2) {
                    yield view.getText(inputKey);
                }
                Boolean checked = view.getBoolean(inputKey);
                yield checked == null ? null
                        : setting.options().get(checked ? 1 : 0).id();
            }
            case CYCLE, TEXT -> view.getText(inputKey);
            case SLIDER -> {
                Float number = view.getFloat(inputKey);
                yield number == null ? null : formatNumber(number);
            }
            default -> null;
        };
    }

    /**
     * Derives the dialog input name for a setting.
     *
     * <p>Minecraft validates input names as macro variable names - letters, digits and underscore
     * only - so a perfectly ordinary config key like {@code chat-filter} would be rejected and take
     * the whole page down with it. Everything else is replaced with an underscore, and the
     * setting's index within its category is appended so two keys that sanitise to the same string
     * stay distinct.</p>
     *
     * <p>The result is derived purely from the config, so the key used to read a response always
     * matches the one that was sent.</p>
     */
    public static String inputKey(SettingDefinition setting, int index) {
        String key = setting.key();
        StringBuilder sanitised = new StringBuilder(key.length() + 4);
        for (int i = 0; i < key.length(); i++) {
            char character = key.charAt(i);
            sanitised.append(Character.isLetterOrDigit(character) || character == '_' ? character : '_');
        }
        return sanitised.append('_').append(index).toString();
    }

    /** Renders a slider value without a trailing {@code .0} when it is a whole number. */
    private static String formatNumber(float value) {
        if (value == Math.rint(value) && !Float.isInfinite(value)) {
            return Long.toString((long) value);
        }
        return Float.toString(value);
    }

    private static float parseFloat(String raw, float fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return Float.parseFloat(raw);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    // ---------------------------------------------------------------- helpers

    private boolean canSee(Player player, Category category) {
        return category.permission() == null || player.hasPermission(category.permission());
    }

    private static @Nullable Component tooltipOrNull(@Nullable String raw, Placeholders placeholders) {
        return raw == null || raw.isEmpty() ? null : Text.mm(raw, placeholders.parsed(), placeholders.raw());
    }

    private List<DialogBody> bodyLines(List<String> lines, Placeholders placeholders,
                                       @Nullable String icon) {
        List<DialogBody> bodies = new ArrayList<>();
        if (icon != null && !icon.isEmpty()) {
            Material material = Material.matchMaterial(icon);
            if (material != null && material.isItem()) {
                bodies.add(DialogBody.item(new ItemStack(material))
                        .showDecorations(false)
                        .showTooltip(false)
                        .build());
            } else {
                plugin.getLogger().warning("Unknown icon material in settings.yml: '" + icon + "'");
            }
        }
        for (String line : lines) {
            bodies.add(DialogBody.plainMessage(Text.mm(line, placeholders.parsed(), placeholders.raw())));
        }
        return bodies;
    }

    /**
     * Builds every dialog for a player and throws if any of them is malformed. Used by
     * {@code /settingsplus verify} so an operator finds out at the console rather than by watching
     * a player's screen freeze.
     */
    public int verify(Player player) {
        MenuConfig menu = plugin.settings().menu();
        buildRoot(player, menu);
        int built = 1;
        for (Category category : menu.categories()) {
            buildCategory(player, menu, category);
            built++;
        }
        return built;
    }

    private static DialogBase.DialogAfterAction afterAction(AfterAction afterAction) {
        return switch (afterAction) {
            case CLOSE -> DialogBase.DialogAfterAction.CLOSE;
            case NONE -> DialogBase.DialogAfterAction.NONE;
            case WAIT_FOR_RESPONSE -> DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE;
        };
    }

    /** A dialog button handler that receives the submitted input state. */
    @FunctionalInterface
    private interface ClickHandler {
        void accept(DialogResponseView view) throws Exception;
    }
}
