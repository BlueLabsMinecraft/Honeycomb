package dev.bluelabs.honeycomb.panel;

import dev.bluelabs.honeycomb.HoneycombPlugin;
import dev.bluelabs.honeycomb.text.Icons;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The icon picker: browse the item atlas, then drop the icon into the field you came from.
 *
 * <p>Dialogs have no cursor position, so the icon is appended to the field when the
 * editor is rebuilt. That is why the picker remembers which field asked for it.</p>
 */
final class IconPicker {

    /** field name to sprite tag, waiting for the editor to pick it up. */
    private static final Map<UUID, Map<String, String>> PENDING = new HashMap<>();

    private IconPicker() {
    }

    /**
     * Takes an icon waiting for a field, if there is one.
     *
     * @param player player
     * @param field  field name
     * @return sprite tag or {@code null}
     */
    static String take(Player player, String field) {
        Map<String, String> waiting = PENDING.get(player.getUniqueId());
        if (waiting == null) {
            return null;
        }
        return waiting.remove(field);
    }

    /**
     * @param player player
     * @return true when the player asked for an icon
     */
    static boolean has(Player player) {
        Map<String, String> waiting = PENDING.get(player.getUniqueId());
        return waiting != null && !waiting.isEmpty();
    }

    private static void remember(Player player, String field, String tag) {
        PENDING.computeIfAbsent(player.getUniqueId(), key -> new HashMap<>()).put(field, tag);
    }

    /**
     * Opens the picker for one field.
     *
     * @param player player
     * @param plugin plugin
     * @param field  field the icon goes back into
     * @param reopen command that reopens the editor
     */
    static void open(Player player, HoneycombPlugin plugin, String field, String reopen) {
        List<DialogInput> inputs = List.of(
                DialogKit.text("item", "Any item name, for example diamond or oak_log", ""));
        List<ActionButton> buttons = new ArrayList<>();
        for (String item : Icons.COMMON) {
            buttons.add(DialogKit.act(
                    DialogKit.styled(Icons.sprite(item) + " <gray>" + item.replace('_', ' ')),
                    audience -> {
                        if (audience instanceof Player who) {
                            remember(who, field, Icons.sprite(item));
                            who.closeDialog();
                        }
                    }));
        }
        buttons.add(DialogKit.save("Use the typed item", (who, response) -> {
            String typed = response.getText("item");
            if (typed == null || typed.isBlank() || !Icons.plausible(typed)) {
                who.sendMessage(plugin.messages().prefixed("panel.icon-invalid", Map.of()));
                return;
            }
            remember(who, field, Icons.sprite(typed));
            who.closeDialog();
        }));
        buttons.add(DialogKit.run("Back", reopen));
        buttons.add(DialogKit.close());

        List<Component> body = List.of(
                DialogKit.heading("ICONS"),
                DialogKit.shade("Pick an icon. It is added to the end of the field you came from."),
                DialogKit.shade("These are the real game textures, so no resource pack is needed."));
        DialogKit.show(player, plugin, DialogKit.form("Icons", body, inputs, buttons));
    }
}
