package dev.bluelabs.honeycomb.text;

import dev.bluelabs.honeycomb.animation.AnimationRegistry;
import dev.bluelabs.honeycomb.placeholder.PlaceholderContext;
import dev.bluelabs.honeycomb.placeholder.PlaceholderService;
import dev.bluelabs.honeycomb.scheduler.TickClock;
import dev.bluelabs.honeycomb.util.Text;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a configuration template into the component a display needs.
 *
 * <p>The order is deliberate: animation frames are substituted first (a frame may
 * itself contain placeholders), then placeholders are expanded, then MiniMessage
 * is parsed. Animated templates bypass the placeholder cache because their text
 * changes every frame.</p>
 */
public final class RenderPipeline {

    /** {honeycomb.name} and {anim:name} are the same as <anim:name>. */
    private static final Pattern CURLY_ANIMATION =
            Pattern.compile("\\{(?:honeycomb\\.|anim:)([A-Za-z0-9_\\-]+)}");

    private static final Pattern ANIMATION_TOKEN = Pattern.compile("<anim:([A-Za-z0-9_\\-]+)>");

    private final PlaceholderService placeholders;
    private final AnimationRegistry animations;
    private final TickClock clock;

    /**
     * @param placeholders placeholder service
     * @param animations   animation registry
     * @param clock        shared tick clock, needed to pick animation frames
     */
    public RenderPipeline(PlaceholderService placeholders, AnimationRegistry animations, TickClock clock) {
        this.placeholders = placeholders;
        this.animations = animations;
        this.clock = clock;
    }

    /**
     * Substitutes animation frames and placeholders, returning MiniMessage source.
     *
     * @param template raw template
     * @param context  render context
     * @return the prepared string
     */
    public String prepare(String template, PlaceholderContext context) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        String withFrames = applyAnimations(template);
        boolean animated = withFrames.indexOf("<anim:") >= 0;
        return animated
                ? placeholders.expand(withFrames, context)
                : placeholders.expandCached(withFrames, context);
    }

    private String applyAnimations(String template) {
        String source = template;
        if (source.indexOf('{') >= 0) {
            source = CURLY_ANIMATION.matcher(source).replaceAll("<anim:$1>");
        }
        if (source.indexOf("<anim:") < 0) {
            return source;
        }
        Matcher matcher = ANIMATION_TOKEN.matcher(source);
        StringBuilder output = new StringBuilder(source.length() + 32);
        while (matcher.find()) {
            String id = matcher.group(1);
            String frame = animations.frameAt(id, clock.tick());
            // An unknown animation renders as nothing rather than as a stray tag.
            matcher.appendReplacement(output, Matcher.quoteReplacement(frame == null ? "" : frame));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    /**
     * Renders a single template.
     *
     * @param template raw template
     * @param context  render context
     * @return the rendered component
     */
    public Component render(String template, PlaceholderContext context) {
        return Text.parse(prepare(template, context));
    }

    /**
     * Renders a list of template lines.
     *
     * @param templates raw templates
     * @param context   render context
     * @return rendered components, one per line
     */
    public List<Component> renderLines(List<String> templates, PlaceholderContext context) {
        List<Component> lines = new ArrayList<>(templates.size());
        for (String template : templates) {
            lines.add(render(template, context));
        }
        return lines;
    }

    /**
     * Joins rendered lines into one component, keeping the newlines so multi-line
     * displays such as the tablist header stay readable in configuration.
     *
     * @param templates raw templates
     * @param context   render context
     * @return the joined component
     */
    public Component renderMultiline(List<String> templates, PlaceholderContext context) {
        Component result = Component.empty();
        for (int index = 0; index < templates.size(); index++) {
            if (index > 0) {
                result = result.append(Component.newline());
            }
            result = result.append(render(templates.get(index), context));
        }
        return result;
    }

    /**
     * @return the placeholder service, for diagnostics and the API
     */
    public PlaceholderService placeholders() {
        return placeholders;
    }
}
