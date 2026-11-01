package dev.bluelabs.honeycomb.server;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.plugin.Plugin;

import java.util.function.Supplier;

/**
 * Rewrites the server brand packet while it is leaving the server.
 *
 * <p>Paper sends {@code minecraft:brand} during login, before any plugin has a player
 * to attach a handler to. ProtocolLib listens from the moment the plugin enables, so it
 * sees that very first packet. This is the only reliable way to change the F3 brand on
 * a running Paper server without patching the server jar.</p>
 */
public final class BrandHook {

    private final Plugin plugin;
    private boolean registered;
    private boolean warned;

    public BrandHook(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers the packet listener.
     *
     * @param brand supplies the text to show, read on every packet
     * @return true when the listener is active
     */
    public boolean register(Supplier<String> brand) {
        if (registered) {
            return true;
        }
        try {
            ProtocolLibrary.getProtocolManager().addPacketListener(
                    new PacketAdapter(plugin, ListenerPriority.HIGHEST, PacketType.Play.Server.CUSTOM_PAYLOAD) {
                        @Override
                        public void onPacketSending(PacketEvent event) {
                            rewrite(event, brand);
                        }
                    });
            registered = true;
            return true;
        } catch (Throwable throwable) {
            plugin.getLogger().warning("ProtocolLib is installed but its packet listener could not be registered: "
                    + throwable.getMessage());
            return false;
        }
    }

    private void rewrite(PacketEvent event, Supplier<String> brand) {
        try {
            PacketContainer packet = event.getPacket();
            String channel = packet.getStrings().read(0);
            if (channel == null || !channel.equalsIgnoreCase("minecraft:brand")) {
                return;
            }
            String text = brand.get();
            if (text == null || text.isBlank()) {
                return;
            }
            if (packet.getStrings().size() > 1) {
                packet.getStrings().write(1, text);
                return;
            }
            if (!warned) {
                warned = true;
                plugin.getLogger().warning("ProtocolLib did not expose a brand field on this server build; "
                        + "the F3 brand keeps the server default.");
            }
        } catch (Throwable ignored) {
            // A packet that does not parse is left untouched.
        }
    }
}
