# Honeycomb 1.0.0

Two jars from one source tree.

| File | Minecraft | Java |
|---|---|---|
| `Honeycomb-1.0.0-java21.jar` | 1.21.9, 1.21.10, 1.21.11 | 21 |
| `Honeycomb-1.0.0-java25.jar` | 26.1, 26.2, 26.3 | 25 |

Platforms: Paper, Folia, Purpur, Spigot, Bukkit, Pufferfish.

## Verification (this build)

Run against Folia 1.21.11 with a real 1.21.11 client on a clean config folder:

| Check | Result |
|---|---|
| Tablist header and footer | Rendered, placeholders resolved |
| Scoreboard title and rows | 9 rows, gradient title |
| Bossbar | Rendered, gradient title |
| Action bar | Rendered with ping, online, TPS |
| Below name | Objective created on join |
| Panel | Dialog opens, sections and counts |
| Reload | 14 ms, 0 warnings, 0 errors |
| Preset load | SMP applied, 4 tablist lines, 8 scoreboard rows |
| Unresolved placeholders | None |
| Console errors | None |

## What changed from 0.12

### Panel design

1. Main menu split into DISPLAYS, SERVER and MAINTENANCE groups instead of one flat list.
2. Every display button carries its layout count, for example `Scoreboard (2)`.
3. Section pages open with an uppercase title and a count line, so you know how many exist before scrolling.
4. Layout names get a marker and open with one click, no summary clutter.
5. Empty sections say so in amber instead of showing a blank page.
6. Create button is `+ Create new <kind>` in bold green, always directly under the last layout.
7. Layout pages are headed `SCOREBOARD · DEFAULT`, kind and name together.
8. A `Who sees it` line names the audience: green `everyone` or gold rule count.
9. Conditions are the first fields on every page.
10. Save reads `Create layout` for a new entry and `Save changes` for an existing one.
11. Saving confirms in chat through the shared prefix instead of a bare green line.
12. Save failures and missing names report through the same message file.
13. Back returns to the section list, Done always closes, Apply changes reloads.

### Shipped design

14. Tablist redesigned: brand line, separator rows, `Welcome back`, players, ping and domain footer.
15. Scoreboard redesigned: aligned key and value rows, no score numbers, domain footer.
16. Bossbar redesigned: brand gradient with the player name and progress overlay.
17. Action bar redesigned: one glanceable line with ping, online and TPS.
18. Message prefix unified to the three stop honey gradient with matching separator.
19. `%player_prefix%` and `%player_suffix%` collapse to empty when LuckPerms is absent instead of printing raw tokens.
20. Default scoreboard no longer shows a Rank row that needs an integration.

### Behaviour

- Folia scoreboard engine sends objective, display slot and rows as packets; the Bukkit board path stays for Paper.
- A layout whose placeholder rule cannot be compared no longer matches, so demo layouts cannot hijack the screen.
- `plugin.reload()` cannot throw, so a panel save always finishes and reopens.
- Dialogs open on the owning thread.
- `/honeycomb debug` and `/honeycomb status` removed, along with their permissions.

## Structure

```
shared/               sources for both jars
honeycomb-legacy/     1.21.9 - 1.21.11, Java 21
honeycomb-modern/     26.1 - 26.3, Java 25
docs/                 api, migration, presets, modrinth copy
```

Scoreboard engine: `display/scoreboard/PlayerSidebar.java` (Bukkit API) and `display/scoreboard/packet/PacketSidebar.java` (packets). Both implement `Sidebar`.

## Build

```
./gradlew build          # both jars, 154 tests
```

JDK 21 and JDK 25 must be installed. Builds are offline friendly once dependencies are cached.

## Config files

`config.yml`, `tablist.yml`, `scoreboards.yml`, `nametags.yml`, `bossbars.yml`, `belowname.yml`, `messages.yml`, `placeholders.yml`, `animations.yml`, `integrations.yml`, `network.yml`, `updates.yml`, plus `panel.yml` for panel edits and `worlds/` for per world overrides.

Everything ships enabled. Panel edits always win over the shipped files.
