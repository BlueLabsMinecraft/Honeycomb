package dev.bluelabs.honeycomb.server;

import dev.bluelabs.honeycomb.HoneycombPlugin;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Changes the server brand shown in the debug screen (F3).
 *
 * <p>The brand line is a {@code minecraft:brand} payload the server sends after login,
 * and Paper fills it with its own name. There is no API to change it, so the outgoing
 * channel is given a handler that swaps that one packet for ours. Everything runs
 * through reflection because the packet classes are not part of the API, and every
 * step fails quietly when a future server build reshapes them.</p>
 */
public final class BrandService implements Listener {

    /**
     * @return true when ProtocolLib is installed, which is what makes the brand change
     */
    public static boolean protocolLibPresent() {
        return org.bukkit.Bukkit.getPluginManager().getPlugin("ProtocolLib") != null;
    }


    /** Created only when ProtocolLib is there, so its classes never load otherwise. */
    private BrandHook hook() {
        if (brandHook == null) {
            brandHook = new BrandHook(plugin);
        }
        return brandHook;
    }

    private static final String HANDLER = "honeycomb_brand";

    private final HoneycombPlugin plugin;
    private final dev.bluelabs.honeycomb.text.RenderPipeline pipeline;
    private volatile String template = "";
    private volatile String brand = "";
    private BrandHook brandHook;

    public BrandService(HoneycombPlugin plugin, dev.bluelabs.honeycomb.text.RenderPipeline pipeline) {
        this.plugin = plugin;
        this.pipeline = pipeline;
    }

    /**
     * @return how often the line is resent so animations move, in ticks
     */
    public int refreshTicks() {
        return Math.max(0, plugin.configManager()
                .yaml(dev.bluelabs.honeycomb.config.ConfigType.CONFIG)
                .getInt("brand.refresh-ticks", 40));
    }

    /**
     * Applies the configured brand to every player online now and to anyone who
     * joins later. Passing a blank name clears the override.
     *
     * @param enabled whether the brand override is on
     * @param text    brand text, MiniMessage or legacy {@code &} codes
     */
    public void apply(boolean enabled, String text) {
        template = enabled ? (text == null ? "" : text) : "";
        String wanted = enabled ? translate(render(template, null)) : "";
        if (wanted.equals(brand)) {
            return;
        }
        brand = wanted;
        if (wanted.isEmpty()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            inject(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (brand.isEmpty()) {
            return;
        }
        // The brand packet is sent as the player finishes logging in, so the handler
        // has to be in the channel before that happens.
        dev.bluelabs.honeycomb.scheduler.Scheduling.later(plugin, 1L, () -> inject(event.getPlayer()));
    }

    /**
     * Puts the rewriting handler into a player's outgoing pipeline.
     *
     * @param player player to patch
     */
    public void inject(Player player) {
        if (brand.isEmpty()) {
            return;
        }
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object listener = field(handle, "connection");
            Object connection = listener == null ? null : field(listener, "connection");
            Object channel = connection == null ? null : field(connection, "channel");
            if (!(channel instanceof io.netty.channel.Channel netty)) {
                plugin.getLogger().warning("The F3 brand could not be set: the packet channel was not reachable.");
                return;
            }
            Object packet = new BrandHandler().brandPacket(translate(render(template, player)));
            if (netty.pipeline().get(HANDLER) == null) {
                netty.pipeline().addBefore("packet_handler", HANDLER, new BrandHandler());
            }
            if (packet != null) {
                // Login already sent the server brand, so push ours after it.
                netty.writeAndFlush(packet);
            }
        } catch (Throwable failure) {
            plugin.getLogger().warning("The F3 brand could not be set on this server build: " + failure);
        }
    }

    /**
     * Renders the brand template for one player.
     *
     * <p>Placeholders and animations are expanded per player, so a line can greet the
     * person looking at it and can move. A template that fails to render is sent as
     * written rather than dropped.</p>
     *
     * @param text   template
     * @param player player, or {@code null} for the global fallback
     * @return rendered text
     */
    private String render(String text, Player player) {
        if (text == null || text.isBlank() || pipeline == null) {
            return text == null ? "" : text;
        }
        try {
            return pipeline.prepare(text,
                    dev.bluelabs.honeycomb.placeholder.PlaceholderContext.of(player));
        } catch (RuntimeException failure) {
            return text;
        }
    }

    private static Object field(Object target, String name) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        return null;
    }

    /**
     * Replaces the outgoing brand payload with the configured name.
     */
    private final class BrandHandler extends ChannelDuplexHandler {

        @Override
        public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) throws Exception {
            String current = brand;
            if (current.isEmpty() || !isBrandPacket(message)) {
                super.write(context, message, promise);
                return;
            }
            Object replacement = brandPacket(current);
            super.write(context, replacement == null ? message : replacement, promise);
        }

        private boolean isBrandPacket(Object message) {
            if (!message.getClass().getSimpleName().equals("ClientboundCustomPayloadPacket")) {
                return false;
            }
            try {
                Method payload = message.getClass().getMethod("payload");
                Object payloadValue = payload.invoke(message);
                if (payloadValue == null) {
                    return false;
                }
                if (payloadValue.getClass().getSimpleName().equals("BrandPayload")) {
                    return true;
                }
                Method type = payloadValue.getClass().getMethod("type");
                Object id = type.invoke(payloadValue);
                return String.valueOf(id).contains("brand");
            } catch (Throwable ignored) {
                return false;
            }
        }

        Object brandPacket(String text) {
            try {
                Class<?> payloadClass = Class.forName("net.minecraft.network.protocol.common.custom.BrandPayload");
                Object payload = payloadClass.getConstructor(String.class).newInstance(text);
                Class<?> payloadType = Class.forName("net.minecraft.network.protocol.common.custom.CustomPacketPayload");
                Class<?> packetClass = Class.forName("net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket");
                return packetClass.getConstructor(payloadType).newInstance(payload);
            } catch (Throwable failure) {
                return null;
            }
        }
    }

    /** Matches {@code &#RRGGBB}. */
    private static final java.util.regex.Pattern HEX_CODE =
            java.util.regex.Pattern.compile("(?i)&#([0-9a-f]{6})");

    private static final String LEGACY_CHARS = "0123456789abcdefklmnorABCDEFKLMNOR";

    /**
     * Turns MiniMessage and legacy {@code &} codes into the section codes the brand
     * payload carries. Text that fails to parse is sent as typed rather than dropped.
     *
     * @param input raw text from the panel or config
     * @return text ready for the packet
     */
    static String translate(String input) {
        String text = input == null ? "" : input.trim();
        if (text.indexOf('<') >= 0) {
            try {
                text = LegacyComponentSerializer.legacySection()
                        .serialize(MiniMessage.miniMessage().deserialize(text));
            } catch (RuntimeException ignored) {
                // not MiniMessage, keep it as typed
            }
        }
        java.util.regex.Matcher hex = HEX_CODE.matcher(text);
        StringBuilder buffer = new StringBuilder();
        while (hex.find()) {
            StringBuilder code = new StringBuilder("§x");
            for (char digit : hex.group(1).toCharArray()) {
                code.append('§').append(digit);
            }
            hex.appendReplacement(buffer, java.util.regex.Matcher.quoteReplacement(code.toString()));
        }
        hex.appendTail(buffer);
        text = buffer.toString();

        StringBuilder result = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '&' && i + 1 < text.length() && LEGACY_CHARS.indexOf(text.charAt(i + 1)) >= 0) {
                result.append('§').append(Character.toLowerCase(text.charAt(i + 1)));
                i++;
                continue;
            }
            result.append(c);
        }
        return result.toString();
    }
}
