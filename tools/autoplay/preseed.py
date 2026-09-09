#!/usr/bin/env python3
"""Preseed a fresh classic-flat singleplayer world for the autoplay proof.

Writes <saves>/<world>/level.dat (deterministic: seed 0, creative,
classic flat, no structures touched) after deleting any previous copy, so
every automated run proves from scratch. Refuses loudly when the target
cannot be cleared. Quick-play joins this world by folder name; hub
tools/verify-client-save.sh judges world == pure union afterwards
(decoration-free slices y=63..65: ground features never reach them).

1.20.1 shape (measured, not assumed): same WorldGenSettings skeleton as
1.16.5 (cf. bridge-1165 tools/autoplay/preseed.py, paralleled
per-version, never imported) — flat overworld generator with EMPTY
structures so no village piece can ever land in the proof slices — with
the 1.20.1 version stamps (DataVersion 3465). The verdict owns any drift:
a preseed the game ignores (or migrates) shows up as foreign blocks or
missing cells, never as a quiet pass.

Usage: preseed.py <saves-dir> <world-name>
"""
import gzip
import os
import shutil
import struct
import sys


def _tag(t, name, payload):
    return struct.pack(">b", t) + struct.pack(">h", len(name)) + name.encode() + payload


def _end():
    return struct.pack(">b", 0)


def _compound(name, body):
    return _tag(10, name, body + _end())


def _list(name, etype, items):
    return _tag(9, name, struct.pack(">b", etype) + struct.pack(">i", len(items)) + b"".join(items))


def _string(name, value):
    v = value.encode()
    return _tag(8, name, struct.pack(">h", len(v)) + v)


def _int(name, value):
    return _tag(3, name, struct.pack(">i", value))


def _long(name, value):
    return _tag(4, name, struct.pack(">q", value))


def _byte(name, value):
    return _tag(1, name, struct.pack(">b", value))


def _c(name, *tags):
    # Compound from child tags (_compound appends TAG_End itself; bodies
    # must NOT carry their own — a stray end terminates the PARENT early
    # and silently drops every sibling tag after it).
    return _compound(name, b"".join(tags))


def _world_gen_settings(seed):
    flat_settings = _c("settings",
        _string("biome", "minecraft:plains"),
        _byte("lakes", 0),
        _byte("features", 0),
        _list("layers", 10, _split_layers()),
        _c("structures", _c("structures")),
    )
    overworld = _c("minecraft:overworld",
        _string("type", "minecraft:overworld"),
        _c("generator",
            _string("type", "minecraft:flat"),
            flat_settings,
        ),
    )
    nether = _c("minecraft:the_nether",
        _string("type", "minecraft:the_nether"),
        _c("generator",
            _string("type", "minecraft:noise"),
            _string("settings", "minecraft:nether"),
            _long("seed", seed),
            _c("biome_source",
                _long("seed", seed),
                _string("preset", "minecraft:nether"),
                _string("type", "minecraft:multi_noise"),
            ),
        ),
    )
    end = _c("minecraft:the_end",
        _string("type", "minecraft:the_end"),
        _c("generator",
            _string("type", "minecraft:noise"),
            _string("settings", "minecraft:end"),
            _long("seed", seed),
            _c("biome_source",
                _long("seed", seed),
                _string("type", "minecraft:the_end"),
            ),
        ),
    )
    return _c("WorldGenSettings",
        _byte("bonus_chest", 0),
        _long("seed", seed),
        _byte("generate_features", 0),
        _c("dimensions", overworld, nether, end),
    )


def _split_layers():
    # Layers as separate TAG_Compound payloads (list element headers carry
    # no names; each element is a bare compound body + TAG_End).
    out = []
    for block, height in (("minecraft:bedrock", 1),
                          ("minecraft:dirt", 2),
                          ("minecraft:grass_block", 1)):
        out.append(_string("block", block) + _int("height", height) + _end())
    return out


def main(saves, world):
    target = os.path.join(saves, world)
    if os.path.lexists(target):
        shutil.rmtree(target)
    os.makedirs(target)
    data = b"".join([
        _string("LevelName", world),
        _int("GameType", 1),
        _int("Difficulty", 1),
        _byte("allowCommands", 1),
        _long("Time", 0),
        _long("DayTime", 0),
        _int("SpawnX", 8),
        _int("SpawnY", 5),
        _int("SpawnZ", 8),
        _world_gen_settings(0),
        _int("version", 3465),
        _compound("Version", b"".join([
            _int("Id", 3465),
            _string("Name", "1.20.1"),
            _byte("Snapshot", 0),
        ])),
        _int("DataVersion", 3465),
        _byte("raining", 0),
        _byte("thundering", 0),
    ])
    root = _compound("", _compound("Data", data))
    with gzip.open(os.path.join(target, "level.dat"), "wb") as fh:
        fh.write(root)
    print("ok preseed : fresh flat <%s> (seed 0, creative)" % target)


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("FAIL preseed : usage preseed.py <saves-dir> <world-name>")
        sys.exit(1)
    main(sys.argv[1], sys.argv[2])
