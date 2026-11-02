# Honeycomb 0.17.0 - handover

| File | Versions | Java |
|---|---|---|
| `Honeycomb-0.17.0-java21.jar` | 1.21.9 - 1.21.11 | 21 |
| `Honeycomb-0.17.0-java25.jar` | 26.1 - 26.3 | 25 |

## Scoreboard fix (Folia and everywhere else)

Rows are written the modern way first, one custom name per score. A server that refuses that
(older builds, some Folia setups) is switched once to team prefixes, the trick the long lived
scoreboard libraries use: the score entry stays invisible and the visible text lives in the
team. Every visible row is rewritten through the new path at the moment of the switch, so the
board never goes blank during it.

Three smaller causes of an invisible scoreboard are gone:

* creating the objective is idempotent, so a board that survived a reload no longer throws
  "objective already exists" and stops drawing,
* the slot is reclaimed on the first draw in case another plugin took the sidebar slot,
* the plain-entry fallback now also rewrites the teams, and a failure sends the player one chat
  line naming the exception instead of only writing to the console.

Learnings came from FastBoard (teams and prefix split, plain invisible entries, custom names
only where the server has them) and from the Folia scheduling rules.

## Panel wording

The scoreboard is called a scoreboard everywhere now, in the panel, the list, the migration
dialog and the handover. Conditions sit at the top of every entry page, above the text fields,
because they decide who sees the entry.

```
Name: default
Conditions: none
[ Enabled ]
[ Conditions: worlds ] [ Conditions: permissions ] [ Conditions: placeholder rules ]
[ Priority ]
[ Title ] [ Lines, one per row ] [ Update interval ] [ Hide numbers ]
[ Save ] [ Back ] [ Close ]
```

## Text

No em dashes anywhere in plugin output, config comments or docs. Dashes are plain hyphens.

Closing tags are repaired: paired works, missing still colours, a stray closing tag is dropped,
mismatched nesting is closed in the right order, unknown tags are removed.

## Defaults

Tablist, scoreboard, bossbar, action bar and below name are all on for a fresh install.

## Build

```bash
./gradlew build     # both jars plus 154 tests
```

MIT, copyright (c) 2026 Blue Labs.
