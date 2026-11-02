package dev.bluelabs.honeycomb.network;

/**
 * Network wide player counts.
 *
 * <p>Only a real data source may answer these: on a standalone server
 * {@link #available()} is false and network placeholders stay unresolved rather
 * than reporting invented numbers.</p>
 */
public interface NetworkCounts {

    /**
     * @return true when a proxy or shared data source is connected
     */
    boolean available();

    /**
     * @return players on the whole network, or {@code -1} when unknown
     */
    int onlineEverywhere();

    /**
     * @param serverName backend server name
     * @return players on that server, or {@code -1} when unknown
     */
    int onlineOn(String serverName);

    /**
     * @return players on this server, falling back to the local count
     */
    int onlineHere();

    /**
     * @return the proxy name for diagnostics: None, BungeeCord or Velocity
     */
    String proxyName();

    /**
     * @return server names the proxy reported
     */
    java.util.List<String> servers();

    /**
     * @return the name this server is known by on the network
     */
    String serverName();

    /**
     * @return a short description of the data source for diagnostics
     */
    String sourceName();
}
