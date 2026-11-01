package dev.bluelabs.honeycomb.command;

import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * One {@code /honeycomb} subcommand.
 */
public interface Subcommand {

    /**
     * @return the subcommand name
     */
    String name();

    /**
     * @return alternative names, may be empty
     */
    default List<String> aliases() {
        return List.of();
    }

    /**
     * @return permission required, empty when every player may run it
     */
    String permission();

    /**
     * @return usage shown in the help list, without the leading command name
     */
    String usage();

    /**
     * @return short description shown in the help list
     */
    String description();

    /**
     * Runs the subcommand.
     *
     * @param sender the sender
     * @param args   arguments after the subcommand name
     */
    void execute(CommandSender sender, String[] args);

    /**
     * Provides tab completion.
     *
     * @param sender the sender
     * @param args   arguments after the subcommand name
     * @return suggestions
     */
    default List<String> complete(CommandSender sender, String[] args) {
        return List.of();
    }
}
