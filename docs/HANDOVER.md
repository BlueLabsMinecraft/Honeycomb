# Handover

Written for whoever picks this up next, human or AI. It covers how the project is
put together, the rules that keep it working, what is finished, and where the sharp
edges are.

## Where things are

```
honeycomb/
├── shared/src/                all sources, resources and tests (the only source tree)
├── honeycomb-legacy/          Java 21 artifact, Paper 1.21.9 API, MC 1.21.9 - 1.21.11
├── honeycomb-modern/          Java 25 artifact, Paper 26.1.2 API, MC 26.1 - 26.3
├── docs/                      logo, API.md, TESTING.md, this file
├── tools/smoke-test/          headless client scripts used to verify displays
└── README.md, CONTRIBUTING.md, CHANGELOG.md, LICENSE, .gitignore
```

Both modules depend on `shared` and add only their platform dependency, their
toolchain, and their expanded `plugin.yml` / `honeycomb-build.properties`. Nothing is
duplicated between them, and there is no reflection or version branching in the
shared sources.

## Build and test

```bash
./gradlew build                        # both artifacts + 46 unit tests
./gradlew :honeycomb-legacy:jar        # -> honeycomb-legacy/build/libs/*.jar
./gradlew :honeycomb-modern:jar        # -> honeycomb-modern/build/libs/*.jar
```

JDK 21 and JDK 25 are both required for a full build. `gradle.properties` pins
toolchain paths; change them if your JDKs live elsewhere. Building only the legacy
artifact works on a machine that has just JDK 21.

## How the runtime fits together

```
HoneycombPlugin
├── ConfigManager          12 YAML files, validation, migration, backups
├── Settings / Messages    typed reads of config.yml and messages.yml
├── TickClock              one monotonic counter for everything
├── TaskRegistry           every scheduled task, cancellable by name
├── AnimationRegistry      named frame animations
├── PlaceholderRegistry    id -> resolver
│   └── PlaceholderService expansion + short lived cache
├── InternalPlaceholders   the built-in set, re-registered on reload
├── RenderPipeline         <anim:...> -> placeholders -> MiniMessage
├── IntegrationManager     optional plugin adapters, each loaded defensively
├── NetworkService         proxy player counts over plugin messaging
├── PlayerSessionRegistry  per-player display state
└── DisplayCoordinator     single 1-tick task -> every DisplaySystem.tick(player)
    ├── TablistService
    ├── ScoreboardService   -> PlayerBoard -> PlayerSidebar
    ├── NametagService      -> teams on the viewer's own board
    ├── BossbarService
    ├── BelowNameService
    └── SortingService      -> Paper setPlayerListOrder
```

`DisplaySystem` is the contract: `enable`, `disable`, `reload`, `tick(player)`,
`refresh(player)`, `forget(player)`, `updateInterval`, `describe`. Adding a display
means implementing that interface and registering it in `HoneycombPlugin`; nothing
else needs to change.

### Update model

One repeating task advances the clock and calls `system.tick(player)` for every
online player. Each system decides whether it is due, spreading players across ticks
with `PlayerSession#updateOffset()` so a hundred players do not all update on the
same tick. Every write is diffed against the last applied value before it is sent.

## Rules that must not be broken

1. **Never create a repeating task outside `TaskRegistry`.** Reload and disable both
   depend on cancelling by name. A leaked task survives a reload and the player data
   it holds is never released.
2. **Never touch a display Honeycomb did not create.** Bossbars are tracked per
   session, teams are only modified for names we generated, and the sidebar lives on
   our own board object. This is what keeps the plugin polite next to TAB or a
   scoreboard plugin.
3. **A player sees one scoreboard.** Sidebar and below-name share `PlayerBoard`
   through `BoardAccess`. Any new per-player objective belongs on that board and
   must be released through `BoardAccess.release`.
4. **Nametags are per viewer.** Team objects must be written on
   `viewer.getScoreboard()`, never on a single global board, or a player holding a
   private board stops seeing prefixes.
5. **Placeholders must not lie.** A resolver returns `null` when it cannot answer.
   Never substitute `0`, `unknown` or an empty string for a real measurement.
6. **Optional plugins stay optional.** Each adapter is constructed inside a try/catch
   after checking `Bukkit.getPluginManager().getPlugin(...)`; a failure leaves the
   fallback provider in place.
7. **Config files are user data.** `ConfigManager` only adds missing keys and writes
   a `.bak-<version>` before changing an existing file.

## Pitfalls already paid for

* **Bukkit `YamlConfiguration` silently ignores a file that uses tabs.** If a config
  change seems to have no effect, check indentation first. This trap cost a debugging
  session on another project of the owner's.
* **Paper remaps plugin jars on first load.** Replacing the jar while the server runs
  does nothing, and stale remapped copies confuse tests.
* **Generated configs win over the jar.** Editing `messages.yml` inside the jar does
  not change an existing `plugins/Honeycomb/messages.yml`. Delete the folder when
  testing shipped defaults.
* **Lower case packet field names.** Scoreboard packets use `display_name` and
  `number_format`; the team packet is called `teams`. Guessing camelCase names in a
  probe returns empty arrays and looks like a bug that is not there.
* **`Score#setScore` re-sorts the sidebar.** Rows keep a fixed score value so
  inserting a line never renumbers the others.
* **Adventure 4 versus 5.** The legacy artifact compiles against Adventure 4.25, the
  modern one against 5.2. Stay on APIs present in both (`Component`, `BossBar`,
  `Audience`) and the shared tree keeps compiling for both.
* **Run Gradle on JDK 25.** The modern module's Paper API is class file 69, which
  javac 21 cannot read.

## State of the work

Done and verified as described in `docs/TESTING.md`: config system, six display
systems, placeholder and animation engines, commands, diagnostics, public API,
proxy counts, unit tests, both artifacts, documentation.

Open items, in the order I would do them:

1. **Run the 26.x artifact on Paper 26.1.2.** It compiles against that API and avoids
   version specific calls, but it has never been started. This is the single most
   valuable next step.
2. **Start 1.21.10 and 1.21.11 servers** with the legacy artifact to close the gap
   between "built against 1.21.9" and "supported".
3. **Exercise the integrations for real**: LuckPerms for group and prefix, Vault for
   `%econ_balance%`, PlaceholderAPI for a foreign placeholder, EssentialsX vanish so
   the nametag skip path is proven.
4. **Below-name custom mode** on a real client with more than one player.
5. **Proxy player counts** against BungeeCord or Velocity, including the no-answer
   case.
6. **Load test** a few hundred simulated players to confirm the spread update model
   and the diffing, and to get a real number for the README.
7. Nice to have: per-world sorting rules, `/honeycomb reload <file>`, a bStats hook.

## Next version, as requested by the owner

Version 0.2 is planned as an in game admin panel built on the 1.21.9 dialog API
(`Player#showDialog`, Dialogs are Paper API in the modern range only, so this will
likely need its own module or a guarded adapter):

* An admin panel reachable only by operators holding `honeycomb.admin`.
* Everything currently in YAML should become editable from the panel: tablist,
  scoreboard, actionbar, bossbars, nametags, animations, sorting, placeholders.
* Actionbar is a display Honeycomb does not have yet; it needs a
  `DisplaySystem` implementation plus config file and defaults.
* The panel should also surface the things the plugin already knows but only shows in
  chat: display status, active layout per player, integration state, diagnostics.
* Not started. Do not half build it; `HoneycombApi` and `DisplaySystem` are the seams
  to extend.

## Conventions worth keeping

* Text in code and docs uses no em dashes; the owner prefers commas, colons and
  parentheses.
* Commands print through `messages.yml`, never with inline strings.
* Every user visible failure names the file and key that caused it.

## 0.2.0 additions

- `panel/ConfigPanel` builds Paper dialog screens (`io.papermc.paper.registry.data.dialog.*`).
  It is version guarded: `Player#showDialog` exists on 1.21.9+ only, and the class is built
  in `onEnable` so a linkage problem shows up at startup rather than on first click.
- `config/PanelOverrides` implements the override layer. If you add a new editable setting,
  add it to `ConfigType#rootKey` handling and call `PanelOverrides#apply` through
  `ConfigManager`, never write to the commented files from the panel.
- Dialog API notes: `DialogInput.bool(key, label, value, onTrue, onFalse)` returns a built
  input, not a builder; `TextDialogInput` needs `MultilineOptions.create(maxLines, height)`;
  button navigation uses `DialogAction.commandTemplate("honeycomb panel <section>")` to avoid
  depending on click callback signatures that differ between Adventure 4 and 5.
