# Honeycomb 0.14.0 - handover

Two jars, one source tree. Java 21 jar runs 1.21.9 to 1.21.11, Java 25 jar runs 26.1 to 26.3.
Paper, Spigot, Bukkit, Folia, Purpur and Pufferfish.

| File | Version | Java |
|---|---|---|
| `Honeycomb-0.14.0-java21.jar` | 1.21.9 - 1.21.11 | 21 |
| `Honeycomb-0.14.0-java25.jar` | 26.1 - 26.3 | 25 |

## What changed in 0.14.0

**Panel lists.** Clicking Tablist, Scoreboard, Action bar, Nametags or Bossbars on the main
screen now opens a list of names, one button per existing entry. A name button opens that
entry: its text, its conditions, its priority. The last button in the list is
**Create new ...** in bold green, which asks for a name first, then the same fields, and only
then writes it.

**Staff layouts.** Turning a staff layout off no longer leaves staff looking at nothing. The
sidebar and the tablist fall back to the first enabled non-staff layout for that player, so
staff keep a working screen. Staff layouts still only apply to `honeycomb.staff` when they are
on.

**Sidebar.** Selection now falls back when no layout matches, instead of drawing an empty
sidebar. A staff layout that is off is skipped rather than selected and blanked.

**Messages.** `messages.yml` ships with the honey gradient prefix:

```yaml
prefix: "<shadow:#000000:1><bold><gradient:#FFE3A8:#FFB01F:#FF7A00>HONEYCOMB</gradient></bold></shadow> <shadow:#000000:1><dark_gray>»</dark_gray></shadow> "
```

A message key that is missing from the file is now printed as words (`Status line`), never as
the dotted key and never as "Missing message:". Replace your old `messages.yml` with the one in
this build to get the new prefix.

**MiniMessage.** `<yellow>hi</yellow>` and `<yellow>hi` both work. Unclosed tags continue across
the whole layout, `&e` codes convert, unknown tags are dropped instead of printing the markup.

**Nametags.** Teams are drawn on the same per-player board as the sidebar, so a prefix reaches
the client even with the sidebar off. When nothing applies the console says why.

**Action bar.** Named texts with worlds, permissions and priority, chosen like the other
displays, with `actionbar.template` as the fallback.

## Panel map

```
/honeycomb panel
  Tablist            header, footer, interval   | Existing designs (n) | Create new design
  Scoreboard         title, lines, interval     | Existing sideboards (n) | Create new sidebar
  Action bar         on/off, text, interval     | Existing texts (n) | Create new text
  Nametags           on/off, group prefixes     | Existing groups (n) | Create new group
  Bossbars           on/off, first bar, text    | Existing bossbars (n) | Create new bossbar
  Staff layouts      tablist and sidebar layouts, two on/off buttons
  Display toggles    per world and per display
```

## Conditions

Every entry can carry the same block:

```yaml
conditions:
  worlds: [world_nether]
  groups: [vip]
  permissions: [my.permission]
  placeholders:
    - "%vault_eco_balance% >= 100000"
```

Operators: `==`, `!=`, `>=`, `<=`, `>`, `<`, `contains`. Condition matching needs no permission
node of its own; the highest priority matching entry wins.

## Troubleshooting

**Nametags show nothing.** The console prints either `nametags are enabled, but nametags.yml has
no groups` or `no group matched <player>`. A group matches when its name equals the player's
LuckPerms group, when the player holds its `permission:` node, or when it is called `default`.

**Sidebar is empty.** `/honeycomb status` shows whether the display is enabled, and
`/honeycomb debug` writes a report. With 0.14.0 an unmatched player now gets the first enabled
layout instead of an empty board.

**A jar refuses to load.** Check the Java version column above: the 26.x jar needs Java 25.

## Build

```bash
./gradlew build                     # both jars plus tests
./gradlew :honeycomb-legacy:build   # Java 21 artifact
./gradlew :honeycomb-modern:build   # Java 25 artifact
```

138 tests pass on this build.

## Layout of the source

```
shared/            engine used by both jars
honeycomb-legacy/  1.21.x entry point
honeycomb-modern/  26.x entry point
docs/              API notes, migration notes, conditions notes
```

MIT, copyright (c) 2026 Blue Labs.
