package me.caitlyn.opensockets;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class OpenSocketsConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue PORT_START;
    public static final ModConfigSpec.IntValue PORT_END;
    public static final ModConfigSpec.ConfigValue<String> BIND_ADDRESS;
    public static final ModConfigSpec.IntValue MAX_LISTENERS_PER_CARD;
    public static final ModConfigSpec.IntValue MAX_CONNECTIONS_PER_LISTENER;
    public static final ModConfigSpec.IntValue MAX_BUFFERED_BYTES;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("OpenSockets server-side TCP listener settings.",
                "Set bindAddress to 0.0.0.0 only when you want listeners reachable outside the host.").push("sockets");

        PORT_START = builder
                .comment("First port that OpenComputers programs may bind.")
                .defineInRange("portStart", 28000, 1, 65535);
        PORT_END = builder
                .comment("Last port that OpenComputers programs may bind.")
                .defineInRange("portEnd", 28099, 1, 65535);
        BIND_ADDRESS = builder
                .comment("Local address used for listeners. 127.0.0.1 is localhost-only; 0.0.0.0 exposes listeners on all interfaces.")
                .define("bindAddress", "127.0.0.1");
        MAX_LISTENERS_PER_CARD = builder
                .comment("Maximum simultaneous listening sockets per socket card.")
                .defineInRange("maxListenersPerCard", 4, 1, 64);
        MAX_CONNECTIONS_PER_LISTENER = builder
                .comment("Maximum queued or open connections for one listening socket.")
                .defineInRange("maxConnectionsPerListener", 16, 1, 256);
        MAX_BUFFERED_BYTES = builder
                .comment("Maximum queued bytes in either direction for one connection.")
                .defineInRange("maxBufferedBytes", 1048576, 4096, 16777216);

        builder.pop();
        SPEC = builder.build();
    }

    private OpenSocketsConfig() {}
}
