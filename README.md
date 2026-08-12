# OpenSockets

OpenSockets adds server-side TCP listening sockets to OpenComputers: Rebooted.
OpenComputers is a required dependency; this project targets Minecraft 1.21.1 on
NeoForge and the OpenComputers 1.9.4-2 API.

## IntelliJ IDEA

Open the repository directory in IntelliJ IDEA and import the Gradle project with
a Java 21 SDK. The checked-in run configurations are:

- `Launch NeoForge Client`
- `Launch NeoForge Server`

They invoke the Gradle `runClient` and `runServer` tasks respectively.

If the OpenComputers Maven host is unavailable but its artifacts are already
cached locally, the tasks can be run with local jar overrides:

```powershell
.\gradlew.bat runClient `
  "--project-prop=opensockets_oc_api=build/opencomputers-api.jar" `
  "--project-prop=opensockets_oc_runtime=build/opencomputers-runtime.jar" `
  "--project-prop=opensockets_scala_runtime=build/scalablecatsforce-runtime.jar"
```

These override files are development fallbacks only and are ignored with the
rest of `build/`; OpenSockets does not bundle OpenComputers or its language
provider.

## Socket Card

Craft and install the Socket Card in an OpenComputers computer's card slot. It is
also compatible with microcontroller card slots. The host exposes a component
named `opensockets`:

```lua
local socket = require("component").opensockets
local ok, listener, port = socket.listen(0)
assert(ok, listener)
print("listening on " .. port)
```

The component methods are:

| Method | Purpose |
| --- | --- |
| `listen(port)` | Open a listener. `0` chooses a free port from the configured range. |
| `accept(listener)` | Return a pending connection, or no result when none is ready. |
| `read(connection, amount)` | Return buffered bytes without blocking. |
| `write(connection, data)` | Queue bytes for transmission. |
| `close(handle)` | Close a listener or connection. |
| `port(listener)` | Return the operating-system port for a listener. |

The example floppy installs its programs under `/home/socketexamples`. The HTTP
example serves the bundled sample page from `/home/socketexamples/www` on port
`28001`; FTP uses port `28002`.

The mod also registers an `OpenSockets Examples` loot floppy under the
OpenComputers tab. Restart the server after installing the mod (or run
`/reload`), put the floppy in a disk drive, and run the test program from its
mounted path. With current datapack loot support, use:

```lua
lua /mnt/<disk-address>/home/socketexamples/bin/socket-test.lua
```

After installing the floppy, the equivalent paths are
`/home/socketexamples/bin/httpd.lua` and `/home/socketexamples/bin/ftpd.lua`.
The Socket Card still goes in a computer or microcontroller's card slot; the
floppy is only for the example programs.

## Server configuration

The server config is `config/opensockets-server.toml`:

```toml
[sockets]
portStart = 28000
portEnd = 28099
bindAddress = "127.0.0.1"
maxListenersPerCard = 4
maxConnectionsPerListener = 16
maxBufferedBytes = 1048576
```

The default bind address is localhost. Set it to `0.0.0.0` only when listeners
should be reachable from outside the Minecraft host; the host firewall and NAT
configuration still apply.

Listener handles and their bound ports are saved with the Socket Card and
reopened when the card is loaded again. Existing TCP connections are closed when
the card disconnects, the computer disconnects, or the server stops; clients must
reconnect after a server restart.
