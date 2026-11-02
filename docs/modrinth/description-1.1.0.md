![Honeycomb banner](https://raw.githubusercontent.com/BlueLabsMinecraft/Honeycomb/main/docs/modrinth/04-banner.png)

## One plugin. Six displays.

Tablist. Scoreboard. Nametags. Bossbars. Below name. Action bar. Same conditions block on all six, so a nether layout is one small block of YAML instead of a second plugin.

- **Change a line without a restart.** `/honeycomb panel` edits everything live and saves to `panel.yml`, so updates never overwrite your work.
- **Create layouts in the panel.** Every design you already have is listed with its conditions, and a bold green button makes a new one.
- **Conditions first.** Every layout page opens with who sees it: worlds, permissions, group, placeholder rules, priority.
- **Coming from TAB is one dialog.** Tick tablist, scoreboards, nametags, bossbars and animations, choose merge or replace, done. Backup first.
- **Velocity, BungeeCord, Paper, Purpur, Spigot, Folia.** One plugin, one file per Java version.

## Network aware

Behind Velocity or BungeeCord, Honeycomb asks the proxy for real numbers and shows them anywhere placeholders work:

| Placeholder | Shows |
|---|---|
| `%network_online%` | players across the whole network |
| `%network_server_online%` | players on this server |
| `%network_server%` | this server's name on the network |
| `%network_servers%` | how many servers the proxy reports |
| `%network_proxy%` | Velocity, BungeeCord or None |

Set `type: auto` and Honeycomb reads your own server configuration to tell Velocity from BungeeCord. Nothing is invented: until the proxy answers, the placeholders stay unresolved.

## Honeycomb 1.1.0 or TAB?

| | Honeycomb 1.1.0 | TAB |
|---|---|---|
| Tablist, scoreboard, nametags, bossbars | Yes | Yes |
| Below name line | Yes | Yes |
| Action bar | Yes | No |
| Per world layouts on every display | Yes | Yes |
| Placeholder conditions | Yes | Yes |
| Create layouts in game | Dialog panel | Config files |
| Network placeholders from the proxy | Yes | Yes |
| Presets in the jar | 9 | No |
| Import a TAB setup | One dialog | n/a |
| Folia | Yes | Yes |

TAB wins on years in production. Honeycomb wins on editing, presets, the action bar, per world nametags and a panel that cannot desync from your files.

## Install

| Jar | Versions | Java |
|---|---|---|
| `Honeycomb-1.1.0-java21.jar` | 1.21.9 to 1.21.11 | 21 |
| `Honeycomb-1.1.0-java25.jar` | 26.1 to 26.3 | 25 |

Drop the matching jar in `plugins/`, start once, run `/honeycomb panel` as an operator.

**On a proxy network:** the game server takes the jar above. The proxy takes one companion file, and that file is what registers Honeycomb under the Velocity and BungeeCord loaders on Modrinth:

| File | Loader | Goes in |
|---|---|---|
| `HoneycombProxy-1.1.0-velocity.jar` | Velocity | `plugins/` on Velocity |
| `HoneycombProxy-1.1.0-bungeecord.jar` | BungeeCord, Waterfall | `plugins/` on the proxy |

The backend jar itself stays Paper, Spigot, Bukkit, Purpur, Pufferfish and Folia only: it has no proxy manifest, which is why uploading it under a Velocity loader is rejected. PlaceholderAPI, LuckPerms and ProtocolLib are optional. The dialog panel needs Paper or Folia.

**Which boxes to tick when publishing:**

| File | Loaders | Game versions |
|---|---|---|
| `Honeycomb-1.1.0-java21.jar` | Paper, Spigot, Bukkit, Purpur, Pufferfish, Folia | 1.21.9, 1.21.10, 1.21.11 |
| `Honeycomb-1.1.0-java25.jar` | Paper, Spigot, Bukkit, Purpur, Pufferfish, Folia | 26.1, 26.2, 26.3 |
| `HoneycombProxy-1.1.0-velocity.jar` | Velocity | all six: 1.21.9 to 26.3 |
| `HoneycombProxy-1.1.0-bungeecord.jar` | BungeeCord, Waterfall | all six: 1.21.9 to 26.3 |

The proxy files are version agnostic (plugin messages only), so tick the whole range and nobody sees a version warning. The backend jars are tied to one game version each, so keep them split.

**Behind a proxy:** set `network.enabled: true` in `network.yml`. Velocity needs `bungee-plugin-message-channel = true` in `velocity.toml`; BungeeCord needs `bungee_plugin_messages: true`. Everything else is automatic.

## Commands

| Command | Does |
|---|---|
| `/honeycomb panel` | Opens the editor |
| `/honeycomb migrate` | Imports a TAB install |
| `/honeycomb preset load <name>` | Applies a preset |
| `/honeycomb reload` | Re-reads every file |
| `/updatelog` | The player facing update log |

Nine presets ship in the jar: SMP, Survival, DonutSMP, Lifesteal, Economy, PvP, SkyBlock, Prison, Factions.

## Links

Source and releases: [github.com/BlueLabsMinecraft/Honeycomb](https://github.com/BlueLabsMinecraft/Honeycomb)  
Docs and downloads: [honey.gamer.gd](https://honey.gamer.gd)

MIT, copyright (c) 2026 Blue Labs. Not affiliated with Mojang or Microsoft.

[![Partnered with Blue Network](https://raw.githubusercontent.com/BlueLabsMinecraft/Honeycomb/main/docs/modrinth/05-partner.png)](https://modrinth.com/server/bluenetwork)
