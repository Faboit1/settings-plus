# SettingsPlus

Fully configurable, dialog-powered player settings menus for **Paper** and **Folia**.

Every category, every setting, every option, every hover tooltip and every command comes from
`settings.yml`. Nothing about the menu is hardcoded — the plugin ships an example config, not a
fixed feature set.

Built on Minecraft's dialog screens (Paper 1.21.8+), so the menu is a real UI rather than a chest
GUI: proper buttons, checkboxes, dropdowns, sliders and text fields, with MiniMessage everywhere.

---

## The idea, in one config block

```yaml
show-chat:
  type: CYCLE
  label: "<white>Show chat"
  button-format: "%label% <dark_gray>»</dark_gray> %option%"
  default: everyone
  options:
    - id: everyone
      label: "<green><bold>ON</bold>"
      tooltip: "<green><bold>Everyone</bold><newline><gray>You see every public message."
      actions:
        - "player: showchatfrom everyone"
    - id: friends
      label: "<yellow><bold>FRIENDS ONLY</bold>"
      tooltip: "<yellow><bold>Friends only</bold><newline><gray>Only your friends reach your chat."
      actions:
        - "player: showchatfrom friends"
    - id: nobody
      label: "<red><bold>OFF</bold>"
      tooltip: "<red><bold>Off</bold><newline><gray>Public chat is hidden entirely."
      actions:
        - "player: showchatfrom nobody"
```

That renders one button reading `Show chat » ON`. Clicking it advances to `FRIENDS ONLY`, runs
`/showchatfrom friends` as the player, saves the choice, and redraws the page. Each state carries
its own hover text. The same three lines of config would work just as well for
`/vanish`, `/toggletips`, `/pvp` or anything else your server already has a command for.

---

## Requirements

| | |
|---|---|
| Server | Paper or Folia **1.21.8+** |
| Java | 21+ |

The dialog API this plugin is built on is byte-for-byte identical from 1.21.8 through the current
Paper builds, so one jar covers all of them. On an older server the plugin logs a clear message and
disables itself rather than throwing.

---

## Commands and permissions

| Command | Permission | What it does |
|---|---|---|
| `/settings [category]` | `settingsplus.use` (default: everyone) | Opens the menu |
| `/settingsplus reload` | `settingsplus.admin.reload` | Re-reads both config files |
| `/settingsplus verify` | `settingsplus.admin.reload` | Builds every page without showing it, reporting any problem as a message |
| `/settingsplus info` | `settingsplus.admin.reload` | Version, scheduler in use, category and setting counts |
| `/settingsplus get <player> <category.setting>` | `settingsplus.admin.edit` | Reads a stored value |
| `/settingsplus set <player> <category.setting> <value>` | `settingsplus.admin.edit` | Writes one, running its actions |
| `/settingsplus reset <player> <category>` | `settingsplus.admin.edit` | Restores a page's defaults |
| `/settingsplus open <player> [category]` | `settingsplus.admin.open` | Opens the menu for someone else |

Aliases: `/options`, `/prefs` for `/settings`; `/spadmin`, `/settingsadmin` for `/settingsplus`.

`reload` only installs the new menu once it has parsed cleanly — a typo leaves the running config
untouched instead of emptying everyone's menu.

---

## Setting types

| Type | Stores | Notes |
|---|---|---|
| `TOGGLE` | the chosen option's id | Two options. A cycling button, or a checkbox with `render: INPUT`. |
| `CYCLE` | the chosen option's id | Any number of options. A cycling button, or a dropdown with `render: INPUT`. |
| `SLIDER` | a number | `min`, `max`, `step`, `slider-format`. Always a native slider. |
| `TEXT` | what the player typed | `max-length`, `placeholder`, optional multiline. Always a native field. |
| `ACTION` | nothing | A button that just runs its `on-change` actions. |
| `SUBMENU` | nothing | A button that opens the category named in `default`. |

### Render modes

`render: BUTTON` draws `Label » VALUE` as a clickable button. Clicking advances to the next option,
applies it immediately, and redraws the page. **This is the only mode with a per-option hover
tooltip**, which is why it is the default for `TOGGLE` and `CYCLE`.

`render: INPUT` draws a native widget instead — checkbox, dropdown, slider or text field. The whole
option list is visible at once, which reads better for long lists, but Minecraft gives native
widgets no per-option hover. `SLIDER` and `TEXT` only exist in this form.

The two mix freely on one page. Values typed or dragged into native widgets are committed when
*any* button on the page is pressed — including a cycling button beside them — so nothing is lost
by interacting with the page in any order.

---

## Actions

Each entry of an `actions:` or `on-change:` list is `kind: argument`:

| Kind | Effect |
|---|---|
| `player: <cmd>` | Runs the command as the player |
| `console: <cmd>` | Runs it from console |
| `message: <mm>` | Chat message to the player |
| `actionbar: <mm>` | Action bar message |
| `broadcast: <mm>` | Chat message to everyone |
| `sound: <key> [volume] [pitch]` | Plays a sound to the player |
| `open: <category\|root>` | Opens another page |
| `close:` | Closes the dialog |
| `refresh:` | Redraws the current page |
| `reset: [category]` | Restores defaults |

A line with no recognised prefix is treated as a player command, so `spawn`, `/spawn` and
`player: spawn` all mean the same thing. A command that happens to contain a colon
(`minecraft:tp ...`, `say hello: there`) is not mistaken for a prefix.

An option's own `actions` run first, then the setting's `on-change`.

### Placeholders

Usable in labels, tooltips, messages and commands alike:

`%player%` `%uuid%` `%world%` `%category%` `%category_label%` `%setting%` `%setting_label%`
`%value%` `%value_upper%` `%option%` `%option_id%` `%option_plain%`

In MiniMessage text, `<player>` works as well as `%player%`. Placeholders belonging to other
plugins are left untouched rather than eaten.

Text a *player* typed is always inserted literally, so someone whose away message is `<red>oops`
cannot recolour your menu.

---

## Permissions inside the menu

- `permission:` on a **category** hides the whole page.
- `permission:` on a **setting** hides it, unless you also set `locked-label`, which shows a
  greyed-out teaser with a `locked-tooltip` instead.
- `permission:` on a single **option** makes the cycle skip it, so it can never be selected.

---

## Persistence

Values live in `plugins/SettingsPlus/playerdata/<uuid>.yml`, written through a temp file and an
atomic move so a crash mid-write cannot corrupt a player's file. Saves happen when a player quits,
on server shutdown, and on the `storage.save-interval-seconds` timer.

With `apply-on-join` enabled, each setting's actions re-run shortly after a player joins, so
server-side state such as the `/showchatfrom` mode above survives a reconnect. Individual settings
can opt out with `apply-on-join: false`.

---

## Folia

Folia is supported directly, not through a compatibility shim. Every scheduled task goes through
one helper that uses the region-aware schedulers present in plain `paper-api`:

- a player's command runs on **that player's region thread**,
- console commands run on the **global region thread**,
- all disk I/O runs on the **async scheduler**,
- work already on the right region runs inline, so a sequence of actions keeps the order you wrote
  it in.

The same jar runs unmodified on Paper. `/settingsplus info` reports which scheduler is live.

---

## For other plugins

`SettingChangeEvent` fires before every change — from the dialog, a command or the API — on the
player's region thread. It is cancellable, and `setNewValue` can substitute a different value.

```java
@EventHandler
public void onSettingChange(SettingChangeEvent event) {
    if (event.getFullKey().equals("chat.show-chat") && !event.getPlayer().hasPermission("chat.hide")) {
        event.setCancelled(true);
    }
}
```

---

## Building

```bash
mvn clean package     # target/SettingsPlus-<version>.jar
mvn test              # config parser, action parser, text and storage tests
```

GitHub Actions builds and tests every push and pull request, attaching the jar as an artifact.
Pushing a `v*` tag additionally publishes a release with the jar attached.

---

## Licence

MIT — see [LICENSE](LICENSE).
