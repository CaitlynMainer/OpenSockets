# OpenSockets

OpenSockets adds server-side TCP listening sockets to computers and
microcontrollers running the original OpenComputers on Minecraft 1.12.2.

This branch targets Forge 14.23.5.2860 and the original OpenComputers 1.7.7
release (`OpenComputers-MC1.12.2-1.7.7+5413028`). OpenComputers is a required
runtime dependency.

## Building

ForgeGradle for this legacy toolchain must run on Java 8:

```bat
build.bat
```

The script selects the configured Java 8 installation and writes the jar to
`build/libs/`. The equivalent manual command is:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-8.0.422.5-hotspot'
.\gradlew.bat clean build --console=plain
```

The normal build resolves OpenComputers from Curse Maven. For an offline or
locally built OC jar, override the dependency with:

```powershell
.\gradlew.bat build `
  "--project-prop=opensockets_oc_jar=C:\path\to\OpenComputers-MC1.12.2-1.7.7+5413028.jar"
```

Use `runClient` or `runServer` for Forge development runs. A dedicated server
also needs the original OpenComputers jar in its `mods` directory.

## Socket Card

Craft and install the Socket Card in an OpenComputers computer's card slot. It
also works in a microcontroller card slot. The host exposes a component named
`opensockets`:

```lua
local socket = require("component").opensockets
local ok, listener, port = socket.listen(0)
assert(ok, listener)
print("listening on " .. port)
```

The component methods are:

| Method | Purpose |
| --- | --- |
| `listen(port)` | Open or reuse this card's listener on `port`. `0` chooses a free port from the configured range. |
| `accept(listener)` | Return a pending connection, or no result when none is ready. |
| `read(connection, amount)` | Return buffered bytes without blocking. |
| `write(connection, data)` | Queue bytes for transmission. |
| `close(handle)` | Close a listener or connection. |
| `port(listener)` | Return the operating-system port for a listener. |

The registered `OpenSockets Examples` floppy contains programs under
`/home/socketexamples`, including the HTTP example on port `28001` and FTP on
port `28002`.

## Server configuration

Forge writes the configuration to `config/opensockets.cfg`:

```text
[sockets]
portStart=28000
portEnd=28099
bindAddress=127.0.0.1
maxListenersPerCard=4
maxConnectionsPerListener=16
maxBufferedBytes=1048576
```

The default bind address is localhost. Set it to `0.0.0.0` only when listeners
should be reachable from outside the Minecraft host; the host firewall and NAT
configuration still apply.

Listener handles and their bound ports are saved with the Socket Card and
reopened when the card is loaded again. Existing TCP connections are closed
when the card disconnects, the computer disconnects, or the server stops.
