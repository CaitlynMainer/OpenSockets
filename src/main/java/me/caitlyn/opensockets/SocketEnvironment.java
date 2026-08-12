package me.caitlyn.opensockets;

import li.cil.oc.api.Network;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import li.cil.oc.api.UnrecoverablePersistanceException;
import net.minecraft.core.component.DataComponentHolder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.common.MutableDataComponentHolder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.List;

public final class SocketEnvironment extends AbstractManagedEnvironment implements ManagedEnvironment {
    private static final Logger LOGGER = LogManager.getLogger(OpenSockets.MOD_ID);
    private static final String STATE_TAG = "opensockets";
    private static final String LISTENERS_TAG = "listeners";
    private static final String ID_TAG = "id";
    private static final String PORT_TAG = "port";

    private final EnvironmentHost host;
    private final SocketManager manager;

    public SocketEnvironment(EnvironmentHost host) {
        this.host = host;
        manager = new SocketManager();
        setNode(Network.newNode(this, Visibility.Network)
                .withComponent("opensockets")
                .create());
    }

    @Callback(doc = "function(port:number):boolean, number, number, string -- Open a listener. Port 0 selects an available configured port.")
    public Object[] listen(Context context, Arguments args) {
        int requestedPort = args.optInteger(0, 0);
        try {
            SocketManager.Listener listener = manager.listen(requestedPort);
            host.markChanged();
            return new Object[]{true, listener.id(), listener.port()};
        } catch (IOException | IllegalArgumentException exception) {
            return new Object[]{false, exception.getMessage() == null ? "unable to listen" : exception.getMessage()};
        }
    }

    @Callback(doc = "function(listener:number):number, string, number -- Accept a pending connection without blocking.")
    public Object[] accept(Context context, Arguments args) {
        SocketManager.Listener listener = manager.listener(args.checkInteger(0));
        if (listener == null) return new Object[]{false, "unknown listener"};
        SocketManager.Connection connection = listener.pollConnection();
        if (connection == null) return new Object[]{};
        return new Object[]{connection.id(), connection.remoteAddress(), connection.remotePort()};
    }

    @Callback(doc = "function(connection:number, amount:number):string -- Read buffered bytes without blocking.")
    public Object[] read(Context context, Arguments args) {
        SocketManager.Connection connection = manager.connection(args.checkInteger(0));
        if (connection == null) return new Object[]{false, "unknown connection"};
        byte[] data = connection.read(Math.max(1, Math.min(args.optInteger(1, 8192), OpenSocketsConfig.MAX_BUFFERED_BYTES.get())));
        return data == null ? new Object[]{} : new Object[]{data};
    }

    @Callback(doc = "function(connection:number, data:string):boolean, string -- Queue data for transmission.")
    public Object[] write(Context context, Arguments args) {
        SocketManager.Connection connection = manager.connection(args.checkInteger(0));
        if (connection == null) return new Object[]{false, "unknown connection"};
        byte[] data = args.checkByteArray(1);
        return connection.write(data) ? new Object[]{true} : new Object[]{false, "connection is closed or its output buffer is full"};
    }

    @Callback(doc = "function(handle:number):boolean, string -- Close a listener or connection.")
    public Object[] close(Context context, Arguments args) {
        boolean closed = manager.close(args.checkInteger(0));
        if (closed) host.markChanged();
        return closed ? new Object[]{true} : new Object[]{false, "unknown handle"};
    }

    @Callback(doc = "function(listener:number):number -- Return the operating-system port for a listener.")
    public Object[] port(Context context, Arguments args) {
        SocketManager.Listener listener = manager.listener(args.checkInteger(0));
        return listener == null ? new Object[]{false, "unknown listener"} : new Object[]{listener.port()};
    }

    @Override
    public void onDisconnect(Node node) {
        if (node == node()) manager.closeAll();
    }

    @Override
    public void loadData(DataComponentHolder holder) throws UnrecoverablePersistanceException {
        super.loadData(holder);

        CustomData customData = holder.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return;

        CompoundTag state = customData.copyTag().getCompound(STATE_TAG);
        ListTag listeners = state.getList(LISTENERS_TAG, Tag.TAG_COMPOUND);
        for (int index = 0; index < listeners.size(); index++) {
            CompoundTag listener = listeners.getCompound(index);
            if (!listener.contains(ID_TAG, Tag.TAG_INT) || !listener.contains(PORT_TAG, Tag.TAG_INT)) continue;
            try {
                manager.restoreListener(listener.getInt(ID_TAG), listener.getInt(PORT_TAG));
            } catch (IOException | IllegalArgumentException exception) {
                LOGGER.warn("Could not restore OpenSockets listener on port {}", listener.getInt(PORT_TAG), exception);
            }
        }
    }

    @Override
    public void saveData(MutableDataComponentHolder holder) {
        super.saveData(holder);

        CompoundTag customTag = holder.get(DataComponents.CUSTOM_DATA) == null
                ? new CompoundTag()
                : holder.get(DataComponents.CUSTOM_DATA).copyTag();
        ListTag listeners = new ListTag();
        List<SocketManager.ListenerState> states = manager.listenerStates();
        for (SocketManager.ListenerState state : states) {
            CompoundTag listener = new CompoundTag();
            listener.putInt(ID_TAG, state.id());
            listener.putInt(PORT_TAG, state.port());
            listeners.add(listener);
        }

        if (listeners.isEmpty()) {
            customTag.remove(STATE_TAG);
        } else {
            CompoundTag state = new CompoundTag();
            state.put(LISTENERS_TAG, listeners);
            customTag.put(STATE_TAG, state);
        }

        if (customTag.isEmpty()) {
            holder.remove(DataComponents.CUSTOM_DATA);
        } else {
            holder.set(DataComponents.CUSTOM_DATA, CustomData.of(customTag));
        }
    }
}
