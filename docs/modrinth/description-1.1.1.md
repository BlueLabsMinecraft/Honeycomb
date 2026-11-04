![Honeycomb banner](https://raw.githubusercontent.com/BlueLabsMinecraft/Honeycomb/main/docs/modrinth/04-banner.png)

## One plugin. Six displays.

Tablist. Scoreboard. Nametags. Bossbars. Below name. Action bar. Same conditions block on all six, so a nether layout is one small block of YAML instead of a second plugin.

- **Change a line without a restart.** `/honeycomb panel` edits everything live and saves to `panel.yml`, so updates never overwrite your work.
- **Design it in a browser too.** The web editor at honey.gamer.gd/web-editor builds the same layouts and prints an `hc1-` code.
- **Long codes never get typed.** `/honeycomb web` opens a paste box that takes 16384 characters, because a real layout code is far longer than chat's 256 character limit.
- **Conditions first.** Every layout page opens with who sees it: worlds, permissions, group, placeholder rules, priority.
- **Coming from TAB is one dialog.** Tick tablist, scoreboards, nametags, bossbars and animations, choose merge or replace, done. Backup first.

## New in 1.1.1

- **The sidebar can no longer fail to draw.** 1.1.1 fixes a startup order bug where the ping spoofer was created after the engine that reads it, which threw a NullPointerException on every scoreboard render and silently dropped the sidebar. `%player_ping%` now also falls back to the real measured ping if a spoofer is ever absent.
- **`/honeycomb web` works with or without a code.** No argument opens the paste box. A code pasted after the command over 250 characters is redirected there with a clear message instead of being cut at the chat limit.
- **Checked on a real server.** Paper 1.21.9 with a bot client: join, welcome, tablist header, sidebar rows, bossbar, action bar, permission refusals, every admin subcommand. Report ships in the release.

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

## Honeycomb 1.1.1 or TAB?

| | Honeycomb 1.1.1 | TAB |
|---|---|---|
| Tablist, scoreboard, nametags, bossbars | Yes | Yes |
| Below name line | Yes | Yes |
| Action bar | Yes | No |
| Per world layouts on every display | Yes | Yes |
| Placeholder conditions | Yes | Yes |
| Create layouts in game | Dialog panel | Config files |
| Design layouts in a browser | Web editor, `hc1-` code | No |
| Paste a long design, no typing | Paste box, 16384 chars | No |
| Network placeholders from the proxy | Yes | Yes |
| Presets in the jar | 9 | No |
| Import a TAB setup | One dialog | n/a |
| Folia | Yes | Yes |

TAB wins on years in production. Honeycomb wins on editing, presets, the action bar, per world nametags, a browser editor and a panel that cannot desync from your files.

## Install

| Jar | Versions | Java |
|---|---|---|
| `Honeycomb-1.1.1-mc1.21.9-1.21.11.jar` | 1.21.9 to 1.21.11 | 21 |
| `Honeycomb-1.1.1-mc26.1-26.3.jar` | 26.1 to 26.3 | 25 |

Drop the matching jar in `plugins/`, start once, run `/honeycomb panel` as an operator. PlaceholderAPI, LuckPerms and ProtocolLib are optional. The dialog panel needs Paper or Folia.

**Behind a proxy:** set `network.enabled: true` in `network.yml`. Velocity needs `bungee-plugin-message-channel = true` in `velocity.toml`; BungeeCord needs `bungee_plugin_messages: true`. Everything else is automatic.

## Commands

| Command | Does |
|---|---|
| `/honeycomb panel` | Opens the editor |
| `/honeycomb web [code]` | Imports a browser design, with or without the code |
| `/honeycomb migrate` | Imports a TAB install |
| `/honeycomb preset [name]` | Lists, loads or saves presets |
| `/honeycomb test` | Sends a test display through every system |
| `/honeycomb refresh [player]` | Re-applies displays |
| `/honeycomb reload` | Re-reads every file |
| `/honeycomb placeholders` | Shows resolved placeholder values |
| `/honeycomb version` | Plugin, build and server versions |
| `/updatelog` | The player facing update log |

Nine presets ship in the jar: SMP, Survival, DonutSMP, Lifesteal, Economy, PvP, SkyBlock, Prison, Factions.

## Links

Source and releases: [github.com/BlueLabsMinecraft/Honeycomb](https://github.com/BlueLabsMinecraft/Honeycomb)  
Docs and downloads: [honey.gamer.gd](https://honey.gamer.gd)  
Web editor: [honey.gamer.gd/web-editor](https://honey.gamer.gd/web-editor.html)

MIT, copyright (c) 2026 Blue Labs. Not affiliated with Mojang or Microsoft.

[![Partnered with Blue Network](https://raw.githubusercontent.com/BlueLabsMinecraft/Honeycomb/main/docs/modrinth/05-partner.png)](https://modrinth.com/server/bluenetwork)
