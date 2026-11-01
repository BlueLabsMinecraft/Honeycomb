![Honeycomb](https://raw.githubusercontent.com/BlueLabsMinecraft/Honeycomb/main/docs/modrinth/01-logo.png)

![Honeycomb, the TAB alternative for Minecraft 1.21.9 to 26.3](https://raw.githubusercontent.com/BlueLabsMinecraft/Honeycomb/main/docs/modrinth/02-title.png)

![The TAB alternative](https://raw.githubusercontent.com/BlueLabsMinecraft/Honeycomb/main/docs/modrinth/03-catch.png)

![Honeycomb banner](https://raw.githubusercontent.com/BlueLabsMinecraft/Honeycomb/main/docs/modrinth/04-banner.png)

## One jar. Six displays.

Tablist. Sidebar. Nametags. Bossbars. Below name. Action bar. Same conditions block on all six, so a nether layout is one small block of YAML instead of a second plugin.

- **Change a line without a restart.** `/honeycomb panel` edits everything live and saves to `panel.yml`, so updates never overwrite your work.
- **Create layouts in the panel.** Every design you already have is listed with its conditions, and a bold green button makes a new one.
- **Coming from TAB is one dialog.** Tick tablist, sideboards, nametags, bossbars and animations, choose merge or replace, done. Backup first.
- **Folia, Paper, Purpur, Spigot, Bukkit, Pufferfish.** One file per Java version.

## Honeycomb or TAB?

| | Honeycomb 0.12.0 | TAB |
|---|---|---|
| Tablist, sidebar, nametags, bossbars | Yes | Yes |
| Below name line | Yes | Yes |
| Action bar | Yes | No |
| Per world layouts on every display | Yes | Yes |
| Placeholder conditions | Yes | Yes |
| Create layouts in game | Dialog panel | Config files |
| Presets in the jar | 9 | No |
| Import a TAB setup | One dialog | n/a |
| Player facing update log | Yes | No |
| Folia | Yes | Yes |

TAB wins on years in production. Honeycomb wins on editing, presets, the action bar and per world nametags.

## Install

| Jar | Versions | Java |
|---|---|---|
| `Honeycomb-0.12.0-java21.jar` | 1.21.9 to 1.21.11 | 21 |
| `Honeycomb-0.12.0-java25.jar` | 26.1 to 26.3 | 25 |

Drop the matching jar in `plugins/`, start once, run `/honeycomb panel` as an operator. PlaceholderAPI, LuckPerms and ProtocolLib are optional. The dialog panel needs Paper.

## Commands

| Command | Does |
|---|---|
| `/honeycomb panel` | Opens the editor |
| `/honeycomb migrate` | Imports a TAB install |
| `/honeycomb preset load <name>` | Applies a preset |
| `/honeycomb status` | Displays, integrations, unknown placeholders |
| `/updatelog` | The player facing update log |

Nine presets ship in the jar: SMP, Survival, DonutSMP, Lifesteal, Economy, PvP, SkyBlock, Prison, Factions.

## Links

Source and releases: [github.com/BlueLabsMinecraft/Honeycomb](https://github.com/BlueLabsMinecraft/Honeycomb)  
Docs and downloads: [honey.gamer.gd](https://honey.gamer.gd)

MIT, copyright (c) 2026 Blue Labs. Not affiliated with Mojang or Microsoft.

![Partnered with Blue Network](https://raw.githubusercontent.com/BlueLabsMinecraft/Honeycomb/main/docs/modrinth/05-partner.png)
