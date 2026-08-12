package me.caitlyn.opensockets;

import li.cil.oc.api.Network;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import li.cil.oc.api.util.Lifecycle;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SocketEnvironment extends AbstractManagedEnvironment implements ManagedEnvironment, Lifecycle {
    private static final Logger LOGGER = LogManager.getLogger(OpenSockets.MOD_ID);
    private static final String STATE_TAG = "opensockets";
    private static final String LISTENERS_TAG = "listeners";
    private static final String ID_TAG = "id";
    private static final String PORT_TAG = "port";

    private final EnvironmentHost host;
    private final SocketManager manager;
    /**
     * Listener descriptors belong to the card's persisted state. The live
     * sockets must not be used as the source of truth because OC can remove
     * the component node from the network while AbstractManagedEnvironment is
     * saving it.
     */
    private final Map<Integer, Integer> persistedListeners = new LinkedHashMap<>();

    public SocketEnvironment(EnvironmentHost host) {
        this.host = host;
        manager = new SocketManager();
        setNode(Network.newNode(this, Visibility.Network)
                .withComponent("opensockets")
                .create());
    }

    @Callback(doc = "function(port:number):boolean, number, number, string -- Open or reuse a listener on this card. Port 0 selects an available configured port.")
    public Object[] listen(Context context, Arguments args) {
        int requestedPort = args.optInteger(0, 0);
        try {
            SocketManager.Listener listener = manager.listen(requestedPort);
            synchronized (persistedListeners) {
                persistedListeners.put(listener.id(), listener.port());
            }
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
        byte[] data = connection.read(Math.max(1, Math.min(args.optInteger(1, 8192), OpenSocketsConfig.MAX_BUFFERED_BYTES)));
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
        int handle = args.checkInteger(0);
        boolean closed = manager.close(handle);
        if (closed) {
            synchronized (persistedListeners) {
                persistedListeners.remove(handle);
            }
            host.markChanged();
        }
        return closed ? new Object[]{true} : new Object[]{false, "unknown handle"};
    }

    @Callback(doc = "function(listener:number):number -- Return the operating-system port for a listener.")
    public Object[] port(Context context, Arguments args) {
        SocketManager.Listener listener = manager.listener(args.checkInteger(0));
        return listener == null ? new Object[]{false, "unknown listener"} : new Object[]{listener.port()};
    }

    @Override
    public void onDisconnect(Node node) {
        if (node == node()) {
            // Close OS resources, but retain the descriptors for the next
            // save/reconnect. OC may call this during its save path.
            manager.closeAll();
        }
    }

    @Override
    public void onConnect(Node node) {
        if (node == node() && isServerSide()) {
            restorePersistedListeners();
        }
    }

    @Override
    public void onLifecycleStateChange(Lifecycle.LifecycleState state) {
        if (state == Lifecycle.LifecycleState.Disposing) {
            // Client-side OC disposal is not guaranteed to remove a node.
            // Always release OS resources when the component is unloaded.
            manager.closeAll();
        }
    }

    @Override
    public void load(NBTTagCompound nbt) {
        // Client-side OC can reload an existing component environment from a
        // chunk update without first disconnecting the old node. Release any
        // live OS sockets before opening the persisted listeners again.
        manager.closeAll();
        super.load(nbt);

        // OpenComputers creates a client mirror of item components as well as
        // the authoritative server component. Only the server may bind TCP
        // ports; the client mirror must retain no listener state and must not
        // attempt to restore the server's port in the same JVM.
        synchronized (persistedListeners) {
            persistedListeners.clear();
        }
        if (!isServerSide()) return;

        NBTTagCompound state = nbt.getCompoundTag(STATE_TAG);
        NBTTagList listeners = state.getTagList(LISTENERS_TAG, Constants.NBT.TAG_COMPOUND);
        synchronized (persistedListeners) {
            persistedListeners.clear();
        }
        for (int index = 0; index < listeners.tagCount(); index++) {
            NBTTagCompound listener = listeners.getCompoundTagAt(index);
            if (!listener.hasKey(ID_TAG, Constants.NBT.TAG_INT) || !listener.hasKey(PORT_TAG, Constants.NBT.TAG_INT)) continue;
            synchronized (persistedListeners) {
                persistedListeners.put(listener.getInteger(ID_TAG), listener.getInteger(PORT_TAG));
            }
        }
        restorePersistedListeners();
    }

    @Override
    public void save(NBTTagCompound nbt) {
        super.save(nbt);

        NBTTagList listeners = new NBTTagList();
        synchronized (persistedListeners) {
            for (Map.Entry<Integer, Integer> entry : persistedListeners.entrySet()) {
                NBTTagCompound listener = new NBTTagCompound();
                listener.setInteger(ID_TAG, entry.getKey());
                listener.setInteger(PORT_TAG, entry.getValue());
                listeners.appendTag(listener);
            }
        }

        if (listeners.tagCount() == 0) {
            nbt.removeTag(STATE_TAG);
        } else {
            NBTTagCompound state = new NBTTagCompound();
            state.setTag(LISTENERS_TAG, listeners);
            nbt.setTag(STATE_TAG, state);
        }
    }

    private void restorePersistedListeners() {
        if (!isServerSide()) return;

        Map<Integer, Integer> snapshot;
        synchronized (persistedListeners) {
            snapshot = new LinkedHashMap<>(persistedListeners);
        }

        for (Map.Entry<Integer, Integer> entry : snapshot.entrySet()) {
            if (manager.listener(entry.getKey()) != null) continue;
            try {
                manager.restoreListener(entry.getKey(), entry.getValue());
            } catch (IOException | IllegalArgumentException exception) {
                LOGGER.warn("Could not restore OpenSockets listener on port {}", entry.getValue(), exception);
            }
        }
    }

    private boolean isServerSide() {
        return host.world() != null && !host.world().isRemote;
    }
}
