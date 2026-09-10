#!/bin/sh
# D3 live gate: Forge 1.20.1-47.2.0 server run proving PackWire.bind
# (real Block resolve) plus world-tick apply on a real world, then comparing
# the world against the pure decision union (tools/live).
#
# Manual gate (needs network once + Java 17); opt-in from tools/check.sh via
# LIVE=1, never blocking by default. Never silent: any mismatch fails loudly,
# never defaulted.
#
# Env (no machine paths hardcoded):
#   D3_DIR     work dir (default ${TMPDIR:-/tmp}/matou-d3-live; non-owned
#              leftovers refused loudly by the preflight — clean or fresh dir)
#   JAVA17_HOME Java 17 home (default /usr/lib/jvm/java-17-openjdk;
#              the pinned live image exports /opt/java/openjdk)
#   FORGE_URL  installer URL (default Maven 47.2.0 installer)
#   MCP_CONFIG_URL  MCP config URL (default Forge Maven mcp_config
#              1.20.1-20230612.114412, carries joined.tsrg, the obf<->SRG map
#              the narrow map derives from)
#   MOJMAPS_URL  Mojang official server mappings URL (default piston-data
#              server.txt, the moj<->obf map; sha1 from the Mojang version
#              manifest, content-addressed, immutable)
#   BOOT_SECS  server run time (default 150; short runs fail coverage loudly)
#   D3_OFFLINE=1  never download (fail loudly if cache files missing)
#
# R2 release assembly: BUILD_ONLY=1 VERSION=x.y.z assembles dist/ (versioned
# jars + content + packs.cfg.example + SHA256SUMS) and exits before booting
# the server. Release demands strict X.Y.Z, a clean tree in all 4 code repos,
# and a tokenized mods.toml template; anything else fails loudly, never
# defaulted. SOURCE_DATE_EPOCH pins jar entry timestamps (default: bridge
# HEAD commit time); with a pinned toolchain (tools/live/Dockerfile) the
# same commit always yields the same bytes.
#
# Reproducibility pins: installer / universal / vanilla server / MCP config /
# Mojang mappings / ASM sha1 below. Any upstream drift fails loudly instead
# of running against unknown bytes.
#
# Production naming (proven, not assumed): Forge 47.2.0 runs Mojmap classes
# with SRG members (the installer MERGE_MAPPING keeps classes official and
# members searge — see the D3 note in hub STATE.md). forge/ sources are
# Mojmap; only the narrow-map members reobfuscate (Reobf, the ForgeGradle
# reobf equivalent). A wrong model dies live with NoSuchMethodError — found
# loudly at step 6, never silently.
set -eu
cd "$(dirname "$0")/.."
D3_DIR="${D3_DIR:-${TMPDIR:-/tmp}/matou-d3-live}"
JAVA17_HOME="${JAVA17_HOME:-/usr/lib/jvm/java-17-openjdk}"
FORGE_URL="${FORGE_URL:-https://maven.minecraftforge.net/net/minecraftforge/forge/1.20.1-47.2.0/forge-1.20.1-47.2.0-installer.jar}"
MCP_CONFIG_URL="${MCP_CONFIG_URL:-https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_config/1.20.1-20230612.114412/mcp_config-1.20.1-20230612.114412.zip}"
MOJMAPS_URL="${MOJMAPS_URL:-https://piston-data.mojang.com/v1/objects/0b4dba049482496c507b2387a73a913230ebbd76/server.txt}"
BOOT_SECS="${BOOT_SECS:-150}"
# Pins: measured 2026-09-09 from the Maven installer + installed universal +
# Mojang vanilla server + MCP config + Mojang server mappings + provisioned
# ASM 9.5. Drift = loud failure, never silent upgrade.
INSTALLER_SHA1="ded43dd18b3a1dd5098b114c28432224d72bd9f7"
UNIVERSAL_SHA1="ac078f159add92ef9dce7a8259508c9d78470d3a"
MC_SERVER_SHA1="84194a2f286ef7c14ed7ce0090dba59902951553"
MCP_CONFIG_SHA1="c7d29380ddb38becad7c0819b5b325e43bca23f0"
MOJMAPS_SHA1="0b4dba049482496c507b2387a73a913230ebbd76"
ASM_PIN="asm-9.5.jar"
ASM_SHA1="dc6ea1875f4d64fbc85e1691c95b96a3d8569c90"
ASM_COMMONS_PIN="asm-commons-9.5.jar"
ASM_COMMONS_SHA1="19ab5b5800a3910d30d3a3e64fdb00fd0cb42de0"
J17="$JAVA17_HOME/bin"
[ -x "$J17/java" ] || { echo "FAIL d3-live : no Java 17 at <$JAVA17_HOME>"; exit 1; }
[ -x "$J17/javac" ] || { echo "FAIL d3-live : no javac at <$JAVA17_HOME>"; exit 1; }
[ -x "$J17/javap" ] || { echo "FAIL d3-live : no javap at <$JAVA17_HOME>"; exit 1; }
[ -d ../spi/java/src ] || { echo "FAIL d3-live : spi sibling absent"; exit 1; }
[ -d ../example1/java/src ] || { echo "FAIL d3-live : example1 sibling absent"; exit 1; }
[ -d ../minimap/java/src ] || { echo "FAIL d3-live : minimap sibling absent"; exit 1; }
command -v python3 >/dev/null || { echo "FAIL d3-live : python3 required (narrow derive + anvil verify)"; exit 1; }
# R2 versioning: VERSION stamps manifests + mods.toml. Dev live runs take an
# explicit non-release default; release assembly demands strict X.Y.Z.
VERSION="${VERSION:-0.0-dev}"
if [ "${BUILD_ONLY:-}" = "1" ]; then
  printf '%s' "$VERSION" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+$' \
    || { echo "FAIL r2-release : VERSION=<$VERSION> not X.Y.Z (want e.g. 1.0.0)"; exit 1; }
  for r in . ../spi ../example1 ../minimap; do
    git -C "$r" diff --quiet && git -C "$r" diff --cached --quiet \
      || { echo "FAIL r2-release : dirty tree in <$r> (release from clean checkouts only)"; exit 1; }
  done
  grep -q 'version="@VERSION@"' forge/src/META-INF/mods.toml \
    || { echo "FAIL r2-release : mods.toml template hardcoded (keep @VERSION@, bump via VERSION=)"; exit 1; }
fi

# 1. Provision the 47.2.0 server once (idempotent, checksum-verified).
#    D3_OFFLINE=1 never touches the network: missing cache fails loudly.
mkdir -p "$D3_DIR"
SERV="$D3_DIR/server"
mkdir -p "$SERV"
# 1a. D3_DIR preflight: docker runs leave root-owned leftovers (build/,
#     world/, logs/, matou-content/) that a host run cannot clear file by
#     file (rm needs write on the root-owned parent). Fail fast with the fix
#     instead of dying mid-run or reusing stale state silently.
if [ -e "$D3_DIR" ]; then
  BAD_OWNER=$(find "$D3_DIR" ! -user "$(id -un)" -print -quit 2>/dev/null || true)
  if [ -n "$BAD_OWNER" ]; then
    echo "FAIL d3-live : D3_DIR=<$D3_DIR> has non-owned leftovers (e.g. <$BAD_OWNER> from a docker run as root)"
    echo "fix: sudo rm -rf <$D3_DIR/build> <$D3_DIR/server/world> <$D3_DIR/server/logs> <$D3_DIR/server/matou-content> OR D3_DIR=/tmp/matou-d3-clean $0"
    exit 1
  fi
  if [ ! -w "$D3_DIR" ]; then
    echo "FAIL d3-live : D3_DIR=<$D3_DIR> not writable (fix ownership or point D3_DIR at a user-owned dir)"
    exit 1
  fi
fi
if [ ! -f "$D3_DIR/forge-installer.jar" ]; then
  if [ "${D3_OFFLINE:-}" = "1" ]; then
    echo "FAIL d3-live : offline and installer absent ($D3_DIR/forge-installer.jar)"
    exit 1
  fi
  curl -sL -o "$D3_DIR/forge-installer.jar" "$FORGE_URL" \
    || { echo "FAIL d3-live : installer download"; exit 1; }
fi
echo "$INSTALLER_SHA1  $D3_DIR/forge-installer.jar" | sha1sum -c - >/dev/null 2>&1 \
  || { echo "FAIL d3-live : installer sha1 drift (want $INSTALLER_SHA1)"; exit 1; }
LIB="$SERV/libraries"
UNI="$LIB/net/minecraftforge/forge/1.20.1-47.2.0/forge-1.20.1-47.2.0-universal.jar"
if [ ! -f "$UNI" ]; then
  (cd "$SERV" && "$J17/java" -jar "$D3_DIR/forge-installer.jar" --installServer >/dev/null 2>&1) \
    || { echo "FAIL d3-live : --installServer"; exit 1; }
fi
echo "$UNIVERSAL_SHA1  $UNI" | sha1sum -c - >/dev/null 2>&1 \
  || { echo "FAIL d3-live : universal sha1 drift (want $UNIVERSAL_SHA1)"; exit 1; }
MCSERV="$LIB/net/minecraft/server/1.20.1/server-1.20.1.jar"
[ -f "$MCSERV" ] || { echo "FAIL d3-live : vanilla server absent ($MCSERV, re-run --installServer online)"; exit 1; }
echo "$MC_SERVER_SHA1  $MCSERV" | sha1sum -c - >/dev/null 2>&1 \
  || { echo "FAIL d3-live : vanilla server sha1 drift (want $MC_SERVER_SHA1)"; exit 1; }
# 1.20.1 ships a bundler: game classes live in the nested version jar.
# Extract it deterministically (zip bytes, no toolchain) for javap truth.
MC_INNER="$D3_DIR/minecraft-server-1.20.1-inner.jar"
python3 - "$MCSERV" "$MC_INNER" <<'EOF'
import sys, zipfile
bundler, inner = sys.argv[1], sys.argv[2]
z = zipfile.ZipFile(bundler)
names = [n for n in z.namelist()
         if n.startswith("META-INF/versions/") and n.endswith(".jar")]
assert len(names) == 1, "E_SRG_DERIVE:bundler inner %s" % names
open(inner, "wb").write(z.read(names[0]))
print("ok d3-live : inner server jar extracted")
EOF
SRG_GAME="$LIB/net/minecraft/server/1.20.1-20230612.114412/server-1.20.1-20230612.114412-srg.jar"
[ -f "$SRG_GAME" ] || { echo "FAIL d3-live : renamed game absent ($SRG_GAME, re-run --installServer online)"; exit 1; }
ASM="$LIB/org/ow2/asm/asm/9.5/$ASM_PIN"
[ -f "$ASM" ] || { echo "FAIL d3-live : ASM $ASM_PIN missing from server libs"; exit 1; }
echo "$ASM_SHA1  $ASM" | sha1sum -c - >/dev/null 2>&1 \
  || { echo "FAIL d3-live : ASM sha1 drift (want $ASM_SHA1)"; exit 1; }
ASM_COMMONS="$LIB/org/ow2/asm/asm-commons/9.5/$ASM_COMMONS_PIN"
[ -f "$ASM_COMMONS" ] || { echo "FAIL d3-live : ASM commons $ASM_COMMONS_PIN missing from server libs"; exit 1; }
echo "$ASM_COMMONS_SHA1  $ASM_COMMONS" | sha1sum -c - >/dev/null 2>&1 \
  || { echo "FAIL d3-live : ASM commons sha1 drift (want $ASM_COMMONS_SHA1)"; exit 1; }
FMLCORE="$LIB/net/minecraftforge/fmlcore/1.20.1-47.2.0/fmlcore-1.20.1-47.2.0.jar"
[ -f "$FMLCORE" ] || { echo "FAIL d3-live : fmlcore absent ($FMLCORE, re-run --installServer online)"; exit 1; }
JMLLANG="$LIB/net/minecraftforge/javafmllanguage/1.20.1-47.2.0/javafmllanguage-1.20.1-47.2.0.jar"
[ -f "$JMLLANG" ] || { echo "FAIL d3-live : javafmllanguage absent ($JMLLANG, re-run --installServer online)"; exit 1; }
EVENTBUS="$LIB/net/minecraftforge/eventbus/6.0.5/eventbus-6.0.5.jar"
[ -f "$EVENTBUS" ] || { echo "FAIL d3-live : eventbus absent ($EVENTBUS, re-run --installServer online)"; exit 1; }
if [ ! -f "$D3_DIR/mcp_config-1.20.1-20230612.114412.zip" ]; then
  if [ "${D3_OFFLINE:-}" = "1" ]; then
    echo "FAIL d3-live : offline and MCP config absent ($D3_DIR/mcp_config-1.20.1-20230612.114412.zip)"
    exit 1
  fi
  curl -sL -o "$D3_DIR/mcp_config-1.20.1-20230612.114412.zip" "$MCP_CONFIG_URL" \
    || { echo "FAIL d3-live : MCP config download"; exit 1; }
fi
echo "$MCP_CONFIG_SHA1  $D3_DIR/mcp_config-1.20.1-20230612.114412.zip" | sha1sum -c - >/dev/null 2>&1 \
  || { echo "FAIL d3-live : MCP config sha1 drift (want $MCP_CONFIG_SHA1)"; exit 1; }
if [ ! -f "$D3_DIR/server-mappings.txt" ]; then
  if [ "${D3_OFFLINE:-}" = "1" ]; then
    echo "FAIL d3-live : offline and Mojang mappings absent ($D3_DIR/server-mappings.txt)"
    exit 1
  fi
  curl -sL -o "$D3_DIR/server-mappings.txt" "$MOJMAPS_URL" \
    || { echo "FAIL d3-live : Mojang mappings download"; exit 1; }
fi
echo "$MOJMAPS_SHA1  $D3_DIR/server-mappings.txt" | sha1sum -c - >/dev/null 2>&1 \
  || { echo "FAIL d3-live : Mojang mappings sha1 drift (want $MOJMAPS_SHA1)"; exit 1; }
echo "ok d3-live : server provisioned (pins verified)"

# 2. Derive the narrow Mojmap->SRG map from pinned bytes: server.txt gives
#    moj<->obf (classes and members), joined.tsrg v2 gives obf<->srg, and the
#    vanilla server jar disambiguates via javap (exactly-one assert per
#    member, loud otherwise). The map covers every vanilla member our
#    forge/ bytecode references (verified by constant-pool scan at D3 time:
#    setBlock, dimension, OVERWORLD, defaultBlockState, plus the
#    registration tranche: Properties.of, strength).
#    Production classes stay Mojmap (installer MERGE_MAPPING keeps classes
#    official) — only members reobfuscate, so no class lines are needed.
python3 - "$D3_DIR/mcp_config-1.20.1-20230612.114412.zip" "$D3_DIR/server-mappings.txt" "$MC_INNER" "$J17/javap" "$D3_DIR/srg-narrow.srg" <<'EOF'
import re, subprocess, sys, zipfile
mcpcfg, mojmaps, server, javap, outpath = sys.argv[1:6]
tsrg = zipfile.ZipFile(mcpcfg).read("config/joined.tsrg").decode("utf-8")
# moj class (dots) -> obf class; moj class -> [(kind, rettype, name, args, obf)]
moj2obf, members = {}, {}
cur = None
for raw in open(mojmaps):
    if not raw.strip() or raw.startswith("#"):
        continue
    if raw[0] in (" ", "\t"):
        m = re.match(r"^\s+(?:\d+:\d+:)?(\S+) ([\w$<>]+)(\(.*\))? -> ([\w$<>]+)$", raw.rstrip())
        assert m, "E_SRG_DERIVE:unparsed mappings line <%s>" % raw.rstrip()
        rettype, name, args, obf = m.groups()
        kind = "method" if args is not None else "field"
        members.setdefault(cur, []).append((kind, rettype, name, args or "", obf))
    else:
        if "package-info -> " in raw:
            continue  # ProGuard package marker, never a WANT owner
        m = re.match(r"^([\w.$]+) -> ([\w$.]+):$", raw.rstrip())
        assert m, "E_SRG_DERIVE:unparsed mappings class <%s>" % raw.rstrip()
        cur = m.group(1)
        moj2obf[cur] = m.group(2)
        members.setdefault(cur, [])

def to_internal(moj_dots):
    return moj_dots.replace(".", "/")

def obf_desc(moj_desc):
    return re.sub(r"L([^;]+);",
                  lambda m: "L" + to_internal(moj2obf.get(m.group(1).replace("/", "."), m.group(1))) + ";",
                  moj_desc)

def srg_desc(obf_d, obf2srg):
    return re.sub(r"L([^;]+);",
                  lambda m: "L" + obf2srg.get(m.group(1), m.group(1)) + ";",
                  obf_d)

# TSRG v2: class lines `obf srg [id]`; member lines (one tab) `obf [desc] srg
# [id]` — methods carry a descriptor, fields do not; a `static` line (two
# tabs) follows the member it describes, param lines are ignored.
obf2srg, classes = {}, {}
cur = None
for raw in tsrg.splitlines():
    if not raw.strip() or raw.startswith("tsrg2"):
        continue
    if raw[0] in (" ", "\t"):
        s = raw.strip()
        if s == "static":
            classes[cur][-1]["static"] = True
            continue
        if re.match(r"^\d+ ", s):
            continue
        parts = s.split()
        if "(" in s:
            classes[cur].append({"obf": parts[0], "desc": parts[1],
                                 "srg": parts[2], "static": False})
        else:
            classes[cur].append({"obf": parts[0], "desc": None,
                                 "srg": parts[1], "static": False})
    else:
        obf, srg = raw.split()[:2]
        obf2srg[obf] = srg
        cur = obf
        classes.setdefault(cur, [])

def javap_flags(cls):
    # -> {(name, descriptor-or-F:type): is_static} from the obf server jar.
    out = subprocess.check_output([javap, "-p", "-s", "-cp", server, cls]).decode()
    res, name, static = {}, None, False
    for l in out.splitlines():
        s = l.strip()
        if s.startswith("descriptor:"):
            res[(name, s.split(None, 1)[1])] = static
        elif s and not s.startswith("Compiled"):
            m = re.match(r".*\s([\w$<>]+)\(", s)
            if m:
                static = bool(re.search(r"\bstatic\b", s.split("(")[0]))
                name = m.group(1)
            elif "(" not in s and s.endswith(";") and "{" not in s:
                # Field type class carries `?` for javap-printed wildcards
                # (a generic Function field in BlockBehaviour$Properties —
                # measured, not assumed: the pre-fix class failed loud
                # here, same family as the 1165 wildcard finding).
                m2 = re.match(r"(?:(.*)\s)?([\w.$\[\]<>, ?]+?)\s+([\w$]+);", s)
                assert m2, "E_SRG_DERIVE:unparsed javap line <%s> in <%s>" % (s, cls)
                static = bool(re.search(r"\bstatic\b", m2.group(1) or ""))
                name = m2.group(3)
                res[(name, "F:" + re.sub(r"<.*>", "", m2.group(2)))] = static
    return res

WANT_METHODS = [
    ("net/minecraft/world/level/Level", "setBlock",
     "(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z", False),
    ("net/minecraft/world/level/Level", "dimension",
     "()Lnet/minecraft/resources/ResourceKey;", False),
    ("net/minecraft/world/level/block/Block", "defaultBlockState",
     "()Lnet/minecraft/world/level/block/state/BlockState;", False),
    ("net/minecraft/world/level/block/state/BlockBehaviour$Properties", "of",
     "()Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;", True),
    ("net/minecraft/world/level/block/state/BlockBehaviour$Properties", "strength",
     "(F)Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;", False),
]
WANT_FIELDS = [
    ("net/minecraft/world/level/Level", "OVERWORLD",
     "Lnet/minecraft/resources/ResourceKey;", True),
]

PRIM = {"B": "byte", "C": "char", "D": "double", "F": "float",
          "I": "int", "J": "long", "S": "short", "Z": "boolean", "V": "void"}

def desc_args(desc):
    # Descriptor args "(L...;I)Z" -> moj-dot list ["net.minecraft...", "int"].
    body = desc[desc.index("(") + 1:desc.index(")")]
    out, i = [], 0
    while i < len(body):
        c = body[i]
        if c == "L":
            j = body.index(";", i)
            out.append(body[i + 1:j].replace("/", "."))
            i = j + 1
        elif c == "[":
            j = i
            while body[j] == "[":
                j += 1
            if body[j] == "L":
                k = body.index(";", j)
                out.append(body[j + 1:k].replace("/", ".") + "[]" * (j - i))
                i = k + 1
            else:
                out.append(PRIM[body[j]] + "[]" * (j - i))
                i = j + 1
        else:
            out.append(PRIM[c])
            i += 1
    return out

def norm_args(a):
    a = a.strip()
    assert a.startswith("(") and a.endswith(")"), "E_SRG_DERIVE:bad args <%s>" % a
    return [x for x in a[1:-1].split(",") if x]

lines = []
for owner, mcp, desc, want_static in WANT_METHODS:
    moj_cls = owner.replace("/", ".")
    obf_owner = moj2obf[moj_cls]
    want_args = desc_args(desc)
    cands = [(k, r, n, a, o) for (k, r, n, a, o) in members[moj_cls]
             if k == "method" and n == mcp and norm_args(a) == want_args]
    assert len(cands) == 1, "E_SRG_DERIVE:mojmap member <%s %s%s> %s" % (owner, mcp, desc, cands)
    obf_name = cands[0][4]
    od = obf_desc(desc)
    tm = [m for m in classes[obf_owner]
          if m["desc"] == od and m["obf"] == obf_name]
    assert len(tm) == 1, "E_SRG_DERIVE:no tsrg member <%s %s %s>" % (obf_owner, obf_name, od)
    flags = javap_flags(obf_owner)
    assert flags.get((obf_name, od)) == want_static, \
        "E_SRG_DERIVE:javap mismatch <%s %s> %s" % (obf_owner, obf_name, flags.get((obf_name, od)))
    sd = srg_desc(od, obf2srg)
    lines.append("MD: %s/%s %s %s/%s %s" % (obf2srg[obf_owner], tm[0]["srg"], sd, owner, mcp, desc))
for owner, mcp, ftype, want_static in WANT_FIELDS:
    moj_cls = owner.replace("/", ".")
    obf_owner = moj2obf[moj_cls]
    cands = [(k, r, n, a, o) for (k, r, n, a, o) in members[moj_cls]
             if k == "field" and n == mcp and r.replace(".", "/") == ftype[1:-1].replace("L", "")]
    assert len(cands) == 1, "E_SRG_DERIVE:mojmap field <%s %s> %s" % (owner, mcp, cands)
    obf_name = cands[0][4]
    tm = [m for m in classes[obf_owner]
          if m["desc"] is None and m["obf"] == obf_name]
    assert len(tm) == 1, "E_SRG_DERIVE:no tsrg field <%s %s>" % (obf_owner, obf_name)
    flags = javap_flags(obf_owner)
    ftype_obf = obf_desc(ftype)[1:-1]
    assert flags.get((obf_name, "F:" + ftype_obf)) == want_static, \
        "E_SRG_DERIVE:javap mismatch field <%s %s>" % (obf_owner, obf_name)
    lines.append("FD: %s/%s %s/%s" % (obf2srg[obf_owner], tm[0]["srg"], owner, mcp))
assert len(lines) == 6, "E_SRG_DERIVE:want 6 lines, got %d" % len(lines)
open(outpath, "w").write("\n".join(lines) + "\n")
print("ok d3-live : narrow SRG derived (%d lines)" % len(lines))
EOF
SRG_NARROW="$D3_DIR/srg-narrow.srg"
# 2b. Pin every derived line: a derivation the SRG does not confirm is a loud
#     failure, never a silent default. Production classes stay Mojmap — only
#     these 6 members reobfuscate, exactly.
pin_method() {
  grep -q "^MD: [^ ]* [^ ]* $1 $2\$" "$SRG_NARROW" \
    || { echo "FAIL d3-live : stub member unpinned <$1 $2>"; exit 1; }
}
pin_field() {
  grep -q "^FD: [^ ]* $1\$" "$SRG_NARROW" \
    || { echo "FAIL d3-live : stub field unpinned <$1>"; exit 1; }
}
pin_method "net/minecraft/world/level/Level/setBlock" "(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"
pin_method "net/minecraft/world/level/Level/dimension" "()Lnet/minecraft/resources/ResourceKey;"
pin_method "net/minecraft/world/level/block/Block/defaultBlockState" "()Lnet/minecraft/world/level/block/state/BlockState;"
pin_method "net/minecraft/world/level/block/state/BlockBehaviour\$Properties/of" "()Lnet/minecraft/world/level/block/state/BlockBehaviour\$Properties;"
pin_method "net/minecraft/world/level/block/state/BlockBehaviour\$Properties/strength" "(F)Lnet/minecraft/world/level/block/state/BlockBehaviour\$Properties;"
pin_field "net/minecraft/world/level/Level/OVERWORLD"
[ "$(grep -c . "$SRG_NARROW")" = "6" ] \
  || { echo "FAIL d3-live : narrow map drift (want 6 lines)"; exit 1; }
echo "ok d3-live : stubs pinned to derived SRG"

# 2c. Pin every stubbed member against the provisioned jars. Forge classes
#     are never obfuscated, so names are final — presence is the pin.
#     (Vanilla-typed Forge members reference Mojmap classes in the
#     universal, which is why forge/ compiles against stubs, not it.)
#     Vanilla members are pinned against the installer-renamed game jar:
#     production classes are Mojmap, members SRG.
pin_uni() {
  "$J17/javap" -p -cp "$UNI" "$1" 2>/dev/null | grep -q "$2" \
    || { echo "FAIL d3-live : universal pin unmet <$1 :: $2>"; exit 1; }
}
pin_uni 'net.minecraftforge.event.TickEvent$LevelTickEvent' 'level'
pin_uni 'net.minecraftforge.event.TickEvent$ClientTickEvent' 'ClientTickEvent('
pin_uni 'net.minecraftforge.event.TickEvent$ServerTickEvent' 'ServerTickEvent('
pin_uni 'net.minecraftforge.event.TickEvent' 'side'
pin_uni 'net.minecraftforge.event.TickEvent' 'phase'
pin_uni 'net.minecraftforge.event.TickEvent$Phase' 'END'
pin_uni 'net.minecraftforge.common.MinecraftForge' 'EVENT_BUS'
pin_uni 'net.minecraftforge.registries.ForgeRegistries' 'BLOCKS'
pin_uni 'net.minecraftforge.registries.IForgeRegistry' 'getValue('
pin_uni 'net.minecraftforge.registries.IForgeRegistry' 'containsKey('
pin_uni 'net.minecraftforge.registries.DeferredRegister' 'create('
pin_uni 'net.minecraftforge.registries.DeferredRegister' 'register('
pin_uni 'net.minecraftforge.registries.RegistryObject' 'get('
pin_uni 'net.minecraftforge.registries.RegistryObject' 'getId('
pin_uni 'net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent' 'FMLCommonSetupEvent('
pin_game() {
  "$J17/javap" -p -cp "$SRG_GAME" "$1" 2>/dev/null | grep -q "$2" \
    || { echo "FAIL d3-live : game pin unmet <$1 :: $2>"; exit 1; }
}
pin_game 'net.minecraft.world.level.Level' 'm_7731_('
pin_game 'net.minecraft.world.level.Level' 'm_46472_('
pin_game 'net.minecraft.world.level.Level' 'f_46428_'
pin_game 'net.minecraft.world.level.block.Block' 'm_49966_('
pin_lib() {
  "$J17/javap" -p -cp "$2" "$1" 2>/dev/null | grep -q "$3" \
    || { echo "FAIL d3-live : library pin unmet <$1 :: $3>"; exit 1; }
}
pin_lib 'net.minecraftforge.fml.LogicalSide' "$FMLCORE" 'SERVER'
pin_lib 'net.minecraftforge.fml.common.Mod' "$JMLLANG" 'value()'
pin_lib 'net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext' "$JMLLANG" 'get()'
pin_lib 'net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext' "$JMLLANG" 'getModEventBus('
pin_lib 'net.minecraftforge.eventbus.api.SubscribeEvent' "$EVENTBUS" 'SubscribeEvent'
pin_lib 'net.minecraftforge.eventbus.api.IEventBus' "$EVENTBUS" 'register('
pin_lib 'net.minecraftforge.eventbus.api.IEventBus' "$EVENTBUS" 'addListener('
echo "ok d3-live : forge stubs pinned to provisioned jars"

# 3. Build all mod jars with Java 17 (--release 8 keeps the dual-runtime
#    v52 bytes). forge/ compiles against the pinned stubs (Mojmap shape);
#    the live run is the semantic arbiter.
#    R2: jar entries are sorted with timestamps clamped to EPOCH (same
#    commit + same toolchain == same bytes, see normjar), manifests carry
#    VERSION, the bridge jar embeds mods.toml.
#    These are the exact bytes the live run proves AND the release ships.
#    (No java/ stage: the pure seam ships from matou-spi, this repo carries
#    only its Forge side.)
BLD="$D3_DIR/build"
rm -rf "$BLD" \
  || { echo "FAIL d3-live : cannot clear <$BLD> (root-owned docker leftovers? point D3_DIR at a user-owned dir)"; exit 1; }
mkdir -p "$BLD/spi" "$BLD/ex1" "$BLD/mini" "$BLD/forge" "$BLD/jars" "$BLD/modstoml/META-INF"
"$J17/javac" --release 8 -nowarn -d "$BLD/spi" $(find ../spi/java/src -name '*.java')
"$J17/javac" --release 8 -nowarn -cp "$BLD/spi" -d "$BLD/ex1" $(find ../example1/java/src -name '*.java')
"$J17/javac" --release 8 -nowarn -cp "$BLD/spi" -d "$BLD/mini" $(find ../minimap/java/src -name '*.java')
"$J17/javac" --release 8 -nowarn -cp "$BLD/spi:$BLD/ex1" -d "$BLD/forge" $(find tools/live/stub forge/src -name '*.java')
sed "s/@VERSION@/$VERSION/g" forge/src/META-INF/mods.toml > "$BLD/modstoml/META-INF/mods.toml"
grep -q "version=\"$VERSION\"" "$BLD/modstoml/META-INF/mods.toml" \
  || { echo "FAIL d3-live : mods.toml stamp lost (want version $VERSION)"; exit 1; }
EPOCH="${SOURCE_DATE_EPOCH:-$(git log -1 --format=%ct)}"
printf 'Manifest-Version: 1.0\nImplementation-Version: %s\n' "$VERSION" > "$BLD/MANIFEST.MF"
find "$BLD/spi" "$BLD/ex1" "$BLD/mini" "$BLD/forge" "$BLD/modstoml" "$BLD/MANIFEST.MF" -exec touch -h -d "@$EPOCH" {} +
# mkjar: sorted entries, pinned mtimes, VERSION manifest. File lists stay
# explicit because jar -C . walks in readdir order (not reproducible).
# normjar then clamps every zip entry timestamp: the jar tool stamps
# META-INF entries with the wall clock (verified by diff), and Reobf does
# the same for its output. python3 is already a hard dependency (anvil).
# Scope: same commit + same toolchain == same bytes (zlib/JDK may vary
# across machines; use tools/live/Dockerfile to pin the toolchain).
normjar() {
  python3 - "$1" "$EPOCH" <<'EOF'
import sys, zipfile, datetime
path, epoch = sys.argv[1], int(sys.argv[2])
dt = datetime.datetime.utcfromtimestamp(epoch).timetuple()[:6]
zin = zipfile.ZipFile(path)
items = [(i, zin.read(i.filename)) for i in zin.infolist()]
zin.close()
zout = zipfile.ZipFile(path + ".norm", "w", zipfile.ZIP_DEFLATED)
for info, data in items:
    info.date_time = dt
    info.create_system = 0
    zout.writestr(info, data)
zout.close()
EOF
  mv "$1.norm" "$1"
}
mkjar() {
  out="$1"; stage="$2"
  files=$(cd "$stage" && find . -type f | LC_ALL=C sort)
  # Controlled tree, no spaces in class paths: word-splitting is intended.
  (cd "$stage" && "$J17/jar" cfm "$out" "$BLD/MANIFEST.MF" $files)
  normjar "$out"
}
mkjar "$BLD/jars/matou-spi.jar" "$BLD/spi"
mkjar "$BLD/jars/matou-example1.jar" "$BLD/ex1"
mkjar "$BLD/jars/matou-minimap.jar" "$BLD/mini"
rm -rf "$BLD/bridgemod" && mkdir -p "$BLD/bridgemod"
cp -r "$BLD/forge/"* "$BLD/bridgemod/"
# Stubs are compile-only: they must never ship (a fake Block on the
# runtime classpath would shadow vanilla). Refuse loudly if leaked.
rm -rf "$BLD/bridgemod/net"
if [ -e "$BLD/bridgemod/net" ]; then
  echo "FAIL d3-live : stub leak into mod jar"
  exit 1
fi
# 1.20.1 modules isolate every mods/ jar (securejarhandler): a slim bridge
# jar cannot see matou-spi.jar next to it (NoClassDefFoundError, found live
# in D3). The bridge ships FAT — spi + example1 classes embedded, same
# sources, same bytes provenance. The slim jars below stay dev/library
# artifacts (and the java-52 contract still checks them).
cp -r "$BLD/spi/"* "$BLD/ex1/"* "$BLD/bridgemod/"
cp "$BLD/modstoml/META-INF/mods.toml" "$BLD/bridgemod/META-INF/mods.toml" 2>/dev/null \
  || { mkdir -p "$BLD/bridgemod/META-INF" && cp "$BLD/modstoml/META-INF/mods.toml" "$BLD/bridgemod/META-INF/mods.toml"; }
touch -h -d "@$EPOCH" "$BLD/bridgemod/META-INF/mods.toml"
mkjar "$BLD/jars/matoubridge.jar" "$BLD/bridgemod"
echo "ok d3-live : jars built (VERSION=$VERSION)"

# 4. Reobfuscate Mojmap-named member refs to SRG (ForgeGradle reobf
#    equivalent: production vanilla declares SRG member names, so
#    un-reobfed jars die with NoSuchMethodError — found live in B3, never
#    again silently). Classes stay Mojmap (production classes are Mojmap),
#    so only the 6 narrow-map members move; Forge refs pass through
#    untouched (never obfuscated).
"$J17/javac" --release 8 -nowarn -cp "$ASM:$ASM_COMMONS" -d "$BLD" tools/live/Reobf.java
"$J17/java" -cp "$BLD:$ASM:$ASM_COMMONS" Reobf "$SRG_NARROW" "$BLD/jars/matoubridge.jar" "$BLD/jars/matoubridge-reobf.jar"
normjar "$BLD/jars/matoubridge-reobf.jar"
echo "ok d3-live : bridge reobfuscated"

# Annotation visibility: eventbus discovers handlers through
# RuntimeVisibleAnnotations. A stub whose retention drifts from the real
# annotation (RUNTIME) would emit invisible usages and register nothing,
# silently — found live in D3. Refuse any invisible annotation in our
# classes and demand the two visible ones on the mod class.
annot_visible() {
  awk -v want="$2" '/RuntimeVisibleAnnotations/{v=4} v && index($0, want){ok=1} {if (v > 0) v--} END{exit !ok}' "$1" \
    || { echo "FAIL d3-live : annotation <$2> not visible in <$3>"; exit 1; }
}
for c in fr.iamacat.bridge.forge.MatouBridgeMod fr.iamacat.bridge.forge.PackWire fr.iamacat.bridge.forge.WorldCellSink; do
  "$J17/javap" -v -cp "$BLD/jars/matoubridge-reobf.jar" "$c" > "$BLD/annot.txt" 2>/dev/null \
    || { echo "FAIL d3-live : javap on reobf <$c>"; exit 1; }
  grep -q "RuntimeInvisibleAnnotations" "$BLD/annot.txt" \
    && { echo "FAIL d3-live : invisible annotation in <$c> (stub retention drift)"; exit 1; }
done
"$J17/javap" -v -cp "$BLD/jars/matoubridge-reobf.jar" fr.iamacat.bridge.forge.MatouBridgeMod > "$BLD/annot.txt" 2>/dev/null
annot_visible "$BLD/annot.txt" "net.minecraftforge.eventbus.api.SubscribeEvent" "MatouBridgeMod"
annot_visible "$BLD/annot.txt" "net.minecraftforge.fml.common.Mod" "MatouBridgeMod"
echo "ok d3-live : annotations visible"

# Dual-runtime contract (Java 8 vanilla + modern JVM): shipped bytes stay
# major 52 with no module-info and no multi-release entries — v52 loads on
# 8 and 17 alike. Anything newer fails loudly here, on both the live and
# the release path, never silently.
python3 - "$BLD/jars" <<'EOF'
import sys, zipfile, struct
jars = ["matou-spi.jar", "matou-example1.jar", "matou-minimap.jar",
        "matoubridge-reobf.jar"]
bad = []
for j in jars:
    zf = zipfile.ZipFile("%s/%s" % (sys.argv[1], j))
    for n in zf.namelist():
        if n == "module-info.class" or n.startswith("META-INF/versions/"):
            bad.append("%s!%s (multi-release)" % (j, n))
        elif n.endswith(".class"):
            major = struct.unpack(">H", zf.read(n)[6:8])[0]
            if major != 52:
                bad.append("%s!%s (major %d, want 52)" % (j, n, major))
if bad:
    print("FAIL d3-live : java-52 contract broken:")
    print("\n".join("  " + b for b in bad))
    sys.exit(1)
print("ok d3-live : java 52 contract (4 jars, no multi-release)")
EOF

# R2 release assembly: versioned server drop, then exit before booting.
# The Mojmap-named bridge jar never ships (only the reobf one is copied).
# The drop's mod is the FAT bridge (spi+example1 embedded, see step 3);
# the slim jars ship alongside as dev/library artifacts.
if [ "${BUILD_ONLY:-}" = "1" ]; then
  rm -rf dist && mkdir -p dist/matou-content
  cp "$BLD/jars/matou-spi.jar" "dist/matou-spi-$VERSION.jar"
  cp "$BLD/jars/matou-example1.jar" "dist/matou-example1-$VERSION.jar"
  cp "$BLD/jars/matou-minimap.jar" "dist/matou-minimap-$VERSION.jar"
  cp "$BLD/jars/matoubridge-reobf.jar" "dist/matoubridge-$VERSION.jar"
  cp ../example1/content/owned.matou ../example1/content/additive.matou ../example1/content/structure.matou dist/matou-content/
  printf '# Copy to <server>/config/matoubridge/packs.cfg and replace <SERVER>.\n# Wire y=63 keeps plane cells on their own slice, off the structure slices (64..65).\n# The wire block is the registered custom ore (DeferredRegister queues example1:my_ore from owned.matou, the fill lands before setup binds resolve it); aliases stay vanilla stone.\nfr.iamacat.example1.ExamplePack 63 example1:my_ore ownedFile=<SERVER>/matou-content/owned.matou scatterFile=<SERVER>/matou-content/additive.matou structureFile=<SERVER>/matou-content/structure.matou block.example1.structures:hut_wall=minecraft:stone block.example1.structures:hut_roof=minecraft:stone\n' > dist/packs.cfg.example
  (cd dist && sha256sum "matou-spi-$VERSION.jar" "matou-example1-$VERSION.jar" "matou-minimap-$VERSION.jar" "matoubridge-$VERSION.jar" matou-content/owned.matou matou-content/additive.matou matou-content/structure.matou packs.cfg.example > SHA256SUMS.txt)
  (cd dist && sha256sum -c SHA256SUMS.txt)
  echo "ok r2-release : dist/ assembled (VERSION=$VERSION)"
  exit 0
fi

# 5. Deploy the mod + content + packs.cfg, boot the server. Only the FAT
# bridge deploys (module isolation, see step 3) — slim jars never boot.
mkdir -p "$SERV/mods" "$SERV/config/matoubridge"
rm -f "$SERV/mods/"*.jar
cp "$BLD/jars/matoubridge-reobf.jar" "$SERV/mods/matoubridge.jar"
rm -rf "$SERV/matou-content" && cp -r ../example1/content "$SERV/matou-content"
# Wire y=63: plane cells stay on their own slice, off the structure
# slices (64..65), so the verdict stays per-shape sensitive despite the
# set collapse (a 2D and a 3D cell can share x,z, never y). The wire
# block is the registered custom ore (the constructor queues it from
# owned.matou, the deferred fill registers it before setup binds
# resolve it).
printf 'fr.iamacat.example1.ExamplePack 63 example1:my_ore ownedFile=%s/matou-content/owned.matou scatterFile=%s/matou-content/additive.matou structureFile=%s/matou-content/structure.matou block.example1.structures:hut_wall=minecraft:stone block.example1.structures:hut_roof=minecraft:stone\n' "$SERV" "$SERV" "$SERV" > "$SERV/config/matoubridge/packs.cfg"
echo "eula=true" > "$SERV/eula.txt"
printf 'online-mode=false\nlevel-type=minecraft:flat\ngamemode=1\ndifficulty=0\nmotd=D3 live proof\nmax-tick-time=-1\n' > "$SERV/server.properties"
rm -rf "$SERV/world" "$SERV/logs"
set +e
(cd "$SERV" && timeout "$BOOT_SECS" sh run.sh nogui > boot-d3.log 2>&1)
code=$?
set -e
[ "$code" -eq 124 ] || { echo "FAIL d3-live : server exited early (code $code, see $SERV/boot-d3.log)"; exit 1; }
echo "ok d3-live : server ran ($BOOT_SECS s)"

# 6. Fail loudly on any runtime refusal or linkage error (stdout log plus
#    the rolling server log — Forge splits output across both).
LOGS="$SERV/boot-d3.log"
[ -f "$SERV/logs/latest.log" ] && LOGS="$LOGS $SERV/logs/latest.log"
if grep -a -q "NoSuchMethodError\|NoSuchFieldError\|E_FORGE\|E_BRIDGE\|E_EXAMPLE\|E_REG\|Encountered an unexpected exception" $LOGS; then
  echo "FAIL d3-live : runtime refusal (see $SERV/boot-d3.log)"
  grep -a -m5 "NoSuchMethodError\|NoSuchFieldError\|E_FORGE\|E_BRIDGE\|E_EXAMPLE\|E_REG\|Caused by" $LOGS
  exit 1
fi
grep -a -q "matoubridge" $LOGS \
  || { echo "FAIL d3-live : mod never loaded"; exit 1; }
echo "ok d3-live : bind clean, ticks clean"
# Registration proof: the setup-time verify line carries the registry key
# (1.20.1 has no numeric block ids — the anvil probe reads namespaced
# names, and this line proves the custom name resolved through the
# registry, never defaulted).
grep -a -q '\[MatouBridge\] registered <example1:my_ore> id example1:my_ore' $LOGS \
  || { echo "FAIL d3-live : my_ore registration line absent from boot log (deferred fill never registered? see $SERV/boot-d3.log)"; exit 1; }
echo "ok d3-live : my_ore registered ($(grep -a -o '\[MatouBridge\] registered <example1:my_ore> id [^ ]*' $LOGS | tail -n 1))"

# 7. Positive proof: world blocks in chunks (0..1, -1..1) at y=63..65 must
#    equal the pure decision union — plane cells carry the registered
#    custom ore, volume cells their alias stone, nothing foreign, nothing
#    missing. Plane cells land at the wire y=63, volume cells at their own
#    y=64..65; structure offsets reach x,z=17, and the hut anchor z=-4
#    spills into chunk row -1 (region r.0.-1.mca) — hence the 6-chunk,
#    3-slice read. (Same geometry as C3: the mod writes force chunk
#    generation around the origin regardless of world spawn.)
"$J17/javac" --release 8 -nowarn -cp "$BLD/spi:$BLD/ex1" -d "$BLD" tools/live/CellUnion.java
"$J17/java" -cp "$BLD:$BLD/spi:$BLD/ex1" CellUnion \
  "$SERV/config/matoubridge/packs.cfg" 4000 "$BLD/union.txt"
: > "$BLD/world.txt"
for spec in "r.0.0.mca 0 0" "r.0.0.mca 1 0" "r.0.0.mca 0 1" \
    "r.0.0.mca 1 1" "r.0.-1.mca 0 -1" "r.0.-1.mca 1 -1"; do
  set -- $spec
  for y in 63 64 65; do
    python3 tools/live/anvil.py "$SERV/world/region/$1" "$2" "$3" "$y" \
      | awk -v cx="$2" -v cz="$3" -v y="$y" \
        '{split($1, a, ","); print (cx*16+a[1])" "y" "(cz*16+a[2])" "$2}' \
      >> "$BLD/world.txt"
  done
done
python3 - "$BLD/union.txt" "$BLD/world.txt" "$SERV/config/matoubridge/packs.cfg" <<'EOF'
import sys
# Names resolve through packs.cfg itself (wire block plus every
# block.<ref>=<name> alias value) — never hardcoded, never guessed. A
# world name outside that set fails loudly (extend the wire explicitly).
wire_y, wire_block, allowed = None, None, set()
for line in open(sys.argv[3]):
    line = line.strip()
    if line and not line.startswith("#"):
        toks = line.split()
        wire_y, wire_block = int(toks[1]), toks[2]
        allowed.add(wire_block)
        for tok in toks[3:]:
            if tok.startswith("block.") and "=" in tok:
                allowed.add(tok.split("=", 1)[1])
if wire_y is None:
    print("FAIL d3-live : no wire in packs.cfg")
    sys.exit(1)
u = {}
for line in open(sys.argv[1]):
    cell = line.split()[0]
    parts = cell.split(",")
    if len(parts) == 3 and ":" in parts[2]:
        z, bname = parts[2].split(":", 1)
        pos = (int(parts[0]), int(parts[1]), int(z))
    else:
        x, z = cell.split(",")
        pos, bname = (int(x), wire_y, int(z)), wire_block
    if bname not in allowed:
        print("FAIL d3-live : union block <%s> outside packs.cfg set (extend the wire, never guess)" % bname)
        sys.exit(1)
    u[pos] = bname
rows = [l.split() for l in open(sys.argv[2])]
w = {(int(x), int(y), int(z)): n for x, y, z, n in rows}
if not w:
    print("FAIL d3-live : world empty at y=63..65 (no tick applied?)")
    sys.exit(1)
if set(w.values()) - allowed:
    print("FAIL d3-live : foreign blocks %s" % sorted(set(w.values()) - allowed))
    sys.exit(1)
bad = {p: (w[p], u.get(p)) for p in w if u.get(p) != w[p]}
if bad:
    print("FAIL d3-live : name mismatch at %s (want pure union names)" % sorted(bad.items())[:5])
    sys.exit(1)
if u.keys() - w.keys():
    print("FAIL d3-live : pure cells missing from world (%d)" % len(u.keys() - w.keys()))
    sys.exit(1)
print("ok d3-live : world == pure union (%d cells, names %s)" % (len(w), sorted(set(w.values()))))
EOF
