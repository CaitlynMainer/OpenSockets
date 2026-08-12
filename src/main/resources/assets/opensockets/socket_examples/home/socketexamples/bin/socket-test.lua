local component = require("component")
local socket = component.opensockets

if not socket then
  error("Socket Card is not installed or connected")
end

local ok, listener, port = socket.listen(28000)
if not ok then
  error("listen failed: " .. tostring(listener))
end

print("OpenSockets is working")
print("component: opensockets")
print("listener: " .. tostring(listener))
print("port: " .. tostring(port))
socket.close(listener)
