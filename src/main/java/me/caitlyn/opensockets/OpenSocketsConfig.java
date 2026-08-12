package me.caitlyn.opensockets;

import net.minecraftforge.common.config.Configuration;

import java.io.File;

public final class OpenSocketsConfig {
    private static final String CATEGORY = "sockets";

    private static Configuration configuration;

    public static volatile int PORT_START = 28000;
    public static volatile int PORT_END = 28099;
    public static volatile String BIND_ADDRESS = "127.0.0.1";
    public static volatile int MAX_LISTENERS_PER_CARD = 4;
    public static volatile int MAX_CONNECTIONS_PER_LISTENER = 16;
    public static volatile int MAX_BUFFERED_BYTES = 1048576;

    public static void load(File file) {
        configuration = new Configuration(file);
        sync();
    }

    private static void sync() {
        if (configuration == null) return;

        PORT_START = configuration.getInt("portStart", CATEGORY, PORT_START,
                1, 65535, "First port that OpenComputers programs may bind.");
        PORT_END = configuration.getInt("portEnd", CATEGORY, PORT_END,
                1, 65535, "Last port that OpenComputers programs may bind.");
        BIND_ADDRESS = configuration.getString("bindAddress", CATEGORY, BIND_ADDRESS,
                "Local address used for listeners. 127.0.0.1 is localhost-only; 0.0.0.0 exposes listeners on all interfaces.");
        MAX_LISTENERS_PER_CARD = configuration.getInt("maxListenersPerCard", CATEGORY, MAX_LISTENERS_PER_CARD,
                1, 64, "Maximum simultaneous listening sockets per socket card.");
        MAX_CONNECTIONS_PER_LISTENER = configuration.getInt("maxConnectionsPerListener", CATEGORY, MAX_CONNECTIONS_PER_LISTENER,
                1, 256, "Maximum queued or open connections for one listening socket.");
        MAX_BUFFERED_BYTES = configuration.getInt("maxBufferedBytes", CATEGORY, MAX_BUFFERED_BYTES,
                4096, 16777216, "Maximum queued bytes in either direction for one connection.");

        if (PORT_START > PORT_END) {
            PORT_END = PORT_START;
        }

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }

    private OpenSocketsConfig() {
    }
}
