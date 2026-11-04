# Changelog

## 1.1.1

- Fixed a crash that stopped the sidebar from drawing on a live server. The ping spoofer
  was created after the placeholder engine that reads it, so every scoreboard render threw
  a NullPointerException and the display was dropped. It is now created first, and
  `%player_ping%` falls back to the real measured ping if a spoofer is ever absent.
- `/hc web` no longer needs a typed code. Run it with no argument and a paste box opens,
  which takes up to 16384 characters, so a 700 character setup is copied and pasted rather
  than typed. Chat commands stop at 256 characters, so a code pasted after the command is
  now rejected with a clear message that points at the box instead of being cut in half.
- `/hc web` usage shows `web [code]`, and the help entry explains both ways in.
- Added ping spoofer tests covering off, fixed, range, jitter, reversed bounds and an
  unknown mode, which pins the numbers the tablist and sidebar draw.

## 0.12.0

- Closing tags now work across a whole layout. A design that opens <yellow> on the first
  row and closes it on the last keeps its colour on every row instead of only the first,
  which is exactly how TAB layouts are written.
- Action bar: the line above the hotbar is now a display of its own. Switch it on in
  config.yml under actionbar, or from the Action bar button in the panel, and give it any
  template, placeholders and animations included.
- The shipped welcome bossbar no longer appears on its own: bossbars are off by default,
  and importing TAB bars disables the sample bar so it cannot sit there over your design.

## 0.11.0

- The panel can now manage every layout instead of only the default one. Manage layouts lists
  each tablist design, sidebar, bossbar and nametag group that already exists, with its
  conditions shown next to the name, and one bold green Create new button.
- Each entry opens an editor with its own text, priority, worlds, permissions and placeholder
  conditions. Naming a new entry creates it and drops you straight into the editor.
- Panel headings use the honey gradient with a shadow, and the explanation lines are shadowed
  too, so a heading can never break a dialog on an older server.

## 0.10.1

- `/honeycomb migrate` opens a dialog for players: tick tablist, sideboards, below name,
  nametags, bossbars and animations, and choose merge or replace before pressing Preview
  or Import. The panel has an Import from TAB button that opens the same dialog.
- Fixed animations not importing: TAB stores them under an `animations:` root, which the
  reader skipped.
- Display failures now log a full stack trace once a minute instead of a bare exception
  name every tick.
- Sidebar and below name no longer crash on servers that refuse a private scoreboard or
  refuse custom score names; they report it once and fall back instead of warning forever.

## 0.10.0

- New name holograms: a text display above every player's head driven by a template,
  so `%luckperms_prefix%` or a level can float over names without touching the vanilla
  nametag. Configured in `holograms.yml`, off by default.
- `offset` sets the height in blocks, `update-interval` the refresh rate, `view-range`,
  `shadow`, `see-through` and `background` control the look.
- Holograms are per world and conditional: each profile can set its own offset and
  template and match on `worlds`, `permissions` or `placeholders` such as
  `%vault_eco_balance% >= 100000`.
- Each player owns one non persistent display entity, created on the thread that owns
  the player, so it also works on Folia.

## 0.9.3

- `/honeycomb migrate` now reads TAB's modern `header-footer.designs` blocks and
  `scoreboard.scoreboards` blocks, so a TAB install with per world designs imports instead
  of reporting an empty config.
- TAB `display-condition: '%world%=spawn|%world%=arenas'` becomes a Honeycomb world
  condition on a tablist profile or scoreboard entry. Designs keep their names and order.
- TAB `animations.yml` is imported, and `%animation:Name%` inside any line is rewritten to
  `<anim:name>` so the frames keep working.
- TAB `belowname-objective` now imports its text, not just its enabled flag.
- Legacy hex such as `&#FF007C` is converted to MiniMessage along with the `&` codes.
- Unsupported TAB sections (layout player list grid, playerlist objective column) are
  reported as skipped instead of failing silently.

## 0.9.2

- Panel list fields accept 50 rows instead of 6, with room for long MiniMessage lines.
- Saving a panel screen writes `panel.yml` first, then reloads, so a reload problem can no
  longer swallow the save. Both outcomes are reported in chat and the console.
- Panel Save buttons stay valid for 6 hours and 500 clicks instead of 3 clicks.
- The F3 brand accepts legacy codes (`&9`, `&c`, `&#FFAA00`) and MiniMessage tags.

## 0.9.1

- New: `/honeycomb migrate` imports a TAB installation. Header, footer, sidebar
  lines, nametag groups, bossbars and the below name display are copied from
  `plugins/TAB/config.yml` and `groups.yml`, legacy colour codes are converted to
  MiniMessage, TAB placeholders are mapped to PlaceholderAPI names, and the
  current files are backed up to `migration-backup/<timestamp>/` before writing.
  `migrate` without arguments is a preview, `migrate apply` writes and reloads.
- New: `docs/MIGRATION.md` describes the mapping and what is not copied.
- Fixed: migrated text closes every tag and resets formatting the way Minecraft
  does, so `&c&l[ADMIN] &c` no longer produces unbalanced MiniMessage.
- Tests: 7 new cases in `TabMigrationTest`, 126 tests green across both modules.

## 0.9.0 - 2026-11-01

- Folia support. The plugin detects the threaded region server and schedules every
  display on the scheduler that owns the player, so the same jar runs on Paper,
  Spigot, Bukkit and Folia.
- Nametags take conditions. A group can be limited to a world, a permission, a
  placeholder value or a region, which brings per world nametags to the last
  display that was missing them.
- Every display now reads conditions: tablist, sidebar, bossbars, below name and
  nametags. A nether layout or a staff layout works the same way on all five.

## 0.8.0 - 2026-11-01

- Conditions can compare placeholders, not only match worlds, groups and
  permissions. `%vault_eco_balance% >= 100000`, `%player_group% == owner` and
  `%player_world% contains nether` all select a layout the way TAB does.
- Operators: ==, !=, >=, <=, >, < and contains. Numbers are compared as numbers,
  so 100000, 100,000 and $100,000 all read the same.
- Conditions work on tablist, scoreboard, bossbar and below name layouts.
- Presets use arrows between label and value, YOURSERVER as heading and
  yourserver.net in the footer.
- Bundled presets refresh on upgrade, edited copies are kept in presets/backup-<version>.

## 0.7.2 - 2026-11-01

- Preset lines use an arrow instead of stacked spaces: Money -> $1,000 reads the
  same at every width.
- Every preset title and tablist header says YOURSERVER, every footer prints
  yourserver.net.
- New config key server-domain: presets swap yourserver.net for it on load, next
  to server-name.
- Loading a preset no longer warns about YOURSERVER, because that word is part of
  the design and now resolves from config.

## 0.7.1 - 2026-11-01

- Preset buttons show the short name only: SMP, PVP, ECONOMY and so on.
- Nine presets that copy the look of well known server styles: smp, survival,
  donutsmp, economy, pvp, lifesteal, skyblock, prison, factions.
- A preset that reads money, shards or kills names the placeholders it needs and
  warns when PlaceholderAPI is missing, instead of printing raw tokens.

## 0.7.0 - 2026-11-01

- Presets: `preset list`, `preset load <name>` and `preset save <name>`, plus a Presets
  screen in the panel. Five bundled looks (smp, survival, economy, pvp, factions) use a
  YOURSERVER token that `server-name` in config.yml replaces.
- Staff layouts can be edited in the panel, header, footer, title and rows, not only
  switched on and off.
- Operators no longer inherit a nametag group from the operator bypass, so a plain OP
  stops showing an owner or admin prefix.
- The ProtocolLib line appears on the F3 brand screen only.

## 0.6.0 - 2026-11-01

- Every default tablist and scoreboard line carries the same drop shadow as the title.
- Animations are created in the panel, and any of them can be used anywhere as
  `{honeycomb.<id>}` (the old `<anim:id>` still works).
- The update log is MiniMessage: gradient titles, coloured rows, placeholders.
- Text inputs are narrower, and each section explains in gray what to type.
- The brand screen and the main menu show ProtocolLib state in bold green or bold red.
- A mistyped subcommand now answers with the closest real one instead of a bare error.
- Missing integrations no longer invent values such as "None" or "Unavailable".

## 0.5.0 - 2026-11-01

- **ProtocolLib is now an optional dependency** (softdepend). When it is installed the
  F3 brand is rewritten on the packet, which is the only reliable way on Paper.
- The F3 brand screen shows the install hint only while ProtocolLib is missing, and
  switches to a "ProtocolLib detected" line once it is there.
- Servers older than 1.21.9 no longer fail to enable: the dialog panel is skipped and
  reported instead of throwing.


## 0.4.0 - 2026-11-01

- **Staff layouts** are a switch, not the default. `staff.tablist` and
  `staff.scoreboard` in config.yml, or the Staff layouts screen in the panel.
  Off on a fresh install, so everybody sees one layout.
- **Update log**: publish entries in the panel, read them with `/updatelog`,
  `/changelog` or `/updates`, stored in updates.yml.
- **Animations** can be created from the panel, not just the file.
- Panel headings are bold orange and the buttons are coloured.
- Removed `honeycomb.bypass` from `honeycomb.admin`; operators keep their displays.

## 0.3.1 - 2026-11-01

- **Operators keep their displays again.** `honeycomb.admin` used to grant
  `honeycomb.bypass`, so every op silently turned tablist, sidebar, bossbars and
  nametags off. Bypass must now be granted deliberately.
- **F3 brand** tries `setBrandName` first and otherwise sends the `minecraft:brand`
  packet on join and on reload, which works on builds without the method.
- **Command hider** answers with a configurable line (`commands.hidden-message`,
  default "You can't see that!"), takes extra names in `commands.hidden`, and strips
  hidden commands from tab completion through `AsyncTabCompleteEvent`.
- **Panel styling**: bold orange headings, coloured buttons.


## 0.3.0 - 2026-11-01

The panel works now, and three server-side tools moved into it.

- **Panel fixed**: buttons use clickable commands instead of command templates, save
  buttons receive the real dialog response, and a dialog that fails to build now tells
  the player and the console why instead of doing nothing.
- **`/honeycomb panel debug`**: builds all nine dialogs and reports OK or the exact
  exception. Works from the console, so a broken dialog is diagnosable without a client.
- **F3 brand**: `brand.enabled` and `brand.text` set the name in the debug screen.
- **Command hider**: `/pl`, `/plugins`, `/version`, `/ver`, `/about` and extra commands
  reply like an unknown command unless the player has `honeycomb.commands.view`.
- **Message editor**: the panel edits entries from messages.yml.
- Panel sections: tablist, scoreboard, nametags, bossbars, brand, commands, messages,
  animations.


## 0.2.0 - 2026-11-01

Configuration is no longer something you have to edit by hand.

- **In-game panel** (`/honeycomb panel`, permission `honeycomb.admin`): dialog screens for
  tablist, scoreboard and nametags. Edit headers, footers, titles, lines, intervals and
  group prefixes, press Save, and the change is applied immediately.
- Panel edits are stored in `panel.yml` and merged on load, so the commented default
  files are never rewritten. Delete an entry to fall back to the file value, delete the
  file to fall back to every default. One backup (`panel.yml.bak`) is kept.
- Dialogs need Minecraft 1.21.9+; on older servers the command reports that instead of
  failing, and everything still works from the files.
- **MiniMessage**: the shipped tablist header and scoreboard title now start with
  `<shadow:#000000:1>` so you can see the tag is supported. Gradients, rainbow, click and
  hover all work too. Added tests that pin the shadow, gradient and nested tag behaviour.
- Fixed an animation frame counter that could overflow on a long running server.

All notable changes to Honeycomb are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-10-31

First release. Everything below was built for this version; there is no earlier
history to migrate from.

### Added

- Tablist: header, footer, per-player list name, per-permission/world/group
  layouts, animations and full MiniMessage plus placeholder support.
- Sidebar scoreboards: per-player boards, stable row ordering, blank score
  numbers, conditional layouts, refresh intervals, no full rebuilds per update.
- Nametags: per-viewer teams, LuckPerms group or permission based groups,
  priorities, prefixes and suffixes, vanish awareness.
- Bossbars: multiple simultaneous bars, static or placeholder progress, timed
  removal, world/permission conditions, priority ordering.
- Below-name: health mode and custom text mode using fixed number formats.
- Sorting: group order plus alphabetical or numeric tie-breakers applied through
  Paper's player list order.
- Placeholder engine: 40+ internal placeholders, PlaceholderAPI bridge, caching,
  unknown placeholder reporting, custom registration through the API.
- Animation engine: frame based animations, one shared tick clock for all
  animations and displays.
- Commands: `/honeycomb` with `help`, `reload`, `version`, `status`, `debug`,
  `test`, `animations`, `placeholders`, `integrations`, `refresh`.
- Diagnostics: per-pass timing, error counters, task inventory, conflict
  detection, `/honeycomb debug` writes a report file.
- Public API: placeholder, animation, tablist and bossbar access plus events.
- Optional integrations: PlaceholderAPI, LuckPerms, Vault, EssentialsX, CMI,
  vanish plugins, Multiverse-Core, DiscordSRV and ProtocolLib detection, proxy
  player counts over BungeeCord plugin messaging.
- Configuration split across eleven files plus per-world overrides, with
  validation, migration and automatic backups.
- Two build artifacts from one source tree: Java 21 for Minecraft 1.21.9 to
  1.21.11 and Java 25 for Minecraft 26.1 to 26.3.
- 46 unit tests covering placeholder parsing, animations, sorting, profile
  conditions, version parsing and configuration helpers.

### Fixed during development

- Animation frame lookup overflowed for very large tick counts (non-looping
  animations could return an invalid index).
- Command messages rendered a literal `{detail}` token.
- Registered placeholders that cannot resolve in the current context (for
  example player placeholders on the console) were reported as unknown.
- Nametag updates re-sent unchanged prefixes and suffixes every interval.

### Known limitations

- Only Paper 1.21.9 has been run with the plugin (see `docs/TESTING.md`).
  Other supported versions compile against their own Paper API but were not
  started.
- Optional integrations were not exercised against the real plugins.
- Below-name custom mode and proxy player counts are implemented but not
  verified in a live network.
