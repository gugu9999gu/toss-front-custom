from pathlib import Path
import json
import re
import struct
import zipfile

root = Path(__file__).resolve().parent.parent
out = root / "work/vendor-framework"
report = {}
for path in out.iterdir():
    if path.suffix not in {".jar", ".apk"}:
        continue
    found = []
    with zipfile.ZipFile(path) as archive:
        for name in archive.namelist():
            if not name.startswith("classes") or not name.endswith(".dex"):
                continue
            data = archive.read(name)
            count, offset = struct.unpack_from("<II", data, 56)
            strings = []
            targets = {}
            for i in range(count):
                position = struct.unpack_from("<I", data, offset + 4*i)[0]
                while data[position] & 0x80:
                    position += 1
                position += 1
                end = data.find(b"\x00", position)
                value = data[position:end].decode("utf-8", errors="replace")
                strings.append(value)
                if any(term in value.lower() for term in ["debug mode", "sysdebugmode", "appdebugmode", "oscustomer", "debugmode", "debug_mode", "minicat"]):
                    found.append({"dex": name, "string": value})
                if value in {"DEBUG MODE [", "ro.tossplace.appdebugmode", "ro.tossplace.sysdebugmode"}:
                    targets[i] = value
            type_count, type_offset = struct.unpack_from("<II", data, 64)
            method_count, method_offset = struct.unpack_from("<II", data, 88)
            class_count, class_offset = struct.unpack_from("<II", data, 96)
            def uleb(pos):
                value, shift = 0, 0
                while True:
                    byte = data[pos]
                    pos += 1
                    value |= (byte & 127) << shift
                    if not byte & 128:
                        return value, pos
                    shift += 7
            for c in range(class_count):
                definition = class_offset + 32*c
                class_index = struct.unpack_from("<I", data, definition)[0]
                class_data = struct.unpack_from("<I", data, definition+24)[0]
                if not class_data:
                    continue
                pos = class_data
                sizes = []
                for _ in range(4):
                    size, pos = uleb(pos)
                    sizes.append(size)
                for _ in range(sizes[0]+sizes[1]):
                    _, pos = uleb(pos)
                    _, pos = uleb(pos)
                for size in sizes[2:]:
                    method_index = 0
                    for _ in range(size):
                        diff, pos = uleb(pos)
                        method_index += diff
                        _, pos = uleb(pos)
                        code_offset, pos = uleb(pos)
                        if not code_offset:
                            continue
                        insn_count = struct.unpack_from("<I", data, code_offset+12)[0]
                        instructions = data[code_offset+16:code_offset+16+insn_count*2]
                        for index, value in targets.items():
                            pattern = b"\x1a." + re.escape(struct.pack("<H", index)) if index < 65536 else b"\x1b." + re.escape(struct.pack("<I", index))
                            if re.search(pattern, instructions, re.DOTALL):
                                class_name = strings[struct.unpack_from("<I", data, type_offset+class_index*4)[0]]
                                method_name = strings[struct.unpack_from("<I", data, method_offset+method_index*8+4)[0]]
                                found.append({"dex": name, "xref_candidate": value, "class": class_name, "method": method_name})
    report[path.name] = found
(out / "dex-debug-index.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
print(json.dumps(report, indent=2))
