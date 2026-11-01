package dev.bluelabs.honeycomb.command.subcommand;

import dev.bluelabs.honeycomb.HoneycombPlugin;
import dev.bluelabs.honeycomb.command.Subcommand;
import dev.bluelabs.honeycomb.migration.TabMigration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * Imports a TAB configuration into Honeycomb.
 */
public final class MigrateSubcommand implements Subcommand {

    private final HoneycombPlugin plugin;

    /**
     * @param plugin plugin instance
     */
    public MigrateSubcommand(HoneycombPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "migrate";
    }

    @Override
    public List<String> aliases() {
        return List.of("import", "tab");
    }

    @Override
    public String permission() {
        return "honeycomb.admin";
    }

    @Override
    public String usage() {
        return "migrate [apply] [folder]";
    }

    @Override
    public String description() {
        return "Copy your TAB settings into Honeycomb";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        TabMigration migration = new TabMigration(plugin);
        boolean apply = false;
        File folder = null;

        for (String argument : args) {
            if (argument.equalsIgnoreCase("apply") || argument.equalsIgnoreCase("confirm")) {
                apply = true;
            } else if (argument.equalsIgnoreCase("preview") || argument.equalsIgnoreCase("dry")) {
                apply = false;
            } else {
                folder = new File(argument);
            }
        }

        if (folder == null) {
            folder = migration.detect();
            if (folder == null) {
                sender.sendMessage(Component.text("No TAB folder found next to plugins/Honeycomb.", NamedTextColor.RED)
                        .append(Component.newline())
                        .append(Component.text("Point at one with /honeycomb migrate preview <path>", NamedTextColor.GRAY)));
                return;
            }
        }

        if (!new File(folder, "config.yml").isFile()) {
            sender.sendMessage(Component.text("No config.yml in " + folder.getAbsolutePath(), NamedTextColor.RED));
            return;
        }

        TabMigration.Report report;
        try {
            report = migration.run(folder, apply);
        } catch (RuntimeException exception) {
            sender.sendMessage(Component.text("Migration failed: " + exception.getMessage(), NamedTextColor.RED));
            plugin.getLogger().warning("Migration failed: " + exception);
            return;
        }

        sender.sendMessage(Component.text(apply ? "TAB migration applied" : "TAB migration preview",
                NamedTextColor.GOLD)
                .append(Component.text("  " + folder.getName(), NamedTextColor.DARK_GRAY)));

        for (String note : report.notes()) {
            sender.sendMessage(Component.text("  + " + note, NamedTextColor.GREEN));
        }
        if (report.belowname()) {
            sender.sendMessage(Component.text("  + below name switched on", NamedTextColor.GREEN));
        }
        for (String warning : report.warnings()) {
            sender.sendMessage(Component.text("  ! " + warning, NamedTextColor.YELLOW));
        }

        if (!apply) {
            if (!report.changed()) {
                sender.sendMessage(Component.text("Nothing to import from that folder.", NamedTextColor.GRAY));
                return;
            }
            sender.sendMessage(Component.text("Run /honeycomb migrate apply to write it.", NamedTextColor.AQUA));
            return;
        }

        if (report.backup() != null) {
            sender.sendMessage(Component.text("  Backed up to " + report.backup().getName(), NamedTextColor.GRAY));
        }
        sender.sendMessage(Component.text("Reloaded. Check /honeycomb status, then /honeycomb panel to tweak.",
                NamedTextColor.GRAY));
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length <= 1) {
            return List.of("preview", "apply");
        }
        return List.of();
    }
}
