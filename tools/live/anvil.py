#!/usr/bin/env python3
"""D3 probe: list block names at height y in chunk (cx,cz) of a 1.20.1
region file (post-flattening palette: sections carry a block_states palette
of namespaced names plus packed long data)."""
import struct, sys, zlib


def read_nbt(buf, pos):
    t = buf[pos]
    pos += 1
    if t == 0:
        return None, pos
    nlen = struct.unpack(">H", buf[pos:pos + 2])[0]
    pos += 2
    name = buf[pos:pos + nlen].decode("utf-8", "replace")
    pos += nlen
    val, pos = read_payload(buf, pos, t)
    return (name, val), pos


def read_payload(buf, pos, t):
    if t == 1:
        # TAG_Byte is signed (-128..127); section Y below 0 needs the sign.
        v = buf[pos]
        return (v - 256 if v > 127 else v), pos + 1
    if t == 2:
        return struct.unpack(">h", buf[pos:pos + 2])[0], pos + 2
    if t == 3:
        return struct.unpack(">i", buf[pos:pos + 4])[0], pos + 4
    if t == 4:
        return struct.unpack(">q", buf[pos:pos + 8])[0], pos + 8
    if t == 5:
        return struct.unpack(">f", buf[pos:pos + 4])[0], pos + 4
    if t == 6:
        return struct.unpack(">d", buf[pos:pos + 8])[0], pos + 8
    if t == 7:
        n = struct.unpack(">i", buf[pos:pos + 4])[0]
        return bytes(buf[pos + 4:pos + 4 + n]), pos + 4 + n
    if t == 8:
        n = struct.unpack(">H", buf[pos:pos + 2])[0]
        return buf[pos + 2:pos + 2 + n].decode("utf-8", "replace"), pos + 2 + n
    if t == 9:
        et = buf[pos]
        n = struct.unpack(">i", buf[pos + 1:pos + 5])[0]
        pos += 5
        out = []
        for _ in range(n):
            if et in (1, 2, 3, 4, 5, 6, 7, 8, 11, 12):
                v, pos = read_payload(buf, pos, et)
                out.append(v)
            elif et == 10:
                d = {}
                while True:
                    item, pos = read_nbt(buf, pos)
                    if item is None:
                        break
                    d[item[0]] = item[1]
                out.append(d)
            elif et == 9:
                # Nested lists (e.g. section PostProcessing): each sublist
                # carries its own element-type + length header.
                v, pos = read_payload(buf, pos, 9)
                out.append(v)
            else:
                raise ValueError("list of %d" % et)
        return out, pos
    if t == 10:
        d = {}
        while True:
            item, pos = read_nbt(buf, pos)
            if item is None:
                break
            d[item[0]] = item[1]
        return d, pos
    if t == 11:
        n = struct.unpack(">i", buf[pos:pos + 4])[0]
        vals = struct.unpack(">%di" % n, buf[pos + 4:pos + 4 + 4 * n])
        return list(vals), pos + 4 + 4 * n
    if t == 12:
        n = struct.unpack(">i", buf[pos:pos + 4])[0]
        vals = struct.unpack(">%dq" % n, buf[pos + 4:pos + 4 + 8 * n])
        return list(vals), pos + 4 + 8 * n
    raise ValueError("tag %d" % t)


def chunk_at(path, cx, cz):
    raw = open(path, "rb").read()
    lx, lz = cx & 31, cz & 31
    off = struct.unpack(">I", raw[(lx + lz * 32) * 4:(lx + lz * 32) * 4 + 4])[0]
    if off == 0:
        raise SystemExit("chunk absent")
    pos = (off >> 8) * 4096
    ln = struct.unpack(">I", raw[pos:pos + 4])[0]
    comp = raw[pos + 4]
    data = raw[pos + 5:pos + 4 + ln]
    if comp == 1:
        import gzip
        data = gzip.decompress(data)
    elif comp == 2:
        data = zlib.decompress(data)
    elif comp != 3:
        raise SystemExit("compression %d" % comp)
    (name, val), _ = read_nbt(data, 0)
    return val


def unpack_palette(palette, data):
    # 4096 indices into palette from packed longs (no padding across words).
    bits = max(4, (len(palette) - 1).bit_length())
    idx = [0] * 4096
    mask = (1 << bits) - 1
    bit = 0
    for i in range(4096):
        w = bit // 64
        o = bit % 64
        v = (data[w] >> o) & mask
        if o + bits > 64 and w + 1 < len(data):
            v |= (data[w + 1] << (64 - o)) & mask
        idx[i] = v
        bit += bits
    return idx


def main():
    path, cx, cz, y = sys.argv[1], int(sys.argv[2]), int(sys.argv[3]), int(sys.argv[4])
    root = chunk_at(path, cx, cz)
    found = {}
    for sec in root["sections"]:
        base = sec["Y"] * 16
        if base <= y < base + 16:
            states = sec.get("block_states")
            if not states:
                continue
            palette = [e["Name"] for e in states["palette"]]
            if "data" in states:
                idx = unpack_palette(palette, states["data"])
            else:
                # Single-valued section: Mojang omits data, all palette[0].
                idx = [0] * 4096
            ly = y - base
            for x in range(16):
                for z in range(16):
                    name = palette[idx[(ly * 16 + z) * 16 + x]]
                    if name != "minecraft:air":
                        found["%d,%d" % (x, z)] = name
    for cell in sorted(found):
        print(cell, found[cell])


if __name__ == "__main__":
    main()
