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

    /** A one line text field. */
    static TextDialogInput text(String key, String label, String initial) {
        return DialogInput.text(key, Component.text(label))
                .initial(initial == null ? "" : initial)
                .maxLength(1024)
                .width(260)
                .build();
    }

    /** A multi line text field, used for lists such as scoreboard lines. */
    static TextDialogInput lines(String key, String label, String initial, int rows) {
        return DialogInput.text(key, Component.text(label))
                .initial(initial == null ? "" : initial)
                .maxLength(4096)
                .width(320)
                .multiline(TextDialogInput.MultilineOptions.create(rows, 255))
                .build();
    }

    /** A checkbox. */
    static DialogInput toggle(String key, String label, boolean initial) {
        return DialogInput.bool(key, Component.text(label), initial, "on", "off");
    }

    /** A button that runs a Honeycomb command as the player who clicked it. */
    static ActionButton run(String label, String command) {
        return ActionButton.builder(Component.text(label, NamedTextColor.AQUA))
                .width(150)
                .action(DialogAction.staticAction(ClickEvent.runCommand("/" + command)))
                .build();
    }

    /** A button that hands the click back to the server. */
    static ActionButton act(String label, Consumer<Audience> handler) {
        return ActionButton.builder(Component.text(label, NamedTextColor.AQUA))
                .width(150)
                .action(DialogAction.staticAction(ClickEvent.callback((Audience audience) -> handler.accept(audience))))
                .build();
    }

    /** A button that receives the values typed into the dialog's inputs. */
    static ActionButton save(String label, BiConsumer<org.bukkit.entity.Player, DialogResponseView> handler) {
        return ActionButton.builder(Component.text(label, NamedTextColor.AQUA))
                .width(150)
                .action(DialogAction.customClick((response, audience) -> {
                    if (audience instanceof org.bukkit.entity.Player player) {
                        handler.accept(player, response);
                    }
                }, ClickCallback.Options.builder().uses(3).lifetime(Duration.ofMinutes(30)).build()))
                .build();
    }

    /** A close button. */
    static ActionButton close() {
        return act("Close", Audience::closeDialog);
    }

    /** Bold orange heading used by every panel screen. */
    static Component heading(String text) {
        return Component.text(text, NamedTextColor.GOLD).decorate(TextDecoration.BOLD);
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

    static Component hint(String text) {
        return Component.text(text, NamedTextColor.DARK_GRAY);
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
                .maxLength(2048)
                .width(320)
                .multiline(TextDialogInput.MultilineOptions.create(rows, 120))
                .build();
    }
}
