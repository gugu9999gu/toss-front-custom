from pathlib import Path
import hashlib
import json
import struct

root = Path(__file__).resolve().parent.parent
data = (root / "work" / "super-header.bin").read_bytes()
geometry_size = struct.unpack_from("<I", data, 4100)[0]
geometry = bytearray(data[4096:4096 + geometry_size])
geometry_checksum = bytes(geometry[8:40])
geometry[8:40] = bytes(32)
assert hashlib.sha256(geometry).digest() == geometry_checksum
metadata_offset = 12288
header_size = struct.unpack_from("<I", data, metadata_offset + 8)[0]
header = bytearray(data[metadata_offset:metadata_offset + header_size])
header_checksum = bytes(header[12:44])
header[12:44] = bytes(32)
assert hashlib.sha256(header).digest() == header_checksum
table_size = struct.unpack_from("<I", header, 44)[0]
table_offset = metadata_offset + header_size
tables = data[table_offset:table_offset + table_size]
assert hashlib.sha256(tables).digest() == bytes(header[48:80])

def entries(descriptor_offset):
    offset, count, size = struct.unpack_from("<III", header, descriptor_offset)
    return [tables[offset + i * size:offset + (i + 1) * size] for i in range(count)]

extents = []
for entry in entries(92):
    sectors, kind, sector, source = struct.unpack_from("<QIQI", entry)
    extents.append({"sectors": sectors, "type": kind, "physical_sector": sector, "source": source})
groups = []
for entry in entries(104):
    flags, maximum_size = struct.unpack_from("<IQ", entry, 36)
    groups.append({"name": entry[:36].split(b"\0")[0].decode(), "flags": flags,
                   "maximum_size": maximum_size})
partitions = []
for entry in entries(80):
    name = entry[:36].split(b"\0")[0].decode()
    attributes, first, count, group = struct.unpack_from("<IIII", entry, 36)
    owned = extents[first:first + count]
    partitions.append({"name": name, "attributes": attributes, "group": group,
                       "bytes": sum(extent["sectors"] * 512 for extent in owned),
                       "extents": owned})
report = {"metadata_hashes_verified": True, "metadata_slot": 0,
          "groups": groups, "partitions": partitions}
(root / "outputs" / "toss-front2-backup" / "logical-partitions.json").write_text(
    json.dumps(report, indent=2), encoding="utf-8")
print(json.dumps({"groups": groups, "partitions": [{"name": part["name"], "bytes": part["bytes"]}
                                                   for part in partitions]}, indent=2))
