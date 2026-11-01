# Migrating from TAB

Honeycomb reads your TAB folder and writes the same content into its own config
files. Nothing is guessed: header, footer, sidebar, nametags, bossbars and the
below name display are copied with their text converted to MiniMessage.

## Use it

```
/honeycomb migrate            preview: what would be copied, how much of it
/honeycomb migrate apply      back up the current files, write, reload
/honeycomb migrate apply /path/to/plugins/TAB
```

`migrate` on its own never writes. It looks for a `TAB` folder next to
`plugins/Honeycomb`, reads `config.yml` and `groups.yml`, and reports how many
lines, groups and bars it found. Add `apply` to write.

Before writing, the current `tablist.yml`, `scoreboards.yml`, `nametags.yml`,
`bossbars.yml` and `belowname.yml` are copied to
`plugins/Honeycomb/migration-backup/<timestamp>/`, so the run is reversible.
The plugin reloads at the end; no restart.

## What is read

| TAB | Honeycomb |
|---|---|
| `header-footer.header` or `header` | `tablist.header` |
| `header-footer.footer` or `footer` | `tablist.footer` |
| `scoreboard.title` and `scoreboard.lines` | `scoreboards.default.title` and `.lines` |
| `groups.yml` `tabprefix` | `nametags.groups.<group>.prefix` |
| `groups.yml` `tagsuffix` | `nametags.groups.<group>.suffix` |
| `bossbar.bars.<name>` | `bossbars.<name>` (title, colour, style, progress) |
| `belowname.enabled: true` | `belowname.enabled: true`, mode `health` |

Groups starting with `_` (`_DEFAULT_`, `_OTHER_`) are skipped because Honeycomb
uses the fallback layout for those. `taborder` is turned into descending
priorities in file order.

## Text conversion

TAB uses legacy colour codes and its own placeholder names. Both are translated:

- `&0` to `&f` become named MiniMessage colours, `&l`, `&o`, `&n`, `&m`, `&k`
  become their tags, `&r` clears formatting, `&#RRGGBB` becomes
  `<color:#RRGGBB>`.
- Colour codes reset formatting the way Minecraft does, so `&c&l[ADMIN] &c` comes
  out as `<red><bold>[ADMIN] </bold></red>` instead of unbalanced tags.
- Trailing colour codes with no text after them are dropped.
- `%player%` becomes `%player_name%`, `%online%` becomes `%server_online%`,
  `%ping%` becomes `%player_ping%`, `%health%` becomes `%player_health%`,
  `%group%` becomes `%luckperms_primary_group_name%`, `%server%` and
  `%servername%` become `%server_name%`.

Placeholders that belong to other plugins are left untouched, so
`%vault_eco_balance%` keeps working as long as PlaceholderAPI and the matching
expansion are installed.

## After the migration

1. `/honeycomb status` to confirm the displays are enabled.
2. `/honeycomb panel` to adjust wording without editing files.
3. Remove the TAB jar. Both plugins want the tablist, so only one may stay.
4. Check `groups.yml` names against your permission groups. Honeycomb resolves a
   `honeycomb.group.<name>` permission when LuckPerms is absent, so groups that
   only exist in TAB will need that node.

## What is not copied

Animations, conditions, per world layouts and TAB's own placeholder cache are
TAB specific and have no direct equivalent. A migrated setup lands as a single
default layout; add per world or staff layouts afterwards, ideally from the
panel.
