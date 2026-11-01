package dev.bluelabs.honeycomb.command;

import dev.bluelabs.honeycomb.HoneycombPlugin;

/**
 * Keeps a typed reference to the router so subcommands can reach each other (for
 * example {@code /honeycomb} with no arguments shows help) without hard coding
 * names.
 */
public final class CommandRegistry {

    private final HoneycombPlugin plugin;
    private HoneycombCommand command;
    private Subcommand help;

    /**
     * @param plugin plugin instance
     */
    public CommandRegistry(HoneycombPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Creates the router and wires the plugin command.
     */
    public void register() {
        this.command = new HoneycombCommand(plugin);
        this.help = command.find("help");
        var pluginCommand = plugin.getCommand("honeycomb");
        if (pluginCommand == null) {
            plugin.getLogger().severe("Command 'honeycomb' is missing from plugin.yml; commands are unavailable");
            return;
        }
        pluginCommand.setExecutor(command);
        pluginCommand.setTabCompleter(command);
    }

    /**
     * @return the router
     */
    public HoneycombCommand command() {
        return command;
    }

    /**
     * @return the help subcommand
     */
    public Subcommand help() {
        return help;
    }
}
