"""Advertise OBS WebSocket via mDNS so the Android app auto-discovers this machine.
Usage: pip install zeroconf && python obs-mdns.py"""

from zeroconf import ServiceInfo, Zeroconf
import socket

hostname = socket.gethostname()
local_ip = socket.gethostbyname(hostname)

info = ServiceInfo(
    "_obs-websocket._tcp.local.",
    f"OBS on {hostname}._obs-websocket._tcp.local.",
    addresses=[socket.inet_aton(local_ip)],
    port=4455,
    properties={},
)

zc = Zeroconf()
zc.register_service(info)
print(f"Advertising OBS WebSocket at {local_ip}:4455 via mDNS")
input("Press Enter to stop...\n")
zc.unregister_service(info)
zc.close()
