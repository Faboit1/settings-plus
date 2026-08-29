package dev.faboit.settingsplus.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses {@code settings.yml} into a {@link MenuConfig}.
 *
 * <p>Anything structurally broken raises a {@link ConfigException} naming the exact config path, so
 * a typo produces "categories.chat.settings.show-chat: unknown type 'CYLE'" rather than a stack
 * trace. Merely questionable values (a width out of range, a default that is not one of the
 * options) are clamped or corrected and reported through {@link #warnings()}.</p>
 */
public final class ConfigLoader {

    private static final int MIN_WIDTH = 1;
    private static final int MAX_WIDTH = 1024;
    private static final int MAX_COLUMNS = 8;

    private final List<String> warnings = new ArrayList<>();

    /** @return non-fatal problems found during the last {@link #load} call. */
    public List<String> warnings() {
        return List.copyOf(warnings);
    }

    /** Parses a whole {@code settings.yml}. */
    public MenuConfig load(FileConfiguration config) {
        warnings.clear();

        ConfigurationSection menu = config.getConfigurationSection("menu");
        if (menu == null) {
            throw new ConfigException("settings.yml is missing the top-level 'menu' section");
        }
        ConfigurationSection categoriesSection = config.getConfigurationSection("categories");
        if (categoriesSection == null) {
            throw new ConfigException("settings.yml is missing the top-level 'categories' section");
        }

        List<Category> categories = new ArrayList<>();
        for (String id : categoriesSection.getKeys(false)) {
            ConfigurationSection section = categoriesSection.getConfigurationSection(id);
            if (section == null) {
                warnings.add("categories." + id + ": not a section, skipped");
                continue;
            }
            categories.add(loadCategory(id, section));
        }
        if (categories.isEmpty()) {
            throw new ConfigException("settings.yml defines no usable categories");
        }

        ConfigurationSection buttons = menu.getConfigurationSection("buttons");
        MenuConfig.Button exit = button(buttons, "exit", "<red>Close", "<gray>Close the settings menu", 200);
        MenuConfig.Button back = button(buttons, "back", "<gray>« Back", "<gray>Return to the main menu", 200);
        MenuConfig.Button reset = null;
        if (buttons != null && buttons.isConfigurationSection("reset")
                && buttons.getBoolean("reset.enabled", true)) {
            reset = button(buttons, "reset", "<gold>Reset", "<gray>Restore this page's defaults", 200);
        }

        return new MenuConfig(
                menu.getString("title", "<bold>Settings</bold>"),
                menu.getString("external-title"),
                menu.getStringList("body"),
                clamp(menu.getInt("columns", 2), 1, MAX_COLUMNS, "menu.columns"),
                menu.getBoolean("can-close-with-escape", true),
                afterAction(menu.getString("after-action"), "menu.after-action"),
                exit,
                back,
                reset,
                categories
        );
    }

    private MenuConfig.Button button(ConfigurationSection buttons, String key,
                                     String defaultLabel, String defaultTooltip, int defaultWidth) {
        ConfigurationSection section = buttons == null ? null : buttons.getConfigurationSection(key);
        if (section == null) {
            return new MenuConfig.Button(defaultLabel, defaultTooltip, defaultWidth);
        }
        return new MenuConfig.Button(
                section.getString("label", defaultLabel),
                section.getString("tooltip", defaultTooltip),
                clamp(section.getInt("width", defaultWidth), MIN_WIDTH, MAX_WIDTH, "menu.buttons." + key + ".width")
        );
    }

    private Category loadCategory(String id, ConfigurationSection section) {
        String path = "categories." + id;
        ConfigurationSection settingsSection = section.getConfigurationSection("settings");
        List<SettingDefinition> settings = new ArrayList<>();
        if (settingsSection == null) {
            warnings.add(path + ": has no 'settings' section, the page will be empty");
        } else {
            for (String key : settingsSection.getKeys(false)) {
                ConfigurationSection settingConfig = settingsSection.getConfigurationSection(key);
                if (settingConfig == null) {
                    warnings.add(path + ".settings." + key + ": not a section, skipped");
                    continue;
                }
                settings.add(loadSetting(key, settingConfig, path + ".settings." + key));
            }
        }

        String title = section.getString("title", "<bold>" + id + "</bold>");
        return new Category(
                id,
                title,
                section.getString("external-title"),
                section.getString("label", title),
                section.getString("tooltip"),
                section.getStringList("body"),
                section.getString("icon"),
                section.getString("permission"),
                clamp(section.getInt("columns", 1), 1, MAX_COLUMNS, path + ".columns"),
                clamp(section.getInt("width", 200), MIN_WIDTH, MAX_WIDTH, path + ".width"),
                section.getBoolean("can-close-with-escape", true),
                settings
        );
    }

    private SettingDefinition loadSetting(String key, ConfigurationSection section, String path) {
        SettingType type = enumValue(SettingType.class, section.getString("type"), path + ".type");
        RenderMode render = section.isString("render")
                ? enumValue(RenderMode.class, section.getString("render"), path + ".render")
                : defaultRender(type);

        if (render == RenderMode.BUTTON && (type == SettingType.SLIDER || type == SettingType.TEXT)) {
            warnings.add(path + ": " + type + " settings have no button form, using render: INPUT");
            render = RenderMode.INPUT;
        }

        List<SettingOption> options = loadOptions(section, path, type);
        if ((type == SettingType.TOGGLE || type == SettingType.CYCLE) && options.isEmpty()) {
            throw new ConfigException(path + ": a " + type + " setting needs at least one entry under 'options'");
        }
        if (type == SettingType.TOGGLE && options.size() != 2) {
            warnings.add(path + ": TOGGLE expects exactly 2 options but found " + options.size()
                    + "; it will behave like a CYCLE");
        }

        String defaultValue = section.getString("default");
        if (type == SettingType.TOGGLE || type == SettingType.CYCLE) {
            String configured = defaultValue;
            if (configured == null) {
                defaultValue = options.get(0).id();
            } else if (options.stream().noneMatch(option -> option.id().equals(configured))) {
                warnings.add(path + ".default: '" + configured + "' is not one of the options, using '"
                        + options.get(0).id() + "'");
                defaultValue = options.get(0).id();
            }
        }

        float min = (float) section.getDouble("min", 0.0D);
        float max = (float) section.getDouble("max", 100.0D);
        if (type == SettingType.SLIDER && max <= min) {
            throw new ConfigException(path + ": slider 'max' (" + max + ") must be greater than 'min' (" + min + ")");
        }
        float step = (float) section.getDouble("step", 1.0D);
        if (step <= 0.0F) {
            warnings.add(path + ".step: must be greater than 0, using 1");
            step = 1.0F;
        }

        return new SettingDefinition(
                key,
                type,
                render,
                section.getString("label", key),
                section.getString("button-format", "%label%<dark_gray>: </dark_gray>%option%"),
                section.getString("tooltip"),
                clamp(section.getInt("width", 200), MIN_WIDTH, MAX_WIDTH, path + ".width"),
                section.getString("permission"),
                section.getString("locked-label"),
                section.getString("locked-tooltip", "<red>You have not unlocked this setting."),
                defaultValue,
                options,
                actions(section.getStringList("on-change")),
                section.getBoolean("apply-on-join", true),
                min,
                max,
                step,
                section.getString("slider-format", "%s"),
                clamp(section.getInt("max-length", 64), 1, 32767, path + ".max-length"),
                section.getString("placeholder", ""),
                section.getInt("multiline-lines", 0),
                section.getInt("multiline-height", 0)
        );
    }

    private static RenderMode defaultRender(SettingType type) {
        // Sliders and text fields only exist as native widgets; everything else defaults to a
        // button, because only buttons can carry a per-option hover tooltip.
        return switch (type) {
            case SLIDER, TEXT -> RenderMode.INPUT;
            default -> RenderMode.BUTTON;
        };
    }

    /**
     * Reads the {@code options} node, which may be either a list of maps (ordered, ids optional) or
     * a section keyed by option id.
     */
    private List<SettingOption> loadOptions(ConfigurationSection section, String path, SettingType type) {
        if (type != SettingType.TOGGLE && type != SettingType.CYCLE) {
            return List.of();
        }
        List<SettingOption> options = new ArrayList<>();

        if (section.isList("options")) {
            List<?> raw = section.getList("options", List.of());
            for (int i = 0; i < raw.size(); i++) {
                Object element = raw.get(i);
                String optionPath = path + ".options[" + i + "]";
                if (element instanceof String simple) {
                    // Shorthand: "- everyone" becomes an option whose id and label are both that word.
                    options.add(new SettingOption(simple, simple, null, null, List.of()));
                } else if (element instanceof Map<?, ?> map) {
                    options.add(optionFromMap(map, "option-" + i, optionPath));
                } else {
                    throw new ConfigException(optionPath + ": expected a string or a map");
                }
            }
        } else if (section.isConfigurationSection("options")) {
            ConfigurationSection optionsSection = section.getConfigurationSection("options");
            for (String id : optionsSection.getKeys(false)) {
                ConfigurationSection option = optionsSection.getConfigurationSection(id);
                if (option == null) {
                    throw new ConfigException(path + ".options." + id + ": expected a map");
                }
                options.add(optionFromMap(option.getValues(false), id, path + ".options." + id));
            }
        }
        return options;
    }

    private SettingOption optionFromMap(Map<?, ?> map, String fallbackId, String path) {
        Object id = map.get("id");
        String optionId = id == null ? fallbackId : String.valueOf(id);
        Object label = map.get("label");
        Object tooltip = map.get("tooltip");
        Object permission = map.get("permission");

        List<String> rawActions = new ArrayList<>();
        Object actions = map.get("actions");
        if (actions instanceof List<?> list) {
            for (Object action : list) {
                rawActions.add(String.valueOf(action));
            }
        } else if (actions instanceof String single) {
            rawActions.add(single);
        } else if (actions != null) {
            throw new ConfigException(path + ".actions: expected a list of strings");
        }

        return new SettingOption(
                optionId,
                label == null ? optionId : String.valueOf(label),
                tooltip == null ? null : String.valueOf(tooltip),
                permission == null ? null : String.valueOf(permission),
                actions(rawActions)
        );
    }

    private static List<ActionSpec> actions(List<String> raw) {
        List<ActionSpec> parsed = new ArrayList<>(raw.size());
        for (String line : raw) {
            ActionSpec spec = ActionSpec.parse(line);
            if (spec != null) {
                parsed.add(spec);
            }
        }
        return parsed;
    }

    private static AfterAction afterAction(String raw, String path) {
        if (raw == null) {
            return AfterAction.WAIT_FOR_RESPONSE;
        }
        return enumValue(AfterAction.class, raw, path);
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String raw, String path) {
        if (raw == null) {
            throw new ConfigException(path + ": is required, expected one of " + names(type));
        }
        String normalised = raw.strip().toUpperCase(Locale.ROOT).replace('-', '_');
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equals(normalised)) {
                return constant;
            }
        }
        throw new ConfigException(path + ": unknown value '" + raw + "', expected one of " + names(type));
    }

    private static String names(Class<? extends Enum<?>> type) {
        List<String> names = new ArrayList<>();
        for (Enum<?> constant : type.getEnumConstants()) {
            names.add(constant.name());
        }
        return String.join(", ", names);
    }

    private int clamp(int value, int min, int max, String path) {
        if (value < min || value > max) {
            int clamped = Math.min(max, Math.max(min, value));
            warnings.add(path + ": " + value + " is outside " + min + "-" + max + ", using " + clamped);
            return clamped;
        }
        return value;
    }
}
