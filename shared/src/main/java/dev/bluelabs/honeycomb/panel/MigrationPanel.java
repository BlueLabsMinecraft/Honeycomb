package dev.bluelabs.honeycomb.panel;

import dev.bluelabs.honeycomb.HoneycombPlugin;
import dev.bluelabs.honeycomb.migration.TabMigration;
import io.papermc.paper.dialog.DialogResponseView;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.List;

/**
 * The dialog shown by {@code /honeycomb migrate}: tick what to copy from TAB, then
 * preview or write it.
 */
public final class MigrationPanel {

    private MigrationPanel() {
    }

    /**
     * Opens the import dialog.
     *
     * @param player player running the import
     * @param plugin plugin instance
     * @param folder TAB folder
     */
    public static void open(Player player, HoneycombPlugin plugin, File folder) {
        var inputs = List.of(
                DialogKit.toggle("tablist", "Tablist header and footer", true),
                DialogKit.toggle("scoreboards", "Scoreboards", true),
                DialogKit.toggle("belowname", "Below name line", true),
                DialogKit.toggle("nametags", "Nametag prefixes", true),
                DialogKit.toggle("bossbars", "Bossbars", true),
                DialogKit.toggle("animations", "Animations", true),
                DialogKit.toggle("override", "Replace Honeycomb layouts instead of merging", false));

        var buttons = List.of(
                DialogKit.save("Preview", (who, response) -> run(who, plugin, folder, response, false)),
                DialogKit.save("Import", (who, response) -> run(who, plugin, folder, response, true)),
                DialogKit.close());

        io.papermc.paper.dialog.Dialog dialog = DialogKit.form("Import from TAB", List.of(
                        DialogKit.hint("Folder: " + folder.getName()),
                        DialogKit.hint("Tick what to copy. Merge keeps your current entries."),
                        DialogKit.hint("Replace clears the Honeycomb layouts before writing."),
                        DialogKit.hint("Your files are backed up either way.")),
                inputs, buttons);
        DialogKit.show(player, plugin, dialog);
    }

    private static void run(Player player, HoneycombPlugin plugin, File folder,
                            DialogResponseView response, boolean apply) {
        TabMigration.Selection selection = new TabMigration.Selection(
                response.getBoolean("tablist"),
                response.getBoolean("scoreboards"),
                response.getBoolean("belowname"),
                response.getBoolean("nametags"),
                response.getBoolean("bossbars"),
                response.getBoolean("animations"),
                response.getBoolean("override"));

        TabMigration.Report report;
        try {
            report = new TabMigration(plugin).run(folder, apply, selection);
        } catch (RuntimeException failure) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "TAB import failed", failure);
            player.sendMessage(Component.text("Import failed: " + failure.getMessage(), NamedTextColor.RED));
            return;
        }

        player.sendMessage(Component.text(apply ? "Imported from TAB" : "Preview only, nothing written",
                NamedTextColor.GOLD));
        for (String note : report.notes()) {
            player.sendMessage(Component.text("  + " + note, NamedTextColor.GREEN));
        }
        if (report.belowname()) {
            player.sendMessage(Component.text("  + below name switched on", NamedTextColor.GREEN));
        }
        for (String warning : report.warnings()) {
            player.sendMessage(Component.text("  ! " + warning, NamedTextColor.YELLOW));
        }
        if (apply && report.backup() != null) {
            player.sendMessage(Component.text("  backed up to " + report.backup().getName(), NamedTextColor.GRAY));
        }
        if (!apply) {
            player.sendMessage(Component.text("Run /honeycomb migrate again and press Import to write it.",
                    NamedTextColor.AQUA));
        }
    }
}
