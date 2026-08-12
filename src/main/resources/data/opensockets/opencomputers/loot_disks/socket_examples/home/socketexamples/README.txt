OpenSockets Examples

This floppy contains programs for the OpenSockets card.

1. Put the Socket Card in the computer's card slot.
2. Put this floppy in a disk drive connected to the computer.
3. Copy a program from this floppy to /home, or run it directly from its
   mounted path.

The component is named "opensockets". Test it with:

  lua /mnt/<disk-address>/bin/socket-test.lua

The HTTP and FTP programs are examples only. The default server bind address
is localhost (127.0.0.1); set bindAddress to 0.0.0.0 in the server config if
the service must be reachable from outside the Minecraft host.
