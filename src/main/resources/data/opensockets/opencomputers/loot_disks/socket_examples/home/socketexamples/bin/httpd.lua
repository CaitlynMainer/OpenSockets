-- OpenSockets example: serve files from /home/socketexamples/www.
local component = require("component")
local filesystem = require("filesystem")
local socket = component.opensockets
local root = "/home/socketexamples/www"

local ok, listener, port = socket.listen(28001)
if not ok then error("listen failed: " .. tostring(listener)) end
print("HTTPd serving " .. root .. " on port " .. tostring(port))

local function response(status, contentType, body)
  return "HTTP/1.0 " .. status .. "\r\n" ..
         "Content-Type: " .. contentType .. "\r\n" ..
         "Content-Length: " .. tostring(#body) .. "\r\n" ..
         "Connection: close\r\n\r\n" .. body
end

while true do
  local connection = socket.accept(listener)
  if connection then
    local request = ""
    while not request:find("\r\n\r\n", 1, true) do
      local chunk = socket.read(connection, 16384)
      if not chunk then break end
      request = request .. chunk
    end

    local method, path = request:match("^(%S+)%s+(%S+)%s+HTTP/%d%.%d")
    if method ~= "GET" then
      socket.write(connection, response("405 Method Not Allowed", "text/plain", "Only GET is supported.\n"))
    else
      path = (path or "/"):gsub("%?(.*)$", "")
      path = path:gsub("^/", "")
      if path == "" or path:sub(-1) == "/" then path = path .. "index.html" end
      local safe = not path:find("%.%.", 1, true)
      local filePath = safe and filesystem.concat(root, path)
      local file = filePath and io.open(filePath, "rb")
      if not file then
        socket.write(connection, response("404 Not Found", "text/plain", "Not found.\n"))
      else
        local body = file:read("*a")
        file:close()
        socket.write(connection, response("200 OK", "application/octet-stream", body))
      end
    end
    socket.close(connection)
  end
  os.sleep(0.05)
end
