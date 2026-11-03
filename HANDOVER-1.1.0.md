# Honeycomb 1.1.0 handover

Velocity and BungeeCord support, built, tested against a live Folia 1.21.11 server with a client and a stand-in proxy, packaged for both Java versions.

## What this version adds

### Proxy support, done properly

`NetworkService` speaks the payloads both proxies share on `bungeecord:main`, so one code path serves Velocity, BungeeCord, Waterfall and Purpur behind either.

- **Automatic detection.** `type: auto` reads the server's own configuration: `config/paper-global.yml` for Velocity (`proxies.velocity.enabled` or a non-empty secret), `spigot.yml` for BungeeCord (`settings.bungee-cord`), then `proxies.bungee-cord.online-mode` as a second Bungee signal. Detection never guesses from version strings.
- **Both channels.** Registration tries `bungeecord:main` and falls back to the legacy `BungeeCord` name, so old forks keep working.
- **Richer replies.** `PlayerCount` (whole network and per server), `GetServers` (server list, which also drives per-server polling) and `GetServer` (this server's name) are all handled.
- **Five placeholders.** `%network_online%`, `%network_server_online%`, `%network_server%`, `%network_servers%`, `%network_proxy%`.
- **Honest by design.** Nothing resolves until the proxy answers, and answers older than 60 seconds stop counting as live.
- **Panel page.** New `Network` entry in the panel shows proxy type, connection state, totals and server count, with Turn on/off and a Type cycler that writes to `panel.yml`.

### Files

| Path | Role |
|---|---|
| `network/ProxyType.java` | Enum plus configuration-driven detection |
| `network/ProxyReply.java` | Wire format, parsed into a record for testability |
| `network/NetworkService.java` | Channels, polling, state, placeholders |
| `network/ProxyMessageListener.java` | Bukkit listener, forwards to the service |
| `resources/network.yml` | Documented config with `type: auto` and `ask-server-list` |
| `panel/ConfigPanel.java` | Network screen |

### Setup, both proxies

Backend `network.yml`:

```yaml
network:
  enabled: true
  type: auto
  server-name: ""
  ask-server-list: true
  request-interval-seconds: 10
```

Velocity: `bungee-plugin-message-channel = true` in `velocity.toml` (default true), and Velocity must actually be enabled in `paper-global.yml` or the backend refuses the connection.

BungeeCord: `bungee_plugin_messages: true` in the proxy `config.yml` and `settings.bungee-cord: true` in `spigot.yml`.


### Proxy side jars (new)

The backend jar cannot carry `velocity-plugin.json` or `bungee.yml`, so the proxy side is a separate, tiny plugin:

| File | Platform | Manifest |
|---|---|---|
| `HoneycombProxy-1.1.0-velocity.jar` | Velocity 3.x | `velocity-plugin.json`, id `honeycombproxy` |
| `HoneycombProxy-1.1.0-bungeecord.jar` | BungeeCord, Waterfall | `bungee.yml` |

Both answer `PlayerCount`, `GetServers` and `GetServer` on `honeycomb:network` using the proxy's own data, and both are compiled against the real APIs (`velocity-api 3.3.0`, `bungeecord-api 1.21-R0.1`). The backend asks on `honeycomb:network`, `bungeecord:main` and the legacy `BungeeCord` channel, so it works with the bridge installed, or without it when the proxy forwards the bungee channel.

Sources live in `proxy/bungee` and `proxy/velocity`; build with `javac` against those two API jars and zip the classes with the manifest at the root.

## Icons in text (new in this build)

Minecraft 1.21.9 has a sprite component and MiniMessage 4.26 exposes it, so any item or block texture can sit inside a tablist line, a sidebar row, a bossbar title, an action bar text or a welcome message with no resource pack.

- Tag form: `<sprite:"minecraft:items":"minecraft:item/diamond">`.
- `Insert icon` sits on every editor: tablist (header), sidebar and welcome (lines), bossbars (title), action bar (text). It opens a picker with 30 common items plus a field for any item name.
- Dialogs have no cursor, so the icon is appended to the end of that field when the editor reopens.
- `dev.bluelabs.honeycomb.text.Icons` builds the tags, and `IconsTest` proves they parse into an object component with the markup stripped.

## F3 brand line (extended)

`brand.text` is now the whole line, rendered per player through the same pipeline as the displays, so placeholders (`%player_name%`, `%server_name%`, TPS, ping) and animations (`{honeycomb.<id>}`) work inside it. `brand.refresh-ticks` resends the line so an animation actually moves; `0` sends it once. The panel screen gained the refresh field and says plainly that the tx/rx counters under the brand are measured by the client and cannot be set by a server.

## Welcome and leave messages (new in this build)

`welcome.yml` plus `Server -> Welcome & leave`, built the same way as the other displays.

- Each entry has `scope: private` (only that player) or `scope: broadcast` (everyone, replacing the vanilla join or leave line) and `trigger: join` or `trigger: leave`.
- Conditions are the same as everywhere else: `groups`, `worlds`, `permissions` and `placeholders`, with the highest priority match winning. A staff greeting can sit above the default one.
- Lines take MiniMessage, `%placeholders%` and animations, and render through the same pipeline as the displays.
- The panel list and editor use the shared layout screen, so creating a message is the same flow as creating a scoreboard.

## Maintenance mode and server list (new in this build)

`maintenance.yml` is new, and so is the `Server -> Maintenance & MOTD` screen.

- **Maintenance mode:** `maintenance.enabled` blocks logins with the configured kick message. Anyone with `honeycomb.maintenance.bypass` still gets in.
- **MOTD:** `motd.enabled` rewrites both server list lines with MiniMessage, `{online}` and `{max}`.
- **Fake numbers:** `motd.online` and `motd.max` show a fixed count, `-1` keeps the real one.
- **Software name:** `motd.fake-software` replaces the name other tools read, so mcstatus or a proxy ping no longer shows Paper. Blank keeps the real one.

The counts and the software name are not on every server platform's ping event, so they are set through reflection and skipped quietly when the platform does not offer them. `ServerListService` caches the file and reloads with the rest of Honeycomb.

## Panel and command polish (same 1.1.0 build)

- **Create closed the screen.** Opening a dialog from inside a button click was dropped by the client. `DialogKit.show` now schedules the screen two ticks later on the player's own thread (`Scheduling.forPlayerLater`), so create, save and every other button that opens a screen lands.
- **Create reopened the editor** with the new layout loaded, refused names already in use (`panel.name-taken`), and survives a reload failure (`reloadQuietly`).
- **Front page rewritten.** Four explained groups instead of unexplained headings: Displays, Server, Setup, Maintenance. The Displays screen spells out what tablist, sidebar, action bar, nametags and bossbars are.
- **Help grouped and aligned.** `/hc` and `/hc help` print DISPLAYS, SERVER, COMING FROM TAB and OTHER, with the usage column padded so descriptions line up.
- **`/hc panel debug`** still builds every screen and reports failures, from the console or in game.

## Verification (this build)

Run on Folia 1.21.11-14 with Java 21, plugin `Honeycomb-1.1.0-mc1.21.9-1.21.11.jar`, a real 1.21.11 client and a stand-in proxy plugin that dispatches proxy payloads into the server:

| Check | Result |
|---|---|
| Startup | `Configuration: 0 warning(s), 0 error(s)` |
| Proxy registration | `Proxy support on: BungeeCord over bungeecord:main, BungeeCord` |
| Auto detection | With `paper-global.yml` velocity enabled, log reads `Proxy support on: Velocity` |
| PlayerCount | `%network_online%` resolved to 37 |
| GetServer | `%network_server%` resolved to `lobby` |
| GetServers | `%network_servers%` resolved to 3 |
| Rendered output | `Net 37 on lobby via BungeeCord, 3` in the tablist footer |
| Scoreboard on Folia | Packet engine active, objective and rows delivered |
| Console | No exceptions, no warnings from Honeycomb |
| Unit tests | 168 pass, 0 failures (14 new network tests) |
| Real BungeeCord proxy | BungeeCord build 26.1, backend in bungee mode, client through the proxy: `Net 1 on lobby via BungeeCord, 1` |
| Real Velocity proxy | Velocity 3.5.1, modern forwarding, client through the proxy: `Net 1 on lobby via Velocity, 1` |
| Proxy bridge load | `[honeycombproxy]: Honeycomb proxy bridge ready on honeycomb:network` on both proxies |

Client taps on panel buttons cannot be scripted with the test client; the panel screen was verified by construction and by driving the same save path the buttons call.

## Structure

```
shared/            one source tree
  network/         proxy support, new in 1.1.0
  display/         tablist, scoreboard (API and packet engines), nametags, bossbars, belowname, actionbar
  panel/           in-game dialog editor
  placeholder/     internal and PlaceholderAPI bridging
  preset/          nine presets
honeycomb-legacy/  1.21.9-1.21.11, Java 21
honeycomb-modern/  26.1-26.3, Java 25
jars/              prebuilt for both
```

Build: `./gradlew build --offline` (JDK 21 for the legacy module, JDK 25 for the modern one; both are auto-detected).

## Known limits

- The panel needs Paper or Folia; Spigot has no dialog API.
- Below-name and nametag features use Bukkit teams, so on Folia only the scoreboard is available until those move to packets.
- Network counts need a player online to carry the request, which is a proxy requirement rather than a plugin one.
