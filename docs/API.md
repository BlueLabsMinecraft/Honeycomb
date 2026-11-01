# Honeycomb API

Honeycomb exposes a small, stable API so other plugins can add placeholders,
animations, nametag formats and one-off displays without touching internals.

```java
HoneycombApi api = HoneycombApiProvider.get();
if (api == null) {
    // Honeycomb is not installed or not enabled yet.
    return;
}
```

`HoneycombApiProvider.get()` returns `null` before Honeycomb enables and after it
disables, so check it once when you need it rather than caching it forever. Put
`depend: [Honeycomb]` or `softdepend: [Honeycomb]` in your `plugin.yml`; everything
here is optional, so `softdepend` is usually the right choice.

The API is safe to call from the main thread. Display changes take effect
immediately; Honeycomb's own update loop keeps them in sync afterwards and may
re-render the same element on its next pass.

## Methods

### `String version()`
The running Honeycomb version, for example `1.0.0`.

### `List<String> supportedMinecraftVersions()`
The Minecraft ranges this artifact was built for, for example
`["1.21.9", "1.21.11"]`.

### `void registerPlaceholder(String id, PlaceholderResolver resolver)`
Registers `%id%`. The resolver receives a `PlaceholderContext` and returns the
replacement text, or `null` to leave the placeholder visible. Returning `null` is
how you say "not available right now" without inventing a value.

```java
api.registerPlaceholder("myplugin_tokens", context -> {
    Player player = context.player();
    return player == null ? null : Integer.toString(tokens.get(player.getUniqueId()));
});
```

### `boolean unregisterPlaceholder(String id)`
Removes a placeholder registered through the API. Returns whether one was removed.
Built-in placeholders cannot be removed.

### `void registerAnimation(Animation animation)`
Registers an animation usable as `<anim:id>` in any template. `Animation` is
immutable; build one with `new Animation(id, intervalTicks, frames, loop)`.

```java
api.registerAnimation(new Animation("pulse", 4,
        List.of("<white>Loading", "<gray>Loading", "<dark_gray>Loading"), true));
```

### `boolean unregisterAnimation(String id)`
Removes an animation registered through the API.

### `void registerNametagFormat(String name, int priority, String prefix, String suffix, String permission)`
Adds a nametag group next to the ones in `nametags.yml`. `prefix` and `suffix` are
MiniMessage and may contain placeholders. Pass `null` for `permission` to make the
group only reachable by name.

```java
api.registerNametagFormat("vip", 50, "<gold>[VIP] </gold>", "", "myplugin.vip");
```

### `boolean unregisterNametagFormat(String name)`
Removes a group registered through the API.

### `Component render(Player player, String template)`
Runs the full pipeline for one template: animation tokens, then placeholders, then
MiniMessage. Useful when you want Honeycomb's formatting rules in your own UI.

### `void setTablistHeader(Player player, Component header)` / `void setTablistFooter(Player player, Component footer)`
Overrides one half of the tablist for that player. Honeycomb's next update may
replace it if a tablist profile is enabled for them.

### `BossBar showBossbar(Player player, Component title, float progress, BossBar.Color color, BossBar.Overlay overlay)`
Shows an Adventure bossbar to one player and returns it. The caller owns the bar:
Honeycomb never hides it, and you must call `hideBossbar` yourself.

### `void hideBossbar(Player player, BossBar bar)`
Hides a bar created with `showBossbar`.

### `void refresh(Player player)` / `void refreshAll()`
Forces the next render pass to ignore its diff caches so the player's displays are
redrawn immediately. Fires `PlayerDisplayRefreshEvent` for single-player refreshes.

## Events

### `HoneycombReloadEvent`
Fired after `/honeycomb reload` finished, with the reload duration and the number of
configuration warnings. Listen to it to reload your own data that references
Honeycomb placeholders.

```java
@EventHandler
public void onReload(HoneycombReloadEvent event) {
    getLogger().info("Honeycomb reloaded in " + event.millis() + "ms");
}
```

### `PlayerDisplayRefreshEvent`
Fired when a player's displays are refreshed on request. Not cancellable; it exists
so other plugins can piggyback on the same moment.

## Design notes

* Resolvers must be fast: they run inside the display loop. Cache anything that
  costs more than a map lookup, and return `null` rather than a stale value.
* Do not call Bukkit APIs that must run on the main thread from your resolver if
  you might be invoked off-thread; Honeycomb renders on the main thread only.
* Removing placeholders and animations that other plugins registered is not
  possible: `unregister*` only removes entries registered through the API.
