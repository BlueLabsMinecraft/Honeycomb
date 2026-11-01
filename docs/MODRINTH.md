![Honeycomb banner](https://raw.githubusercontent.com/BlueLabsMinecraft/Honeycomb/main/docs/banner.png)

**The TAB alternative for Minecraft 1.21.9 to 26.3.** A tablist plugin, scoreboard plugin, nametag plugin, bossbar and below name line in one jar, for Paper, Spigot, Bukkit, Folia, Purpur and Pufferfish.

Everything you would normally split across four plugins lives in one file: tablist header and footer, sideboards that switch per world, nametags with per group prefixes, several bossbars at once, the below name line, and the action bar above the hotbar. Each of them reads the same conditions block, so a nether layout is one small block of YAML instead of a second config file.

## Why servers switch

- **No file editing for a text change.** `/honeycomb panel` opens native Minecraft dialogs that edit the tablist, sidebar rows, nametags, bossbars, action bar and animations while players are online. Changes land in `panel.yml`, so an update never overwrites your work.
- **One conditions block, six displays.** Worlds, groups, permissions and placeholder comparisons behave identically on the tablist, sidebar, bossbars, below name, nametags and action bar.
- **Every layout, not just the default one.** The panel lists each design you already have with its conditions shown next to the name, and one bold green button creates a new one with its own name and conditions.
- **Coming from TAB is one command.** `/honeycomb migrate` opens a dialog where you tick what to copy and choose merge or replace, then writes it with a backup first.
- **Presets that look like real servers.** SMP, DonutSMP, Lifesteal, PvP, Economy, SkyBlock, Prison, Factions and Survival ship in the jar.
- **Folia in the same jar.** Each display is scheduled on the thread that owns the player.
- **No paywall, no network calls.** MIT licensed.

![Honeycomb sidebar, tablist and panel](https://raw.githubusercontent.com/BlueLabsMinecraft/Honeycomb/main/docs/preview.png)

## Honeycomb or TAB?

| Capability | Honeycomb 0.12.0 | TAB |
|---|---|---|
| Tablist, sidebar, nametags, bossbars | Yes | Yes |
| Below name line | Yes | Yes |
| Action bar | Yes | No |
| Per world layouts on every display | Yes | Yes |
| Placeholder conditions (money, rank, world) | Yes | Yes |
| Staff only tablist and sidebar | Toggle in the panel | Via conditions |
| Create layouts in game | Dialog panel, name plus conditions | Config files |
| Preset library inside the jar | 9 presets | No |
| Import an existing TAB setup | Dialog, choose what to copy | n/a |
| Player facing update log | Yes | No |
| F3 brand line | With ProtocolLib | No |
| Folia | Yes, region scheduling | Yes |

TAB still wins on years in production and on a team that already knows its config. Honeycomb wins on editing, presets, the action bar and per world nametags. If you are on 1.21.9 or newer and want to change a line without a restart, this is the shorter path.

## Features

| | |
|---|---|
| **Tablist** | Header and footer of any length, per player list names, separate staff layout behind a permission |
| **Sidebar** | Layouts by priority and condition, up to 15 rows, per world switching, no flicker on updates |
| **Nametags** | Prefix, suffix and colour per group, applied per viewer with packet level diffing |
| **Bossbars** | Several at once, each with colour, overlay, progress and countdown |
| **Below name** | Health, ping or any placeholder under the floating name |
| **Action bar** | Any template above the hotbar, placeholders and animations included |
| **Animations** | Frame based motion with easing, created in the panel, usable as `{honeycomb.<id>}` |
| **Admin panel** | Native dialogs on 1.21.9 and newer, edit and save without a restart |
| **Conditions** | World, group, permission and placeholder comparisons pick the layout |
| **Presets** | Nine looks that mirror common server styles, plus your own saved ones |
| **Update log** | Entries written by staff, read by players with `/updatelog` |
| **Folia** | Region aware scheduling, same jar |

## Install

Two jars come from one source tree, because the Minecraft generations it supports do not share a Java runtime.

| Jar | Versions | Java |
|---|---|---|
| `Honeycomb-0.12.0-java21.jar` | 1.21.9 to 1.21.11 | 21 |
| `Honeycomb-0.12.0-java25.jar` | 26.1 to 26.3 | 25 |

Both run on Paper, Spigot, Bukkit, Folia, Purpur and Pufferfish.

1. Drop the jar that matches your server into `plugins/`.
2. Start the server once. Honeycomb writes its files and reports anything it adjusted.
3. Run `/honeycomb panel` as an operator, or `/honeycomb preset load smp` first.

PlaceholderAPI, LuckPerms and ProtocolLib are optional. Install ProtocolLib only if you want the F3 brand line replaced. The dialog panel needs Paper.

## Coming from TAB

```
/honeycomb migrate          opens the import dialog
/honeycomb migrate apply    skips the dialog and writes everything
```

The dialog lets you tick tablist, sideboards, below name, nametags, bossbars and animations, and choose merge or replace. Header, footer, sidebar, nametag groups, bossbars and below name are read from `plugins/TAB/config.yml` and `groups.yml`. Legacy colour codes become MiniMessage and TAB placeholders are mapped to their PlaceholderAPI names. Your files are backed up to `migration-backup/<timestamp>/` first.

Closing tags now work across a whole layout, so a design that opens `<yellow>` on the first row and closes it on the last keeps its colour on every row, exactly the way TAB layouts are written.

## Commands

| Command | Description | Permission |
|---|---|---|
| `/honeycomb panel` | Opens the in game editor | `honeycomb.admin` |
| `/honeycomb migrate` | Imports a TAB installation | `honeycomb.admin` |
| `/honeycomb reload` | Reloads every file, keeps sessions | `honeycomb.reload` |
| `/honeycomb preset list` | Lists bundled and saved presets | `honeycomb.admin` |
| `/honeycomb preset load <name>` | Applies a preset over the current layout | `honeycomb.admin` |
| `/honeycomb preset save <name>` | Captures the live layout as a preset | `honeycomb.admin` |
| `/honeycomb status` | Displays, integrations, unknown placeholders | `honeycomb.status` |
| `/honeycomb debug` | Render and packet diagnostics | `honeycomb.debug` |
| `/updatelog`, `/changelog`, `/updates` | Shows the update log to players | everyone |

Permissions follow one shape: `honeycomb.<area>`, plus `honeycomb.group.<name>` for rank based layouts. Staff layouts need the permission explicitly, operator status alone is not enough.

## Conditions

Every layout can carry a `conditions:` block, and the highest priority layout whose conditions all pass is the one a player sees.

```yaml
tablist:
  nether:
    priority: 30
    header:
      - "<bold>THE NETHER"
    conditions:
      worlds: [world_nether, nether]

scoreboards:
  high-roller:
    priority: 40
    conditions:
      placeholders:
        - "%vault_eco_balance% >= 100000"
```

Conditions apply to every display, so a nether nametag or a staff only board is the same block of YAML. Operators are `==`, `!=`, `>=`, `<=`, `>`, `<` and `contains`. Numeric comparisons accept `100000`, `100,000` and `$100,000`.

## Configuration

```yaml
# plugins/Honeycomb/config.yml
server-name: "NETHERITE SMP"
server-domain: "netheritesmp.net"

tablist:
  update-interval: 20
  header:
    - "<shadow:#000000:1><gradient:#FFD34A:#E28000><bold>%server_name%</bold></gradient></shadow>"
    - "<shadow:#000000:1><gray>Welcome back, <white>%player_name%</white></shadow>"
  footer:
    - "<shadow:#000000:1><gray>netheritesmp.net</gray></shadow>"

actionbar:
  enabled: true
  update-interval: 40
  template: "<shadow:#000000:1><white>%player_name% <dark_gray>| <yellow>$%vault_eco_balance%"
```

Around forty placeholders ship with the plugin, including `%player_name%`, `%player_ping%`, `%player_health%`, `%player_group%`, `%player_world%`, `%server_name%`, `%server_online%`, `%server_tps%` and `%network_online%`. Anything else goes to PlaceholderAPI when it is installed, so money and stat lines can read `%vault_eco_balance_formatted%`.

## Presets

Bundled: `SMP`, `Survival`, `DonutSMP`, `Economy`, `PvP`, `Lifesteal`, `SkyBlock`, `Prison`, `Factions`. Every preset uses arrows between label and value, `YOURSERVER` as the heading and `yourserver.net` in the footer, both replaced from `server-name` and `server-domain` when you load one.

```bash
/honeycomb preset load donutsmp
/honeycomb preset save my-server
```

## Server types this is built for

SMP and survival servers that want a clean sidebar plus a rank nametag, PvP and practice servers that need per arena layouts and an action bar, lifesteal and economy servers that switch the sidebar on balance conditions, and networks that need staff only tablists behind a permission.

## Links

- Source, issues and releases: [github.com/BlueLabsMinecraft/Honeycomb](https://github.com/BlueLabsMinecraft/Honeycomb)
- Documentation and downloads: [honey.gamer.gd](https://honey.gamer.gd)
- API notes for developers: [docs/API.md](https://github.com/BlueLabsMinecraft/Honeycomb/blob/main/docs/API.md)

Modrinth builds come from the same source tree as the GitHub releases.

![Honeycomb](https://raw.githubusercontent.com/BlueLabsMinecraft/Honeycomb/main/docs/logo.png)

MIT licensed, copyright (c) 2026 Blue Labs. Not affiliated with Mojang or Microsoft. The honeycomb icon is the item texture from the Minecraft Wiki.
