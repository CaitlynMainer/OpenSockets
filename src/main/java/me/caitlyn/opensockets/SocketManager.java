package me.caitlyn.opensockets;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class SocketManager {
    private static final Set<SocketManager> INSTANCES = ConcurrentHashMap.newKeySet();
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(new SocketThreadFactory());

    private final Map<Integer, Listener> listeners = new ConcurrentHashMap<>();
    private final Map<Integer, Connection> connections = new ConcurrentHashMap<>();
    private final AtomicInteger nextHandle = new AtomicInteger(1);

    public SocketManager() {
        INSTANCES.add(this);
    }

    public Listener listen(int requestedPort) throws IOException {
        if (requestedPort != 0) {
            Listener existing = listenerOnPort(requestedPort);
            if (existing != null) return existing;
        }
        return openListener(requestedPort, nextHandle.getAndIncrement());
    }

    public Listener restoreListener(int id, int port) throws IOException {
        if (id < 1) throw new IllegalArgumentException("invalid listener handle");
        if (listeners.containsKey(id)) throw new IllegalArgumentException("listener handle already exists");
        nextHandle.updateAndGet(next -> Math.max(next, id + 1));
        return openListener(port, id);
    }

    public List<ListenerState> listenerStates() {
        List<ListenerState> result = new ArrayList<>(listeners.size());
        listeners.values().forEach(listener -> result.add(new ListenerState(listener.id(), listener.port())));
        return result;
    }

    private Listener openListener(int requestedPort, int handle) throws IOException {
        int start = OpenSocketsConfig.PORT_START;
        int end = OpenSocketsConfig.PORT_END;
        if (start > end) throw new IllegalArgumentException("invalid configured port range");
        if (requestedPort != 0 && (requestedPort < start || requestedPort > end)) {
            throw new IllegalArgumentException("port is outside the configured range");
        }
        if (listeners.size() >= OpenSocketsConfig.MAX_LISTENERS_PER_CARD) {
            throw new IllegalArgumentException("listener limit reached");
        }

        IOException lastFailure = null;
        int first = requestedPort == 0 ? start : requestedPort;
        int last = requestedPort == 0 ? end : requestedPort;
        for (int port = first; port <= last; port++) {
            ServerSocket server = new ServerSocket();
            try {
                server.setReuseAddress(false);
                server.bind(bindAddress(port), OpenSocketsConfig.MAX_CONNECTIONS_PER_LISTENER);
                Listener listener = new Listener(handle, server, this);
                listeners.put(listener.id(), listener);
                EXECUTOR.execute(listener::acceptLoop);
                return listener;
            } catch (IOException exception) {
                lastFailure = exception;
                try {
                    server.close();
                } catch (IOException ignored) {
                }
            }
        }
        throw lastFailure == null ? new IOException("no port available") : lastFailure;
    }

    public static final class ListenerState {
        private final int id;
        private final int port;

        public ListenerState(int id, int port) {
            this.id = id;
            this.port = port;
        }

        public int id() {
            return id;
        }

        public int port() {
            return port;
        }
    }

    private static SocketAddress bindAddress(int port) {
        String address = OpenSocketsConfig.BIND_ADDRESS;
        return new InetSocketAddress(address == null || address.trim().isEmpty() ? "0.0.0.0" : address, port);
    }

    private Connection addConnection(Socket socket) throws IOException {
        Connection connection = new Connection(nextHandle.getAndIncrement(), socket);
        connections.put(connection.id(), connection);
        EXECUTOR.execute(connection::readLoop);
        EXECUTOR.execute(connection::writeLoop);
        return connection;
    }

    private void removeConnection(Connection connection) {
        connections.remove(connection.id(), connection);
    }

    private void closeListener(Listener listener) {
        if (!listeners.remove(listener.id(), listener)) return;
        listener.close();
    }

    public Listener listener(int id) {
        return listeners.get(id);
    }

    private Listener listenerOnPort(int port) {
        for (Listener listener : listeners.values()) {
            if (listener.port() == port) return listener;
        }
        return null;
    }

    public Connection connection(int id) {
        return connections.get(id);
    }

    public boolean close(int id) {
        Listener listener = listeners.get(id);
        if (listener != null) {
            closeListener(listener);
            return true;
        }
        Connection connection = connections.get(id);
        if (connection != null) {
            connection.close();
            return true;
        }
        return false;
    }

    public void closeAll() {
        listeners.values().forEach(this::closeListener);
        connections.values().forEach(Connection::close);
    }

    public static void shutdown() {
        INSTANCES.forEach(SocketManager::closeAll);
        INSTANCES.clear();
    }

    public final class Listener {
        private final int id;
        private final ServerSocket server;
        private final SocketManager owner;
        private final BlockingQueue<Connection> pending;
        private final AtomicBoolean open = new AtomicBoolean(true);

        private Listener(int id, ServerSocket server, SocketManager owner) {
            this.id = id;
            this.server = server;
            this.owner = owner;
            this.pending = new LinkedBlockingQueue<>(OpenSocketsConfig.MAX_CONNECTIONS_PER_LISTENER);
        }

        public int id() {
            return id;
        }

        public int port() {
            return server.getLocalPort();
        }

        private void acceptLoop() {
            while (open.get()) {
                try {
                    Socket socket = server.accept();
                    socket.setTcpNoDelay(true);
                    if (pending.remainingCapacity() == 0) {
                        socket.close();
                        continue;
                    }
                    Connection connection = owner.addConnection(socket);
                    if (!pending.offer(connection)) connection.close();
                } catch (IOException exception) {
                    if (open.get()) closeListener(this);
                    return;
                }
            }
        }

        Connection pollConnection() {
            return pending.poll();
        }

        private void close() {
            if (!open.compareAndSet(true, false)) return;
            try {
                server.close();
            } catch (IOException ignored) {
            }
            Connection connection;
            while ((connection = pending.poll()) != null) connection.close();
        }
    }

    public final class Connection {
        private static final int CHUNK_SIZE = 8192;

        private final int id;
        private final Socket socket;
        private final BlockingQueue<byte[]> input = new LinkedBlockingQueue<>();
        private final BlockingQueue<byte[]> output = new LinkedBlockingQueue<>();
        private final ArrayDeque<byte[]> partialInput = new ArrayDeque<>();
        private final AtomicInteger bufferedInput = new AtomicInteger();
        private final AtomicInteger bufferedOutput = new AtomicInteger();
        private final AtomicBoolean open = new AtomicBoolean(true);

        private Connection(int id, Socket socket) throws IOException {
            this.id = id;
            this.socket = socket;
            socket.setSoTimeout(0);
        }

        public int id() {
            return id;
        }

        public String remoteAddress() {
            return socket.getInetAddress().getHostAddress();
        }

        public int remotePort() {
            return socket.getPort();
        }

        private void readLoop() {
            try (InputStream stream = socket.getInputStream()) {
                byte[] buffer = new byte[CHUNK_SIZE];
                int count;
                while (open.get() && (count = stream.read(buffer)) != -1) {
                    byte[] data = java.util.Arrays.copyOf(buffer, count);
                    int total = bufferedInput.addAndGet(count);
                    if (total > OpenSocketsConfig.MAX_BUFFERED_BYTES || !input.offer(data)) {
                        close();
                        return;
                    }
                }
            } catch (IOException ignored) {
            } finally {
                close();
            }
        }

        private void writeLoop() {
            try (OutputStream stream = socket.getOutputStream()) {
                while (open.get() || !output.isEmpty()) {
                    byte[] data = output.poll(250, TimeUnit.MILLISECONDS);
                    if (data == null) continue;
                    bufferedOutput.addAndGet(-data.length);
                    stream.write(data);
                    stream.flush();
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
            } finally {
                close();
            }
        }

        public byte[] read(int maximum) {
            if (!partialInput.isEmpty() || !input.isEmpty()) {
                ByteArrayOutputStream result = new ByteArrayOutputStream(Math.min(maximum, CHUNK_SIZE));
                while (result.size() < maximum) {
                    byte[] data = partialInput.pollFirst();
                    if (data == null) data = input.poll();
                    if (data == null) break;
                    int amount = Math.min(data.length, maximum - result.size());
                    result.write(data, 0, amount);
                    bufferedInput.addAndGet(-amount);
                    if (amount < data.length) partialInput.addFirst(java.util.Arrays.copyOfRange(data, amount, data.length));
                }
                return result.toByteArray();
            }
            return null;
        }

        public boolean write(byte[] data) {
            if (!open.get() || data.length > OpenSocketsConfig.MAX_BUFFERED_BYTES) return false;
            int total = bufferedOutput.addAndGet(data.length);
            if (total > OpenSocketsConfig.MAX_BUFFERED_BYTES || !output.offer(data.clone())) {
                bufferedOutput.addAndGet(-data.length);
                return false;
            }
            return true;
        }

        public void close() {
            if (!open.compareAndSet(true, false)) return;
            closeSocketOnly();
            removeConnection(this);
        }

        private void closeSocketOnly() {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static final class SocketThreadFactory implements ThreadFactory {
        private final AtomicInteger sequence = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "opensockets-io-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
