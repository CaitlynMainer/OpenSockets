-- OpenSockets example: a deliberately small passive FTP server.
-- This is an example, not an internet-hardened FTP implementation.
local component = require("component")
local filesystem = require("filesystem")
local socket = component.opensockets

local root = "/home/ftp"
local function reply(connection, text)
  socket.write(connection, text .. "\r\n")
end

local function safePath(path)
  path = path or "/"
  path = path:gsub("^/", "")
  if path:find("%.%.", 1, true) then return nil end
  return filesystem.concat(root, path)
end

local function acceptData(listener)
  while true do
    local connection = socket.accept(listener)
    if connection then return connection end
    os.sleep(0.05)
  end
end

local function openPassive(connection)
  local ok, listener, dataPort = socket.listen(0)
  if not ok then
    reply(connection, "425 " .. tostring(listener))
    return nil
  end
  -- The default OpenSockets bind address is localhost. Replace this with the
  -- server's externally reachable address when bindAddress is 0.0.0.0.
  local high = math.floor(dataPort / 256)
  local low = dataPort % 256
  reply(connection, string.format("227 Entering Passive Mode (127,0,0,1,%d,%d)", high, low))
  return listener
end

local ok, listener, port = socket.listen(28002)
if not ok then error("listen failed: " .. tostring(listener)) end
print("FTPd control listener on port " .. tostring(port))

while true do
  local connection = socket.accept(listener)
  if connection then
    reply(connection, "220 OpenComputers FTPd")
    local running = true
    while running do
      local data = socket.read(connection, 4096)
      if not data then os.sleep(0.05) else
        for line in data:gmatch("[^\r\n]+") do
          local command, argument = line:match("^(%S+)%s*(.*)$")
          command = (command or ""):upper()
          if command == "USER" then
            reply(connection, "331 Password required")
          elseif command == "PASS" then
            reply(connection, "230 Logged in")
          elseif command == "SYST" then
            reply(connection, "215 UNIX Type: L8")
          elseif command == "PWD" then
            reply(connection, '257 "/" is the current directory')
          elseif command == "TYPE" then
            reply(connection, "200 Type set")
          elseif command == "PASV" then
            if dataListener then socket.close(dataListener) end
            dataListener = openPassive(connection)
          elseif command == "LIST" then
            local path = safePath(argument)
            if not dataListener or not path or not filesystem.isDirectory(path) then
              reply(connection, "425 Use PASV first")
            else
              reply(connection, "150 Opening data connection")
              local data = acceptData(dataListener)
              for name in filesystem.list(path) do
                socket.write(data, name .. "\r\n")
              end
              socket.close(data)
              socket.close(dataListener)
              dataListener = nil
              reply(connection, "226 Transfer complete")
            end
          elseif command == "RETR" then
            local path = safePath(argument)
            local file = path and io.open(path, "rb")
            if not dataListener or not file then
              reply(connection, "550 File unavailable")
            else
              reply(connection, "150 Opening data connection")
              local data = acceptData(dataListener)
              while true do
                local chunk = file:read(8192)
                if not chunk then break end
                socket.write(data, chunk)
              end
              file:close()
              socket.close(data)
              socket.close(dataListener)
              dataListener = nil
              reply(connection, "226 Transfer complete")
            end
          elseif command == "STOR" then
            local path = safePath(argument)
            local file = path and io.open(path, "wb")
            if not dataListener or not file then
              reply(connection, "550 File unavailable")
            else
              reply(connection, "150 Opening data connection")
              local data = acceptData(dataListener)
              while true do
                local chunk = socket.read(data, 8192)
                if not chunk then break end
                file:write(chunk)
              end
              file:close()
              socket.close(data)
              socket.close(dataListener)
              dataListener = nil
              reply(connection, "226 Transfer complete")
            end
          elseif command == "QUIT" then
            reply(connection, "221 Goodbye")
            running = false
          else
            reply(connection, "502 Command not implemented")
          end
        end
      end
    end
    socket.close(connection)
  end
  os.sleep(0.05)
end
