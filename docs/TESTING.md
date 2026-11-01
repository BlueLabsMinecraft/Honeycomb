# What was tested, and how

This page separates what has been executed from what has only been compiled. It is
deliberately specific, because "supports 26.3" means nothing without a record of
what actually ran.

## Automated tests

```bash
./gradlew build
```

46 unit tests in `shared/src/test/java`, executed against the Java 21 artifact, all
passing. Coverage:

| Area | Class |
| ---- | ----- |
| Placeholder expansion, caching, unknown reporting | `PlaceholderServiceTest` |
| Placeholder registry behaviour | `PlaceholderRegistryTest` |
| Animation frame selection, looping, very large tick counters | `AnimationTest` |
| Sorting rules and tie-breakers | `SortingRuleTest` |
| Layout condition matching (worlds, globs, permissions, groups) | `ProfileConditionsTest` |
| Highest priority layout selection | `ProfileSelectorTest` |
| Config value helpers against real YAML | `ConfigValuesTest` |
| Report bookkeeping | `ValidationReportTest` |
| Server version parsing and range checks | `MinecraftVersionsTest` |
| Number and duration formatting | `NumbersTest`, `TimeFormatsTest` |

The suite caught one real defect during development: the animation frame index
overflowed for very large tick counters, so a non-looping animation could return an
invalid frame. It is fixed and covered.

## Live server, Paper 1.21.9 (build 59)

The Java 21 artifact was installed in a real Paper 1.21.9 server, started, and
driven through commands from the console.

Observed in the log:

```
[Honeycomb] Honeycomb 1.0.0 for Minecraft 1.21.9-1.21.11 (Java 21)
[Honeycomb] Server reports Minecraft 1.21.9
[Honeycomb] Configuration: 0 warning(s), 0 error(s)
```

* All twelve configuration files and `worlds/world.yml` were generated on first
  start.
* `/honeycomb version`, `status`, `integrations`, `placeholders`, `animations`,
  `debug`, `reload`, `refresh` and an unknown subcommand all produced expected
  output and no exceptions.
* `/honeycomb reload` reported 10 ms and left exactly two scheduled tasks, so the
  reload neither leaks nor duplicates tasks.
* `/honeycomb debug` reported 980 display passes with an average of 0.003 ms and a
  worst case of 0.255 ms on an empty server, and wrote its report file.
* Animation frames resolved and rendered: `<anim:honeycomb>` produced the coloured
  `HONEYCOMB` frame in the console.
* Shutdown was clean ("Honeycomb disabled after 00:01:39", no errors).
* Real placeholder values: `server_tps=20.00`, `server_mspt=0.39`,
  `server_memory_used=311`, `server_version=1.21.9`.

## Live client, two headless bots

A display nobody receives is not a working display, so the plugin was also checked
through real client connections. mineflayer's protocol support stops at 1.21.4, so
the same artifact was loaded on Paper 1.21.4 and two bots joined. This proves the
display code paths, not 1.21.9 specific rendering.

Received by the client:

* **Tablist** header `HONEYCOMB` / `Welcome, Tester!` and footer `Online: 1/5` /
  `play.example.net`. Gradient, `<bold>` and `%player_name%`,
  `%server_online%`, `%server_max_players%` all resolved.
* **Sidebar**: objective `honeycomb` created and set to the sidebar slot; score
  packets with `number_format: 0` (numbers hidden) and `display_name` carrying the
  rendered line (`━━━━━━━━━━━━` in `dark_gray`).
* **Bossbar**: one bar, configured title, `health 1`, `color 4` (blue).
* **Nametags**: team `hcf3d28cb072` containing `Tester`, prefix `[P] ` in gray,
  visibility `always`, written on the viewer's own board.

Two defects were found and fixed by this run: an unreplaced `{detail}` in command
messages, and console-context placeholders being reported as unknown. Packet level
diffing for nametag teams was added afterwards for the same reason.

Reproduction scripts live in `tools/smoke-test/`.

## Not verified

Stated plainly so nobody assumes otherwise:

* Paper 1.21.10, 1.21.11 and 26.1 - 26.3 were never started. Those artifacts compile
  against their own Paper API (`paper-api:26.1.2-R0.1-SNAPSHOT` for 26.x) and carry
  the range in their manifest, but "compiles" is not "runs".
* LuckPerms, Vault, PlaceholderAPI, EssentialsX, CMI, SuperVanish, PremiumVanish,
  Multiverse-Core, DiscordSRV and ProtocolLib were not installed during testing.
  Their absence was verified to be harmless; their presence was not exercised.
* Below-name in `custom` mode and proxy player counts were not observed end to end.
  Both ship disabled.
* Per-world overrides were only exercised implicitly: `worlds/world.yml` was parsed
  with no warnings, but no world-specific layout was rendered.
* No load test. The design targets hundreds of players (spread update offsets, one
  tick loop, diffed writes) but there is no measurement to quote.
