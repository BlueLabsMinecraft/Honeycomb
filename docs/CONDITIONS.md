# Conditions

Every layout can carry a `conditions:` block. When a player is rendered, Honeycomb
picks the highest priority layout whose conditions all pass. A layout without
conditions matches everyone, which is why a `priority: 0` default works.

```yaml
conditions:
  worlds: [world_nether, nether]     # any of these, !name excludes, globs work
  groups: [owner, admin]             # exact group names, lower case
  permissions: [honeycomb.staff]     # !node means must not have it
  placeholders:
    - "%vault_eco_balance% >= 100000"
```

## Placeholder comparisons

The `placeholders` list is what makes conditions behave like TAB: any text, from any
plugin, can decide the layout.

```
%placeholder% == value
%placeholder% != value
%placeholder% >= number
%placeholder% <= number
%placeholder% >  number
%placeholder% <  number
%placeholder% contains text
```

Rules:

* The left side is expanded first. Both sides may contain placeholders.
* `>=`, `<=`, `>`, `<` compare as numbers when both sides look numeric, so
  `100000`, `100,000` and `$100,000` all compare equal to `100000`.
* Everything else compares as text, case insensitive.
* A missing placeholder expands to nothing, which never matches a comparison
  except `==` against an empty value.

## Examples

Nether tablist, higher priority than the default board:

```yaml
tablist:
  nether:
    priority: 30
    header:
      - "<bold>THE NETHER"
    conditions:
      worlds: [world_nether, nether]
```

A staff board that only shows to staff, plus a richer board for supporters:

```yaml
scoreboards:
  staff:
    priority: 50
    staff: true
    conditions:
      permissions: [honeycomb.staff]

  supporter:
    priority: 30
    conditions:
      placeholders:
        - "%player_group% == supporter"
        - "%player_group% == patron"
```

Wait: entries inside one condition type are ANDed for permissions and placeholders,
and ORed for worlds. To express "supporter or patron" use two layouts with the same
bodies, or `contains` on a combined placeholder.
