"""Read a small inventory from the original ext4 images, without mounting them."""
from pathlib import Path
import json
import struct

root = Path(__file__).resolve().parent.parent
backup = root / "outputs" / "toss-front2-backup"
layout = json.loads((backup / "logical-partitions.json").read_text())
raw_path = backup / "ufs-lun0.bin.partial"
if not raw_path.exists():
    raw_path = backup / "ufs-lun0.bin"

class Ext4Reader:
    def __init__(self, file, partition):
        self.file = file
        self.extents = partition["extents"]
        sb = self.read(1024, 1024)
        assert sb[56:58] == b"\x53\xef"
        self.block_size = 1024 << struct.unpack_from("<I", sb, 24)[0]
        self.inodes_per_group = struct.unpack_from("<I", sb, 40)[0]
        self.inode_size = struct.unpack_from("<H", sb, 88)[0]
        self.descriptor_size = max(32, struct.unpack_from("<H", sb, 254)[0])

    def read(self, offset, size):
        output = bytearray()
        position = 0
        for extent in self.extents:
            length = extent["sectors"] * 512
            if offset >= position + length:
                position += length
                continue
            within = offset - position
            take = min(size, length - within)
            if extent["type"] == 1:
                output.extend(bytes(take))
            else:
                assert extent["source"] == 0
                self.file.seek(128776 * 4096 + extent["physical_sector"] * 512 + within)
                block = self.file.read(take)
                assert len(block) == take
                output.extend(block)
            offset += take
            size -= take
            position += length
            if not size:
                return bytes(output)
        raise ValueError("Read outside logical partition")

    def inode(self, number):
        group, index = divmod(number - 1, self.inodes_per_group)
        gd = self.read(self.block_size + group * self.descriptor_size, self.descriptor_size)
        table = struct.unpack_from("<I", gd, 8)[0]
        if self.descriptor_size >= 64:
            table |= struct.unpack_from("<I", gd, 40)[0] << 32
        return self.read(table * self.block_size + index * self.inode_size, self.inode_size)

    def extent_tree(self, node):
        magic, count, _, depth = struct.unpack_from("<HHHH", node)
        assert magic == 0xF30A
        result = []
        for i in range(count):
            entry = node[12 + i * 12:24 + i * 12]
            if depth:
                _, low, high = struct.unpack_from("<IIH", entry)
                result.extend(self.extent_tree(self.read(((high << 32) | low) * self.block_size, self.block_size)))
            else:
                logical, length, high, low = struct.unpack("<IHHI", entry)
                result.append((logical, length & 0x7FFF, (high << 32) | low))
        return result

    def file_bytes(self, number, maximum=1024 * 1024):
        inode = self.inode(number)
        size = struct.unpack_from("<I", inode, 4)[0] | (struct.unpack_from("<I", inode, 108)[0] << 32)
        assert size <= maximum, "Refusing to read a large file for inventory"
        output = bytearray(size)
        assert struct.unpack_from("<I", inode, 32)[0] & 0x80000
        for logical, blocks, physical in self.extent_tree(inode[40:100]):
            start = logical * self.block_size
            take = min(blocks * self.block_size, size - start)
            if take > 0:
                output[start:start + take] = self.read(physical * self.block_size, take)
        return bytes(output)

    def directory(self, number):
        if struct.unpack_from("<H", self.inode(number), 0)[0] & 0xF000 != 0x4000:
            raise KeyError("Path crosses a symlink or non-directory")
        data = self.file_bytes(number)
        entries = {}
        offset = 0
        while offset + 8 <= len(data):
            child, record_length, name_length, _ = struct.unpack_from("<IHBB", data, offset)
            assert record_length >= 8
            name = data[offset + 8:offset + 8 + name_length].decode("utf-8", errors="replace")
            if child and name not in {".", ".."}:
                entries[name] = child
            offset += record_length
        return entries

    def lookup(self, path):
        number = 2
        for component in path.strip("/").split("/"):
            number = self.directory(number)[component]
        return number

report = {"basis": "Original base ext4 images; excludes virtual A/B snapshot overlays", "partitions": {}}
with raw_path.open("rb") as file:
    for name in ["system_a", "system_ext_a", "vendor_a", "product_a"]:
        partition = next(item for item in layout["partitions"] if item["name"] == name)
        fs = Ext4Reader(file, partition)
        item = {"root_entries": list(fs.directory(2)), "properties": {}, "app_directories": {}}
        for path in ["build.prop", "system/build.prop", "etc/build.prop"]:
            try:
                text = fs.file_bytes(fs.lookup(path)).decode("utf-8", errors="replace")
            except KeyError:
                continue
            for line in text.splitlines():
                if line.startswith(("ro.build.version.", "ro.product.cpu.", "ro.vendor.build.version.",
                                    "ro.product.vendor.device=", "ro.treble.", "ro.vndk.", "ro.debuggable=")):
                    key, value = line.split("=", 1)
                    item["properties"][key] = value
        for path in ["app", "priv-app", "system/app", "system/priv-app"]:
            try:
                item["app_directories"][path] = list(fs.directory(fs.lookup(path)))
            except KeyError:
                pass
        report["partitions"][name] = item
(backup / "base-system-inventory.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
print(json.dumps(report, indent=2))
