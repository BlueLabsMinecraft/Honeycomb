package dev.bluelabs.honeycomb.network;

import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

/**
 * Receives proxy payloads and forwards them to {@link NetworkService}.
 */
public final class ProxyMessageListener implements PluginMessageListener {

    private final NetworkService networkService;

    /**
     * @param networkService the service that owns the state
     */
    public ProxyMessageListener(NetworkService networkService) {
        this.networkService = networkService;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        networkService.handle(channel, message);
    }
}
