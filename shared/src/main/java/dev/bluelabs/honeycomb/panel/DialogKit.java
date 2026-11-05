package dev.bluelabs.honeycomb.panel;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Small helpers for the configuration panel dialogs.
 *
 * <p>Every dialog Honeycomb shows is built from the same three pieces: a
 * {@link DialogBase} holding the title, body and inputs, a {@link DialogType} holding
 * the buttons, and actions attached to those buttons. Keeping the construction in one
 * place means a mistake in the Paper dialog API only has to be fixed once.</p>
 */
final class DialogKit {

    private static final ClickCallback.Options ONCE = ClickCallback.Options.builder()
            .uses(1)
            .lifetime(Duration.ofMinutes(30))
            .build();

    private DialogKit() {
    }

    /** A plain information dialog with a single dismiss button. */
    static Dialog notice(String title, List<Component> lines, ActionButton button) {
        List<DialogBody> body = new ArrayList<>();
        for (Component line : lines) {
            body.add(DialogBody.plainMessage(line));
        }
        List<ActionButton> actions = new ArrayList<>();
        if (button != null) {
            actions.add(button);
        }
        return Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(heading(title))
                        .body(body)
                        .canCloseWithEscape(true)
                        .build())
                .type(actions.isEmpty() ? DialogType.notice() : DialogType.notice(actions.get(0))));
    }

    /** A dialog with several buttons laid out in a grid. */
    static Dialog menu(String title, List<Component> lines, List<ActionButton> buttons) {
        List<DialogBody> body = new ArrayList<>();
        for (Component line : lines) {
            body.add(DialogBody.plainMessage(line));
        }
        return Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(heading(title))
                        .body(body)
                        .canCloseWithEscape(true)
                        .build())
                .type(DialogType.multiAction(buttons).build()));
    }

    /** A form: text inputs plus the buttons that act on them. */
    static Dialog form(String title, List<Component> lines, List<DialogInput> inputs,
                       List<ActionButton> buttons) {
        List<DialogBody> body = new ArrayList<>();
        for (Component line : lines) {
            body.add(DialogBody.plainMessage(line));
        }
        return Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(heading(title))
                        .body(body)
                        .inputs(inputs)
                        .canCloseWithEscape(true)
                        .build())
                .type(DialogType.multiAction(buttons).build()));
    }


    /**
     * Runs a button handler so a failure is reported instead of swallowed.
     *
     * <p>Paper drops an exception thrown inside a click callback, which used to look
     * like a button that closes the screen and does nothing. The player now gets the
     * reason in chat and the console keeps the stack trace.</p>
     *
     * @param player  player who clicked
     * @param handler the work
     */
    private static void guard(Player player, Runnable handler) {
        try {
            handler.run();
        } catch (RuntimeException | LinkageError failure) {
            Bukkit.getLogger().warning("Honeycomb panel action failed: " + failure);
            player.sendMessage(Component.text("Honeycomb: that did not save ("
                    + failure.getClass().getSimpleName()
                    + (failure.getMessage() == null ? "" : ": " + failure.getMessage())
                    + "). The console has the details.", NamedTextColor.RED));
        }
    }


    /**
     * A red line that explains what went wrong inside the screen the player is on, instead
     * of closing it and sending a chat message.
     *
     * @param text what to fix
     * @return the component
     */
    static Component error(String text) {
        return styled("<shadow:#000000:1><red><bold>! <reset><red>" + MiniMessage.miniMessage()
                .escapeTags(text));
    }

    /**
     * A plain explanation line, dimmer than the heading and never styled as markup.
     *
     * @param text the sentence
     * @return the component
     */
    static Component note(String text) {
        return styled("<shadow:#000000:1><dark_gray>" + MiniMessage.miniMessage().escapeTags(text));
    }

    /** A one line text field. */
    static TextDialogInput text(String key, String label, String initial) {
        return DialogInput.text(key, Component.text(label))
                .initial(initial == null ? "" : initial)
                .maxLength(4096)
                .width(260)
                .build();
    }

    /** Rows a list input accepts. The client scrolls past the visible height. */
    static final int MAX_ROWS = 50;

    /**
     * Height of a list input, in pixels.
     *
     * @param rows visible rows to size the box for
     * @return a height between 140 and 400 pixels
     */
    private static int height(int rows) {
        return Math.max(140, Math.min(400, rows * 18 + 40));
    }

    /** A multi line text field, used for lists such as scoreboard lines. */
    static TextDialogInput lines(String key, String label, String initial, int rows) {
        return DialogInput.text(key, Component.text(label))
                .initial(initial == null ? "" : initial)
                .maxLength(16384)
                .width(320)
                .multiline(TextDialogInput.MultilineOptions.create(MAX_ROWS, height(rows)))
                .build();
    }

    /** A checkbox. */
    static DialogInput toggle(String key, String label, boolean initial) {
        return DialogInput.bool(key, Component.text(label), initial, "on", "off");
    }

    /** A button that runs a Honeycomb command as the player who clicked it. */
    static ActionButton run(String label, String command) {
        return run(Component.text(label, NamedTextColor.AQUA), command);
    }

    /** A button with a custom label that runs a command. */
    static ActionButton run(Component label, String command) {
        return ActionButton.builder(label)
                .width(150)
                .action(DialogAction.staticAction(ClickEvent.runCommand("/" + command)))
                .build();
    }

    /** A button with a custom label that calls back into the server. */
    static ActionButton act(Component label, Consumer<Audience> handler) {
        return ActionButton.builder(label)
                .width(150)
                .action(DialogAction.staticAction(ClickEvent.callback(audience -> call(audience, handler))))
                .build();
    }

    /**
     * Runs a click handler with reporting, whoever clicked.
     *
     * @param audience who clicked
     * @param handler  the work
     */
    private static void call(Audience audience, Consumer<Audience> handler) {
        if (audience instanceof Player player) {
            guard(player, () -> handler.accept(audience));
            return;
        }
        handler.accept(audience);
    }

    /** A button with a custom label that receives the typed values. */
    static ActionButton save(Component label, BiConsumer<org.bukkit.entity.Player, DialogResponseView> handler) {
        return ActionButton.builder(label)
                .width(150)
                .action(DialogAction.customClick((response, audience) -> {
                    if (audience instanceof org.bukkit.entity.Player player) {
                        guard(player, () -> handler.accept(player, response));
                    }
                }, ClickCallback.Options.builder().uses(500).lifetime(Duration.ofHours(6)).build()))
                .build();
    }

    /** A button that hands the click back to the server. */
    static ActionButton act(String label, Consumer<Audience> handler) {
        return ActionButton.builder(Component.text(label, NamedTextColor.AQUA))
                .width(150)
                .action(DialogAction.staticAction(ClickEvent.callback((Audience audience) -> call(audience, handler))))
                .build();
    }

    /** A button that receives the values typed into the dialog's inputs. */
    static ActionButton save(String label, BiConsumer<org.bukkit.entity.Player, DialogResponseView> handler) {
        return ActionButton.builder(Component.text(label, NamedTextColor.AQUA))
                .width(150)
                .action(DialogAction.customClick((response, audience) -> {
                    if (audience instanceof org.bukkit.entity.Player player) {
                        guard(player, () -> handler.accept(player, response));
                    }
                }, ClickCallback.Options.builder().uses(500).lifetime(Duration.ofHours(6)).build()))
                .build();
    }

    /** A close button. */
    static ActionButton close() {
        return ActionButton.builder(styled("<b><color:#FF0000><shadow:#000000:1>\u00D7</shadow></color></b>"))
                .width(34)
                .action(DialogAction.customClick((response, audience) -> audience.closeDialog(),
                        ClickCallback.Options.builder().uses(64).build()))
                .build();
    }

    /** Bold orange heading used by every panel screen. */
    static Component heading(String text) {
        return styled("<gradient:#FFE3A8:#FFB01F:#FF7A00><shadow:#000000:1><bold>" + text);
    }

    /**
     * Renders MiniMessage, falling back to plain gold when a tag is unknown on the
     * running server, so a heading can never break a dialog.
     *
     * @param miniMessage markup
     * @return the component
     */
    static Component styled(String miniMessage) {
        try {
            return dev.bluelabs.honeycomb.util.Text.parse(miniMessage);
        } catch (RuntimeException unsupported) {
            return Component.text(miniMessage.replaceAll("<[^>]*>", ""), NamedTextColor.GOLD)
                    .decorate(TextDecoration.BOLD);
        }
    }

    /**
     * A shadowed gray line, used for the explanations under a screen.
     *
     * @param text plain text
     * @return the component
     */
    static Component shade(String text) {
        return styled("<shadow:#000000:1><dark_gray>" + text);
    }

    /** Coloured button label: navigation, save and close each read differently. */
    static Component label(String text, NamedTextColor colour) {
        return Component.text(text, colour).decoration(TextDecoration.BOLD, true);
    }

    static Component good(String text) {
        return Component.text(text, NamedTextColor.GREEN).decorate(TextDecoration.BOLD);
    }

    static Component warn(String text) {
        return Component.text(text, NamedTextColor.RED).decorate(TextDecoration.BOLD);
    }

    static void show(Player player, org.bukkit.plugin.Plugin plugin, Dialog dialog) {
        // Closing first is what makes a Back button reliable: the client refuses a new
        // dialog while an old one is still open, which is why it used to do nothing.
        try {
            player.closeDialog();
        } catch (RuntimeException ignored) {
            // Nothing is open, which is the normal case.
        }
        // Two ticks: the click callback that asked for this screen has to finish first,
        // or the client closes the new dialog along with the old one.
        dev.bluelabs.honeycomb.scheduler.Scheduling.forPlayerLater(plugin, player, 2L, () -> {
            try {
                player.showDialog(dialog);
            } catch (RuntimeException failure) {
                player.sendMessage(Component.text("Honeycomb: that screen could not open ("
                        + failure.getClass().getSimpleName() + ")", NamedTextColor.RED));
            }
        });
    }

    static Component hint(String text) {
        return styled("<shadow:#000000:1><dark_gray>" + MiniMessage.miniMessage().escapeTags(text));
    }

    /**
     * Builds a multi line text input.
     *
     * @param key      input name, lower case letters, digits and underscores
     * @param label    shown label
     * @param initial  starting text
     * @param rows     visible rows
     * @return the input
     */
    static DialogInput multiline(String key, String label, String initial, int rows) {
        return DialogInput.text(key, Component.text(label))
                .initial(initial == null ? "" : initial)
                .maxLength(16384)
                .width(320)
                .multiline(TextDialogInput.MultilineOptions.create(MAX_ROWS, height(rows)))
                .build();
    }
}
