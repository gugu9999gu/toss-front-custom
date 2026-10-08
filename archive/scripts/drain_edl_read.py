"""Finish receiving any pending USB read response; never send a command."""
import json
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent / "edl"))
from edl.Library.usblib import UsbClass

connection = UsbClass(portconfig=[[0x05C6, 0x9008, -1]])
if not connection.connect():
    raise SystemExit("EDL USB is not connected")
received = 0
ack_seen = False
deadline = time.monotonic() + 15
try:
    while time.monotonic() < deadline and received < 32 * 1024 * 1024:
        data = connection.read(timeout=1200)
        if not data:
            break
        received += len(data)
        ack_seen |= b'value="ACK"' in data
finally:
    connection.close()
print(json.dumps({"pending_bytes_received": received, "ack_seen": ack_seen}))
