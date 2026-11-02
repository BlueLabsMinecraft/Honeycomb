# Honeycomb 0.16.0 - handover

| File | Versions | Java |
|---|---|---|
| `Honeycomb-0.16.0-java21.jar` | 1.21.9 - 1.21.11 | 21 |
| `Honeycomb-0.16.0-java25.jar` | 26.1 - 26.3 | 25 |

Fresh install: tablist, sidebar, bossbar, action bar and below name are all on.

## Panel

`/honeycomb panel` -> Scoreboard (or Tablist, Action bar, Nametags, Bossbars) now opens a plain
list. One small button per entry, the name only; a fresh install has one called `default`.

Click `default` and the page shows:

```
Name: default
Conditions: none
Lines and text take MiniMessage, one row per line.
[ Enabled ]  [ Title ]  [ Lines, one per row ]  [ Update interval ]  [ Hide numbers ]
[ Priority ] [ Worlds ] [ Permissions ] [ Placeholder conditions, one per row ]
[ Save ] [ Back ] [ Close ]
```

Leave the fields alone and press Back to return to the list. Under `default` sits
**Create new**, which asks for a name, then opens the same page empty with a **Create** button.

## Defaults

| Display | Default (0.16.0) |
|---|---|
| Tablist | on, header, footer, player list names |
| Sidebar | on, layout `default`, 10 rows, numbers hidden |
| Bossbar | on, `welcome` bar |
| Action bar | on, ping and online count |
| Below name | on, health mode |
| Nametags | on |

Bossbar and action bar used to ship disabled, which is why a fresh server showed neither.

## Sidebar hardening

A sidebar can no longer go blank without saying why:

* the objective reclaims the `SIDEBAR` slot when it is first drawn, in case another plugin took it,
* coloured score entries fall back to plain entries if the server refuses them,
* a row the server rejects falls back to a plain stripped-text row instead of losing the board,
* any remaining failure is logged once with the exception name and message.

## Text

Closing tags are repaired: paired works, missing still colours, a stray `</gray>` is dropped,
mismatched nesting is closed in the right order, unknown tags are removed. Tablist header and
footer are parsed as one block, so a tag opened in the first row closes in the last.

## Conditions

```yaml
conditions:
  worlds: [world_nether]
  groups: [vip]
  permissions: [my.permission]
  placeholders:
    - "%vault_eco_balance% >= 100000"
```

Highest priority match wins; no conditions means everyone.

## Build

```bash
./gradlew build     # both jars plus 154 tests
```

MIT, copyright (c) 2026 Blue Labs.
