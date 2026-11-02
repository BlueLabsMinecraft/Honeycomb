# Honeycomb 0.15.0 - handover

| File | Versions | Java |
|---|---|---|
| `Honeycomb-0.15.0-java21.jar` | 1.21.9 - 1.21.11 | 21 |
| `Honeycomb-0.15.0-java25.jar` | 26.1 - 26.3 | 25 |

## What changed

**Closing tags.** The parser now repairs tag structure instead of handing raw markup to
MiniMessage. `<gray>hello</gray>` colours the text, `<yellow>hello` still colours it without a
closing tag, `hello</gray>` shows just `hello` because a closing tag with nothing open is
dropped, `<red><gray>x</red>` is closed in the right order, and an unknown tag such as
`<yellow text>` is removed rather than printed. This is the behaviour the 0.7 to 0.9 builds had
by parsing a whole block at once; the per line rewrite in 0.13 lost it.

**Commands removed.** `/honeycomb debug` and `/honeycomb status` are gone, along with their
classes. Relay and display state is in the panel and the console.

**Tablist and sidebar.** The tablist header and footer are parsed as one block again, so a tag
opened in the first row closes in the last. The sidebar keeps per row components but each row
is repaired by the same parser, so a stray closing tag in a row no longer blanks the board.

## Panel

```
/honeycomb panel
  Tablist / Scoreboard / Action bar / Nametags / Bossbars
      Existing <n>  -> one button per name -> opens that entry
      Create new    -> asks for the name, then the fields
  Staff layouts   tablist and sidebar layouts, two on/off buttons
```

With a staff layout off, staff get the first enabled normal layout instead of an empty screen.

## Conditions

```yaml
conditions:
  worlds: [world_nether]
  groups: [vip]
  permissions: [my.permission]
  placeholders:
    - "%vault_eco_balance% >= 100000"
```

Operators: `==`, `!=`, `>=`, `<=`, `>`, `<`, `contains`. Highest priority match wins.

## Messages

`messages.yml` ships with the honey gradient prefix and every key the commands use. A missing
key prints readable words, never the dotted path.

## Build

```bash
./gradlew build          # both jars plus tests
```

154 tests pass on this build, including eight that pin closing tag behaviour.

MIT, copyright (c) 2026 Blue Labs.
