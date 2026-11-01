package dev.bluelabs.honeycomb.command.subcommand;

import dev.bluelabs.honeycomb.HoneycombPlugin;
import dev.bluelabs.honeycomb.command.Subcommand;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Prints a measured debug report and writes it to {@code logs/}.
 */
public final class DebugSubcommand implements Subcommand {

    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final HoneycombPlugin plugin;

    /**
     * @param plugin plugin instance
     */
    public DebugSubcommand(HoneycombPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "debug";
    }

    @Override
    public String permission() {
        return "honeycomb.debug";
    }

    @Override
    public String usage() {
        return "debug";
    }

    @Override
    public String description() {
        return "Prints timings, tasks and integration details";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        List<String> report = plugin.diagnostics().buildReport(plugin, plugin.version());
        sender.sendMessage(plugin.messages().component("command.debug-header"));
        for (String line : report) {
            sender.sendMessage(plugin.messages().component("command.debug-line", Map.of("line", line)));
        }
        File folder = new File(plugin.getDataFolder(), "logs");
        File target = new File(folder, "debug-" + FILE_STAMP.format(LocalDateTime.now()) + ".txt");
        // File output happens off the main thread: /debug must not stall the server.
        plugin.tasks().async("debug-write", () -> {
            try {
                if (!folder.exists() && !folder.mkdirs()) {
                    plugin.getLogger().warning("Could not create the logs folder");
                    return;
                }
                Files.writeString(target.toPath(), String.join(System.lineSeparator(), report) + System.lineSeparator(),
                        StandardCharsets.UTF_8);
                String relative = folder.getName() + "/" + target.getName();
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        plugin.messages().send(sender, "command.debug-written", Map.of("file", relative)));
            } catch (IOException exception) {
                plugin.getLogger().warning("Could not write the debug report: " + exception.getMessage());
            }
        });
        plugin.getLogger().info("Debug report requested by " + sender.getName().toLowerCase(Locale.ROOT));
    }
}
