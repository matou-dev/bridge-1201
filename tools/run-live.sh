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
# Shared harness steps (hub SSOT, thin version wrapper — hub
# decisions/LIVE_SHELL_COMMON.md): sibling-absent fails loud, same shim
# discipline as tools/run-client.sh.
[ -f ../hub/tools/live-common.sh ] \
  || { echo "FAIL d3-live : hub sibling absent (clone hub next to bridge-1201 — live steps source ../hub/tools/live-common.sh)"; exit 1; }
[ -f ../hub/tools/live-derive.sh ] \
  || { echo "FAIL d3-live : hub sibling absent (clone hub next to bridge-1201 — derive steps source ../hub/tools/live-derive.sh)"; exit 1; }
# shellcheck disable=SC1091
. ../hub/tools/live-common.sh
# shellcheck disable=SC1091
. ../hub/tools/live-derive.sh
live_init "d3-live"
# Era-bound adapters: the hub libs own the mechanics; these bind the
# caller-owned map/jars so every pin/jar call site below stays byte-identical.
# (pin_game stays local: the SRG game jar has no lib helper.)
pin_method() { live_pin_method "$SRG_NARROW" "$@"; }
pin_field() { live_pin_field "$SRG_NARROW" "$@"; }
pin_uni() { live_pin_uni "$J17" "$UNI" "$@"; }
mkjar() { live_mkjar "$1" "$2" "$BLD/MANIFEST.MF" "$J17/jar" "$EPOCH"; }
normjar() { live_normjar "$1" "$EPOCH"; }
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
# 1a. D3_DIR preflight (docker root-owned leftovers fail fast, loudly).
live_preflight_dir "D3_DIR" "$D3_DIR"
live_fetch "$D3_DIR/forge-installer.jar" "$FORGE_URL" "$INSTALLER_SHA1" "${D3_OFFLINE:-0}"
LIB="$SERV/libraries"
UNI="$LIB/net/minecraftforge/forge/1.20.1-47.2.0/forge-1.20.1-47.2.0-universal.jar"
if [ ! -f "$UNI" ]; then
  live_install_server "$SERV" "$D3_DIR/forge-installer.jar" "$J17"
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
live_fetch "$D3_DIR/mcp_config-1.20.1-20230612.114412.zip" "$MCP_CONFIG_URL" "$MCP_CONFIG_SHA1" "${D3_OFFLINE:-0}"
live_fetch "$D3_DIR/server-mappings.txt" "$MOJMAPS_URL" "$MOJMAPS_SHA1" "${D3_OFFLINE:-0}"
# Client mappings (one row needs them — see step 2): Mojang official
# moj<->obf map for CLIENT classes, pinned by tools/autoplay/
# client-mappings-pin.txt (same repo, referenced here — never copied, hub
# doctrine). Cached beside server.txt, D3_OFFLINE-safe.
CLIMAP_PIN="tools/autoplay/client-mappings-pin.txt"
CLIMAP_URL="$(sed -n 's/^URL=//p' "$CLIMAP_PIN")"
CLIMAP_SHA1="$(sed -n 's/^SHA1=//p' "$CLIMAP_PIN")"
CLIMAP_SIZE="$(sed -n 's/^SIZE=//p' "$CLIMAP_PIN")"
[ -n "$CLIMAP_URL" ] && [ -n "$CLIMAP_SHA1" ] && [ -n "$CLIMAP_SIZE" ] \
  || { echo "FAIL d3-live : malformed <$CLIMAP_PIN> (want URL= + SHA1= + SIZE=)"; exit 1; }
if [ ! -f "$D3_DIR/client-mappings.txt" ]; then
  if [ "${D3_OFFLINE:-}" = "1" ]; then
    echo "FAIL d3-live : offline and client mappings absent ($D3_DIR/client-mappings.txt)"
    exit 1;
  fi
  curl -sL -o "$D3_DIR/client-mappings.txt" "$CLIMAP_URL" \
    || { echo "FAIL d3-live : client mappings download"; exit 1; }
fi
echo "$CLIMAP_SHA1  $D3_DIR/client-mappings.txt" | sha1sum -c - >/dev/null 2>&1 \
  || { echo "FAIL d3-live : client mappings sha1 drift (want $CLIMAP_SHA1)"; exit 1; }
[ "$(wc -c < "$D3_DIR/client-mappings.txt")" = "$CLIMAP_SIZE" ] \
  || { echo "FAIL d3-live : client mappings size drift (want $CLIMAP_SIZE)"; exit 1; }
# Pinned vanilla client (tools/autoplay/client-pin.txt — the AUTOPLAY
# companion derive already trusts it; this script reuses the same bytes,
# never its own pin): client-only vanilla members (net/minecraft/client/*,
# com/mojang/*, e.g. the renderer tranche's Minecraft/getCameraEntity and
# PoseStack/last) cannot javap-verify against the notch SERVER jars (no
# client classes in them), so the derive below checks those rows against
# these bytes instead. Same offline rule as every other fetch above.
CLIENT_PIN_URL="$(sed -n 's/^URL=//p' tools/autoplay/client-pin.txt)"
CLIENT_PIN_SHA1="$(sed -n 's/^SHA1=//p' tools/autoplay/client-pin.txt)"
CLIENT_PIN_SIZE="$(sed -n 's/^SIZE=//p' tools/autoplay/client-pin.txt)"
[ -n "$CLIENT_PIN_URL" ] && [ -n "$CLIENT_PIN_SHA1" ] && [ -n "$CLIENT_PIN_SIZE" ] \
  || { echo "FAIL d3-live : malformed tools/autoplay/client-pin.txt (want URL= + SHA1= + SIZE=)"; exit 1; }
MCCLIENT="$D3_DIR/vanilla-client.jar"
if [ ! -f "$MCCLIENT" ] || ! echo "$CLIENT_PIN_SHA1  $MCCLIENT" | sha1sum -c - >/dev/null 2>&1; then
  if [ "${D3_OFFLINE:-}" = "1" ]; then
    echo "FAIL d3-live : offline and vanilla client absent ($MCCLIENT)"
    exit 1
  fi
  echo "note d3-live : fetching pinned vanilla client (network once, $CLIENT_PIN_SHA1)"
  rm -f "$MCCLIENT"
  curl -sL -o "$MCCLIENT" "$CLIENT_PIN_URL" \
    || { echo "FAIL d3-live : vanilla client download failed"; exit 1; }
  echo "$CLIENT_PIN_SHA1  $MCCLIENT" | sha1sum -c - >/dev/null 2>&1 \
    || { echo "FAIL d3-live : vanilla client sha1 drift (want $CLIENT_PIN_SHA1, never silent upgrade)"; exit 1; }
fi
[ "$(wc -c < "$MCCLIENT")" = "$CLIENT_PIN_SIZE" ] \
  || { echo "FAIL d3-live : vanilla client size drift (want $CLIENT_PIN_SIZE)"; exit 1; }
echo "ok d3-live : vanilla client pinned ($CLIENT_PIN_SHA1)"
echo "ok d3-live : server provisioned (pins verified)"

# 2. Derive the narrow Mojmap->SRG map from pinned bytes: server.txt gives
#    moj<->obf (classes and members), joined.tsrg v2 gives obf<->srg, and the
#    vanilla server jar disambiguates via javap (exactly-one assert per
#    member, loud otherwise). The map covers every vanilla member our
#    forge/ bytecode references (verified by constant-pool scan at D3 time:
#    setBlock, dimension, OVERWORLD, defaultBlockState, plus the
#    registration tranche: Properties.of, strength, plus the loot tranche:
#    ServerLevel/addFreshEntity, Entity/level/getX/getY/getZ,
#    Level/isClientSide, Vec3i/getX/getY/getZ,
#    BlockStateBase/getBlock, Items/DIAMOND, plus the spawn tranche:
#    Entity/getId/isAlive/moveTo, LivingEntity/getAttribute/setHealth/
#    getMaxHealth, AttributeInstance/setBaseValue,
#    EntityGetter/getEntitiesOfClass, EntityType/PIG,
#    Attributes/MAX_HEALTH, plus the repop tranche (hub
#    decisions/REPOP_SPIKE.md): no new member — the stone resolve rides
#    ForgeRegistries.BLOCKS containsKey/getValue (universal pins below),
#    the landing rides Level.setBlock/Block.defaultBlockState, and the
#    break read rides the loot rows (Level/isClientSide/dimension,
#    Vec3i/getX/getY/getZ, BlockStateBase/getBlock), plus the custom
#    entity tranche (hub
#    decisions/SPAWN.md): EntityType$Builder/of/sized/clientTrackingRange/
#    build, Pig/createAttributes, AttributeSupplier$Builder/build, and the
#    two vanilla SAMs our lambdas/method-refs target
#    (EntityType$EntityFactory/create,
#    EntityRendererProvider/create — see the Reobf note in
#    tools/live/Reobf.java: an invokedynamic names its SAM in the
#    compiled namespace, so the shipped bytes must carry the SRG name or
#    LambdaMetafactory spins a class the runtime interface does not
#    declare — AbstractMethodError, measured live on 47.2.0, never
#    silent. The 1165/1122 SAMs need no row: their runtime names are
#    stable by construction there).
#    MobCategory/CREATURE needs no row (joined.tsrg v2 maps obf b
#    straight to CREATURE — runtime name identical, passthrough by
#    construction like Forge classes); other client refs (PigRenderer)
#    and Forge refs (ENTITY_TYPES, EntityAttributeCreationEvent,
#    EntityRenderersEvent) pass the server Reobf untouched (unmapped refs
#    pass through — same split as the 1165 custom entity tranche, client
#    link measured at live time).
#    Plus the renderer tranche (hub decisions/MATOU_MODEL.md +
#    GL_INSTANCING_ADAPTER.md): frame interpolation rides the Entity
#    xo/yo/zo + yRotO/xRotO olds with the getYRot/getXRot currents
#    (server classes — same server.txt + inner-jar javap leg as every
#    row above); the view entity rides Minecraft.getCameraEntity, the
#    world behind Minecraft.level (ClientLevel-typed), and both shader
#    matrices ride PoseStack.last/pose (Mojang class, SRG members at
#    runtime like every Mojmap-era member) — client-only rows verified
#    against the pinned vanilla client jar above (1165 split, never
#    defaulted). Iteration names no ClientLevel member (the renderer
#    polls the already-mapped EntityGetter.getEntitiesOfClass), JOML and
#    LWJGL are libraries (never obfuscated, no rows), and the frame
#    event is Forge (presence-pinned below, never obfuscated).
#    The provider SAM derives from client.txt (client classes never ship
#    in server.txt or the server jars — same split as the autoplay
#    derive, which owns the client javap leg; here the exact-one asserts
#    on both hops plus the live client run lock it, never a bare recall).
#    Production classes stay Mojmap (installer MERGE_MAPPING keeps classes
#    official) — only members reobfuscate, so no class lines are needed.
# Mechanics live in hub/tools/live-derive.sh (era 1.20), rows in
# tools/live/want.tsv — same 54 lines, byte-identical output.
SRG_NARROW="$D3_DIR/srg-narrow.srg"
live_derive_mojmaps "$D3_DIR/mcp_config-1.20.1-20230612.114412.zip" "$D3_DIR/server-mappings.txt" "$MC_INNER" "$J17/javap" "$SRG_NARROW" "$D3_DIR/client-mappings.txt" "$MCCLIENT" "tools/live/want.tsv"
# 2b. Pin every derived line: a derivation the SRG does not confirm is a loud
#     failure, never a silent default. Production classes stay Mojmap — only
#     these 54 members reobfuscate, exactly.
pin_method "net/minecraft/world/level/Level/setBlock" "(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"
pin_method "net/minecraft/world/level/Level/dimension" "()Lnet/minecraft/resources/ResourceKey;"
pin_method "net/minecraft/world/level/block/Block/defaultBlockState" "()Lnet/minecraft/world/level/block/state/BlockState;"
pin_method "net/minecraft/world/level/block/state/BlockBehaviour\$Properties/of" "()Lnet/minecraft/world/level/block/state/BlockBehaviour\$Properties;"
pin_method "net/minecraft/world/level/block/state/BlockBehaviour\$Properties/strength" "(F)Lnet/minecraft/world/level/block/state/BlockBehaviour\$Properties;"
pin_field "net/minecraft/world/level/Level/OVERWORLD"
pin_method "net/minecraft/server/level/ServerLevel/addFreshEntity" "(Lnet/minecraft/world/entity/Entity;)Z"
pin_method "net/minecraft/world/entity/Entity/level" "()Lnet/minecraft/world/level/Level;"
pin_method "net/minecraft/world/entity/Entity/getX" "()D"
pin_method "net/minecraft/world/entity/Entity/getY" "()D"
pin_method "net/minecraft/world/entity/Entity/getZ" "()D"
pin_method "net/minecraft/world/level/Level/isClientSide" "()Z"
pin_method "net/minecraft/core/Vec3i/getX" "()I"
pin_method "net/minecraft/core/Vec3i/getY" "()I"
pin_method "net/minecraft/core/Vec3i/getZ" "()I"
pin_method "net/minecraft/world/level/block/state/BlockBehaviour\$BlockStateBase/getBlock" "()Lnet/minecraft/world/level/block/Block;"
pin_method "net/minecraft/world/entity/Entity/getId" "()I"
pin_method "net/minecraft/world/entity/Entity/isAlive" "()Z"
pin_method "net/minecraft/world/entity/Entity/moveTo" "(DDDFF)V"
pin_method "net/minecraft/world/entity/LivingEntity/getAttribute" "(Lnet/minecraft/world/entity/ai/attributes/Attribute;)Lnet/minecraft/world/entity/ai/attributes/AttributeInstance;"
pin_method "net/minecraft/world/entity/LivingEntity/setHealth" "(F)V"
pin_method "net/minecraft/world/entity/LivingEntity/getMaxHealth" "()F"
pin_method "net/minecraft/world/entity/ai/attributes/AttributeInstance/setBaseValue" "(D)V"
pin_method "net/minecraft/world/level/EntityGetter/getEntitiesOfClass" "(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"
pin_method "net/minecraft/world/entity/EntityType\$Builder/of" "(Lnet/minecraft/world/entity/EntityType\$EntityFactory;Lnet/minecraft/world/entity/MobCategory;)Lnet/minecraft/world/entity/EntityType\$Builder;"
pin_method "net/minecraft/world/entity/EntityType\$Builder/sized" "(FF)Lnet/minecraft/world/entity/EntityType\$Builder;"
pin_method "net/minecraft/world/entity/EntityType\$Builder/clientTrackingRange" "(I)Lnet/minecraft/world/entity/EntityType\$Builder;"
pin_method "net/minecraft/world/entity/EntityType\$Builder/build" "(Ljava/lang/String;)Lnet/minecraft/world/entity/EntityType;"
pin_method "net/minecraft/world/entity/animal/Pig/createAttributes" "()Lnet/minecraft/world/entity/ai/attributes/AttributeSupplier\$Builder;"
pin_method "net/minecraft/world/entity/ai/attributes/AttributeSupplier\$Builder/build" "()Lnet/minecraft/world/entity/ai/attributes/AttributeSupplier;"
pin_method "net/minecraft/world/entity/EntityType\$EntityFactory/create" "(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;)Lnet/minecraft/world/entity/Entity;"
pin_method "net/minecraft/client/renderer/entity/EntityRendererProvider/create" "(Lnet/minecraft/client/renderer/entity/EntityRendererProvider\$Context;)Lnet/minecraft/client/renderer/entity/EntityRenderer;"
pin_method "net/minecraft/world/item/Item\$Properties/stacksTo" "(I)Lnet/minecraft/world/item/Item\$Properties;"
pin_method "net/minecraft/world/entity/Entity/getYRot" "()F"
pin_method "net/minecraft/world/entity/Entity/getXRot" "()F"
# Combat tranche (hub decisions/VIRTUAL_HITBOXES.md, server weakspot
# hook): Entity/getEyePosition + getLookAngle (the attacker eye/look
# surface as Vec3, owner Entity — 1.20.1-native, no height arithmetic),
# DamageSource/getEntity (the true attacker behind the hurt source)
# and Vec3/x/y/z (the look/eye components — the 1.12 Vec3d owner does
# not port). The narrow map grows 48 -> 54 lines.
pin_method "net/minecraft/world/entity/Entity/getEyePosition" "()Lnet/minecraft/world/phys/Vec3;"
pin_method "net/minecraft/world/entity/Entity/getLookAngle" "()Lnet/minecraft/world/phys/Vec3;"
pin_method "net/minecraft/world/damagesource/DamageSource/getEntity" "()Lnet/minecraft/world/entity/Entity;"
# Loop form (not one pin per line): eSLOC ceiling discipline, same pins
# table-driven — every member name stays literal and grep-able.
for f in x y z; do pin_field "net/minecraft/world/phys/Vec3/$f"; done
pin_field "net/minecraft/world/entity/Entity/xo"
pin_field "net/minecraft/world/entity/Entity/yo"
pin_field "net/minecraft/world/entity/Entity/zo"
pin_field "net/minecraft/world/entity/Entity/yRotO"
pin_field "net/minecraft/world/entity/Entity/xRotO"
# Renderer client-only rows (hub decisions/MATOU_MODEL.md +
# GL_INSTANCING_ADAPTER.md): every net/minecraft/client/* + com/mojang/*
# member the client-only InstancedMeshRenderer touches. Anchors are
# client.txt+tsrg+javap-derived above (same measure discipline — never
# recalled): getInstance is the static m_91087_, the level field is the
# ClientLevel-typed f_91073_, getCameraEntity is m_91288_
# (Entity-typed, no Camera reads), matrices ride PoseStack.last/pose
# (m_85850_/m_252922_, org.joml at the end — no Mojang-math write).
pin_method "net/minecraft/client/Minecraft/getInstance" "()Lnet/minecraft/client/Minecraft;"
pin_method "net/minecraft/client/Minecraft/getCameraEntity" "()Lnet/minecraft/world/entity/Entity;"
pin_field "net/minecraft/client/Minecraft/level"
pin_method "com/mojang/blaze3d/vertex/PoseStack/last" "()Lcom/mojang/blaze3d/vertex/PoseStack\$Pose;"
pin_method "com/mojang/blaze3d/vertex/PoseStack\$Pose/pose" "()Lorg/joml/Matrix4f;"
pin_field "net/minecraft/world/item/Items/DIAMOND"
pin_field "net/minecraft/world/entity/EntityType/PIG"
pin_field "net/minecraft/world/entity/ai/attributes/Attributes/MAX_HEALTH"
[ "$(grep -c . "$SRG_NARROW")" = "54" ] \
  || { echo "FAIL d3-live : narrow map drift (want 54 lines)"; exit 1; }
echo "ok d3-live : stubs pinned to derived SRG"

# 2c. Pin every stubbed member against the provisioned jars. Forge classes
#     are never obfuscated, so names are final — presence is the pin.
#     (Vanilla-typed Forge members reference Mojmap classes in the
#     universal, which is why forge/ compiles against stubs, not it.)
#     Vanilla members are pinned against the installer-renamed game jar:
#     production classes are Mojmap, members SRG.
pin_uni 'net.minecraftforge.event.TickEvent$LevelTickEvent' 'level'
pin_uni 'net.minecraftforge.event.TickEvent$ClientTickEvent' 'ClientTickEvent('
pin_uni 'net.minecraftforge.event.TickEvent$ServerTickEvent' 'ServerTickEvent('
pin_uni 'net.minecraftforge.event.TickEvent' 'side'
pin_uni 'net.minecraftforge.event.TickEvent' 'phase'
pin_uni 'net.minecraftforge.event.TickEvent$Phase' 'END'
pin_uni 'net.minecraftforge.common.MinecraftForge' 'EVENT_BUS'
pin_uni 'net.minecraftforge.registries.ForgeRegistries' 'BLOCKS'
pin_uni 'net.minecraftforge.registries.ForgeRegistries' 'ITEMS'
pin_uni 'net.minecraftforge.registries.IForgeRegistry' 'getValue('
pin_uni 'net.minecraftforge.registries.IForgeRegistry' 'containsKey('
pin_uni 'net.minecraftforge.registries.DeferredRegister' 'create('
pin_uni 'net.minecraftforge.registries.DeferredRegister' 'register('
pin_uni 'net.minecraftforge.registries.RegistryObject' 'get('
pin_uni 'net.minecraftforge.registries.RegistryObject' 'getId('
pin_uni 'net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent' 'FMLCommonSetupEvent('
pin_uni 'net.minecraftforge.event.level.BlockEvent' 'getLevel('
pin_uni 'net.minecraftforge.event.level.BlockEvent' 'getPos('
pin_uni 'net.minecraftforge.event.level.BlockEvent' 'getState('
pin_uni 'net.minecraftforge.event.level.BlockEvent$BreakEvent' 'BreakEvent('
pin_uni 'net.minecraftforge.event.entity.living.LivingEvent' 'getEntity('
pin_uni 'net.minecraftforge.event.entity.living.LivingDropsEvent' 'LivingDropsEvent('
for m in 'LivingHurtEvent(' 'getSource(' 'getAmount(' 'setAmount('; do pin_uni 'net.minecraftforge.event.entity.living.LivingHurtEvent' "$m"; done
pin_uni 'net.minecraftforge.event.entity.EntityJoinLevelEvent' 'EntityJoinLevelEvent('
pin_uni 'net.minecraftforge.event.entity.EntityJoinLevelEvent' 'getLevel('
pin_uni 'net.minecraftforge.event.entity.EntityEvent' 'getEntity('
# Custom entity tranche (hub decisions/SPAWN.md): the generic beast
# queues through DeferredRegister on ForgeRegistries.ENTITY_TYPES (same
# create/register calls the block tranche already pins), the setup-time
# tripwire reads it back, the attribute map lands on the mod-bus
# EntityAttributeCreationEvent, and the client-only renderer rides the
# mod-bus RegisterRenderers event (the single (Context) pig ctor,
# measured from the pinned SRG client jar) through a dist-filtered
# nested subscriber. Forge names are runtime-final: presence is the
# pin. The Mod/EventBusSubscriber shape rides javafmllanguage (same
# split as Mod/FMLJavaModLoadingContext below).
pin_uni 'net.minecraftforge.registries.ForgeRegistries' 'ENTITY_TYPES'
pin_uni 'net.minecraftforge.event.entity.EntityAttributeCreationEvent' 'put('
pin_uni 'net.minecraftforge.client.event.EntityRenderersEvent$RegisterRenderers' 'registerEntityRenderer('
# Renderer tranche (hub decisions/GL_INSTANCING_ADAPTER.md): the client
# frame event the instanced overlay subscribes to (Forge-added, never
# obfuscated — presence is the pin, same as every row above; 1.20.1
# fires RenderLevelStageEvent, gated on Stage.AFTER_ENTITIES, with the
# partial tick, the PoseStack and the org.joml projection matrix on the
# event — the 1.16.5 RenderWorldLastEvent does not exist here).
pin_uni 'net.minecraftforge.client.event.RenderLevelStageEvent' 'getStage('
pin_uni 'net.minecraftforge.client.event.RenderLevelStageEvent' 'getPoseStack('
pin_uni 'net.minecraftforge.client.event.RenderLevelStageEvent' 'getProjectionMatrix('
pin_uni 'net.minecraftforge.client.event.RenderLevelStageEvent' 'getPartialTick('
pin_uni 'net.minecraftforge.client.event.RenderLevelStageEvent$Stage' 'AFTER_ENTITIES'
pin_game() {
  "$J17/javap" -p -cp "$SRG_GAME" "$1" 2>/dev/null | grep -q "$2" \
    || { echo "FAIL d3-live : game pin unmet <$1 :: $2>"; exit 1; }
}
pin_game 'net.minecraft.world.level.Level' 'm_7731_('
pin_game 'net.minecraft.world.level.Level' 'm_46472_('
pin_game 'net.minecraft.world.level.Level' 'f_46428_'
pin_game 'net.minecraft.world.level.block.Block' 'm_49966_('
pin_game 'net.minecraft.world.entity.MobCategory' 'CREATURE'
pin_lib() {
  "$J17/javap" -p -cp "$2" "$1" 2>/dev/null | grep -q "$3" \
    || { echo "FAIL d3-live : library pin unmet <$1 :: $3>"; exit 1; }
}
pin_lib 'net.minecraftforge.fml.LogicalSide' "$FMLCORE" 'SERVER'
pin_lib 'net.minecraftforge.fml.common.Mod' "$JMLLANG" 'value()'
pin_lib 'net.minecraftforge.fml.common.Mod$EventBusSubscriber' "$JMLLANG" 'modid()'
pin_lib 'net.minecraftforge.fml.common.Mod$EventBusSubscriber' "$JMLLANG" 'bus()'
pin_lib 'net.minecraftforge.fml.common.Mod$EventBusSubscriber$Bus' "$JMLLANG" 'MOD'
pin_lib 'net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext' "$JMLLANG" 'get()'
pin_lib 'net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext' "$JMLLANG" 'getModEventBus('
pin_lib 'net.minecraftforge.eventbus.api.SubscribeEvent' "$EVENTBUS" 'SubscribeEvent'
pin_lib 'net.minecraftforge.eventbus.api.IEventBus' "$EVENTBUS" 'register('
pin_lib 'net.minecraftforge.eventbus.api.IEventBus' "$EVENTBUS" 'addListener('
pin_lib 'net.minecraftforge.eventbus.api.Event' "$EVENTBUS" 'setCanceled('
echo "ok d3-live : forge stubs pinned to provisioned jars"

# 3. Build all mod jars with Java 17 (--release 8 keeps the dual-runtime
#    v52 bytes). forge/ compiles against the pinned stubs (Mojmap shape);
#    the live run is the semantic arbiter.
#    R2: jar entries are sorted with timestamps clamped to EPOCH (same
#    commit + same toolchain == same bytes, see normjar), manifests carry
#    VERSION, the bridge jar embeds mods.toml.
#    These are the exact bytes the live run proves AND the release ships.
#    Bridge-owned pure (java/src: loot store/seal, operator policy) compiles
#    beside the seam and stages into the forge classes (same shape as
#    1122/1165: java/ ships inside the bridge jar, never standalone).
BLD="$D3_DIR/build"
rm -rf "$BLD" \
  || { echo "FAIL d3-live : cannot clear <$BLD> (root-owned docker leftovers? point D3_DIR at a user-owned dir)"; exit 1; }
mkdir -p "$BLD/spi" "$BLD/ex1" "$BLD/mini" "$BLD/forge" "$BLD/jars" "$BLD/modstoml/META-INF" "$BLD/bridge"
"$J17/javac" --release 8 -nowarn -d "$BLD/spi" $(find ../spi/java/src -name '*.java')
"$J17/javac" --release 8 -nowarn -cp "$BLD/spi" -d "$BLD/ex1" $(find ../example1/java/src -name '*.java')
"$J17/javac" --release 8 -nowarn -cp "$BLD/spi" -d "$BLD/mini" $(find ../minimap/java/src -name '*.java')
"$J17/javac" --release 8 -nowarn -cp "$BLD/spi:$BLD/ex1" -d "$BLD/bridge" $(find java/src -name '*.java')
"$J17/javac" --release 8 -nowarn -cp "$BLD/spi:$BLD/ex1:$BLD/bridge" -d "$BLD/forge" $(find tools/live/stub forge/src -name '*.java')
# Bridge-owned pure stages into the forge classes (ships in the bridge jar).
cp -r "$BLD/bridge/"* "$BLD/forge/"
sed "s/@VERSION@/$VERSION/g" forge/src/META-INF/mods.toml > "$BLD/modstoml/META-INF/mods.toml"
grep -q "version=\"$VERSION\"" "$BLD/modstoml/META-INF/mods.toml" \
  || { echo "FAIL d3-live : mods.toml stamp lost (want version $VERSION)"; exit 1; }
EPOCH="${SOURCE_DATE_EPOCH:-$(git log -1 --format=%ct)}"
printf 'Manifest-Version: 1.0\nImplementation-Version: %s\n' "$VERSION" > "$BLD/MANIFEST.MF"
find "$BLD/spi" "$BLD/ex1" "$BLD/mini" "$BLD/forge" "$BLD/modstoml" "$BLD/MANIFEST.MF" -exec touch -h -d "@$EPOCH" {} +
mkjar "$BLD/jars/matou-spi.jar" "$BLD/spi"
mkjar "$BLD/jars/matou-example1.jar" "$BLD/ex1"
mkjar "$BLD/jars/matou-minimap.jar" "$BLD/mini"
rm -rf "$BLD/bridgemod" && mkdir -p "$BLD/bridgemod"
cp -r "$BLD/forge/"* "$BLD/bridgemod/"
# Stubs are compile-only: they must never ship (a fake Block on the
# runtime classpath would shadow vanilla). Refuse loudly if leaked.
# The renderer tranche adds org/lwjgl/*C, org/joml/* and com/mojang/*
# stubs beside the net/* ones (same strip as the 1165 visual tranche —
# a fake GL11C, Matrix4f or PoseStack on the runtime classpath would
# shadow the real classes).
rm -rf "$BLD/bridgemod/net" "$BLD/bridgemod/org" "$BLD/bridgemod/com"
if [ -e "$BLD/bridgemod/net" ] || [ -e "$BLD/bridgemod/org" ] || [ -e "$BLD/bridgemod/com" ]; then
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

# 3b. Narrow-map coverage: every net/minecraft/* + com/mojang/* member
#     the built Mojmap jar references must resolve in the derived map.
#     Reobf passes unmapped names through silently, so an uncovered ref
#     dies linking live (the 1122 visual tranche found the first
#     RenderWorldLastEvent crashing on an unmapped member — the map
#     covered server refs only, and the step-2 comment claiming full
#     coverage had no check behind it). The walk mirrors Reobf.walk
#     exactly (in-jar superclass chain, fields by name): <init>/<clinit>
#     never rename, Forge/LWJGL/JOML owners pass through by design, so
#     none of those is asserted.
python3 - "$BLD/jars/matoubridge.jar" "$SRG_NARROW" <<'EOF'
import struct, sys, zipfile
jar, mapf = sys.argv[1:3]
methods, fields = set(), set()
for raw in open(mapf):
    t = raw.split()
    if not t:
        continue
    if t[0] == "MD:":
        own, name = t[3].rsplit("/", 1)
        methods.add((own, name, t[4]))
    elif t[0] == "FD:":
        own, name = t[2].rsplit("/", 1)
        fields.add((own, name))
ALLOW = {
    # MobCategory/CREATURE ships MCP-named at runtime (joined.tsrg v2
    # maps obf b straight to CREATURE — passthrough by construction like
    # Forge classes, same documented split as the step-2 comment).
    ("net/minecraft/world/entity/MobCategory", "CREATURE"),
}
def u(pool, i):
    return pool[i][1].decode("utf-8")
def parse(data):
    assert data[:4] == b"\xca\xfe\xba\xbe", "E_MAP_COVER:not a class"
    n = struct.unpack(">H", data[8:10])[0]
    pool = [None] * n
    i, p = 1, 10
    while i < n:
        tag = data[p]
        p += 1
        if tag == 1:
            ln = struct.unpack(">H", data[p:p + 2])[0]
            pool[i] = (tag, data[p + 2:p + 2 + ln])
            p += 2 + ln
        elif tag in (7, 8, 16, 19, 20):
            pool[i] = (tag, struct.unpack(">H", data[p:p + 2])[0])
            p += 2
        elif tag in (9, 10, 11, 12, 17, 18):
            pool[i] = (tag, struct.unpack(">H", data[p:p + 2])[0],
                       struct.unpack(">H", data[p + 2:p + 4])[0])
            p += 4
        elif tag == 15:
            p += 3
        elif tag in (3, 4):
            p += 4
        elif tag in (5, 6):
            p += 8
            i += 1
        else:
            raise AssertionError("E_MAP_COVER:bad tag %d" % tag)
        i += 1
    this_idx = struct.unpack(">H", data[p + 2:p + 4])[0]
    super_idx = struct.unpack(">H", data[p + 4:p + 6])[0]
    this_name = u(pool, pool[this_idx][1])
    super_name = u(pool, pool[super_idx][1]) if super_idx else None
    refs = []
    for e in pool[1:]:
        if e is None or e[0] not in (9, 10, 11):
            continue
        owner = u(pool, pool[e[1]][1])
        _, ni, di = pool[e[2]]
        refs.append((owner, u(pool, ni), u(pool, di), e[0] == 9))
    return this_name, super_name, refs
z = zipfile.ZipFile(jar)
supers, allrefs = {}, []
for info in z.infolist():
    if not info.filename.endswith(".class"):
        continue
    this_name, super_name, refs = parse(z.read(info.filename))
    supers[this_name] = super_name
    allrefs.extend(refs)
missing = []
for owner, name, desc, is_field in allrefs:
    if not (owner.startswith("net/minecraft/") or owner.startswith("com/mojang/")):
        continue
    if name in ("<init>", "<clinit>"):
        continue
    o, hit = owner, False
    while o is not None:
        if is_field:
            if (o, name) in fields:
                hit = True
                break
        elif (o, name, desc) in methods:
            hit = True
            break
        o = supers.get(o)
    if not hit and (owner, name) not in ALLOW:
        missing.append("%s %s %s %s" % ("FD" if is_field else "MD", owner, name, desc))
assert not missing, "E_MAP_COVER:unmapped vanilla refs:\n%s" % "\n".join(sorted(set(missing)))
print("ok d3-live : narrow map covers forge refs")
EOF
echo "ok d3-live : narrow map covers forge refs"

# 4. Reobfuscate Mojmap-named member refs to SRG (ForgeGradle reobf
#    equivalent: production vanilla declares SRG member names, so
#    un-reobfed jars die with NoSuchMethodError — found live in B3, never
#    again silently). Classes stay Mojmap (production classes are Mojmap),
#    so only the 48 narrow-map members move; Forge refs pass through
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
  cp ../example1/content/owned.matou ../example1/content/additive.matou ../example1/content/structure.matou ../example1/content/vein.matou dist/matou-content/
  cp tools/live/my_beast.geo.json dist/my_beast.geo.json
  printf '# Copy to <server>/config/matoubridge/packs.cfg and replace <SERVER>.\n# Wire y=63 keeps plane cells on their own slice, off the structure slices (64..65).\n# The wire block is the registered custom ore (DeferredRegister queues example1:my_ore from owned.matou, the fill lands before setup binds resolve it); aliases stay vanilla stone.\n# Vein clusters land on the BASE_Y=60 band (slices 60..61) as the registered ore via the veinblock alias.\nfr.iamacat.example1.ExamplePack 63 example1:my_ore ownedFile=<SERVER>/matou-content/owned.matou scatterFile=<SERVER>/matou-content/additive.matou structureFile=<SERVER>/matou-content/structure.matou block.example1.structures:hut_wall=minecraft:stone block.example1.structures:hut_roof=minecraft:stone veinFile=<SERVER>/matou-content/vein.matou veinblock.example1.content:my_ore=example1:my_ore\n' > dist/packs.cfg.example
  (cd dist && sha256sum "matou-spi-$VERSION.jar" "matou-example1-$VERSION.jar" "matou-minimap-$VERSION.jar" "matoubridge-$VERSION.jar" matou-content/owned.matou matou-content/additive.matou matou-content/structure.matou matou-content/vein.matou packs.cfg.example my_beast.geo.json > SHA256SUMS.txt)
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
# resolve it). Vein clusters land on their own band (BASE_Y=60, slices
# 60..61) as the registered ore through the veinblock alias.
printf 'fr.iamacat.example1.ExamplePack 63 example1:my_ore ownedFile=%s/matou-content/owned.matou scatterFile=%s/matou-content/additive.matou structureFile=%s/matou-content/structure.matou block.example1.structures:hut_wall=minecraft:stone block.example1.structures:hut_roof=minecraft:stone veinFile=%s/matou-content/vein.matou veinblock.example1.content:my_ore=example1:my_ore\n' "$SERV" "$SERV" "$SERV" "$SERV" > "$SERV/config/matoubridge/packs.cfg"
# Beast shape: the shipped Blockbench geometry the renderer bakes and the
# hitboxes derive from (hub decisions/MATOU_MODEL.md). Deployed beside
# packs.cfg, operator-replaceable like it.
cp tools/live/my_beast.geo.json "$SERV/config/matoubridge/my_beast.geo.json"
echo "eula=true" > "$SERV/eula.txt"
printf 'online-mode=false\nlevel-type=minecraft:flat\ngamemode=1\ndifficulty=0\nmotd=D3 live proof\nmax-tick-time=-1\n' > "$SERV/server.properties"
rm -rf "$SERV/world" "$SERV/logs"
live_boot "$SERV" "$BOOT_SECS" "boot-d3.log" sh run.sh nogui

# 6. Fail loudly on any runtime refusal or linkage error (stdout log plus
#    the rolling server log — Forge splits output across both). E_HIT rides
#    it too: the combat hook refuses corrupt attacker state loudly out of
#    SPI (hub decisions/VIRTUAL_HITBOXES.md) — a NaN eye that passed would
#    mean a defaulted multiplier somewhere.
LOGS="$SERV/boot-d3.log"
[ -f "$SERV/logs/latest.log" ] && LOGS="$LOGS $SERV/logs/latest.log"
live_verdict "NoSuchMethodError\|NoSuchFieldError\|E_FORGE\|E_BRIDGE\|E_EXAMPLE\|E_REG\|E_LOOT\|E_SPAWN\|E_SPIKE\|E_MODEL\|E_HIT\|Encountered an unexpected exception" "NoSuchMethodError\|NoSuchFieldError\|E_FORGE\|E_BRIDGE\|E_EXAMPLE\|E_REG\|E_LOOT\|E_SPAWN\|E_SPIKE\|E_MODEL\|E_HIT\|Caused by" $LOGS
# Registration proof: the setup-time verify line carries the registry key
# (1.20.1 has no numeric block ids — the anvil probe reads namespaced
# names, and this line proves the custom name resolved through the
# registry, never defaulted).
grep -a -q '\[MatouBridge\] registered <example1:my_ore> id example1:my_ore' $LOGS \
  || { echo "FAIL d3-live : my_ore registration line absent from boot log (deferred fill never registered? see $SERV/boot-d3.log)"; exit 1; }
echo "ok d3-live : my_ore registered ($(grep -a -o '\[MatouBridge\] registered <example1:my_ore> id [^ ]*' $LOGS | tail -n 1))"
grep -a -q '\[MatouBridge\] registered-item <example1:my_gem> id example1:my_gem' $LOGS \
  || { echo "FAIL d3-live : my_gem registration line absent from boot log (deferred fill never registered? see $SERV/boot-d3.log)"; exit 1; }
echo "ok d3-live : my_gem registered ($(grep -a -o '\[MatouBridge\] registered-item <example1:my_gem> id [^ ]*' $LOGS | tail -n 1))"

# 7. Positive proof: world blocks in chunks (0..1, -1..1) at y=60..61
#    plus y=63..65 must equal the pure decision union — plane cells
#    carry the registered custom ore, volume cells their alias stone,
#    vein clusters the registered ore, nothing foreign, nothing
#    missing. Plane cells land at the wire y=63, volume cells at their own
#    y=64..65; structure offsets reach x,z=17, and the hut anchor z=-4
#    spills into chunk row -1 (region r.0.-1.mca) — hence the 6-chunk,
#    5-slice read. (Same geometry as C3: the mod writes force chunk
#    generation around the origin regardless of world spawn.)
"$J17/javac" --release 8 -nowarn -cp "$BLD/spi:$BLD/ex1" -d "$BLD" tools/live/CellUnion.java
"$J17/java" -cp "$BLD:$BLD/spi:$BLD/ex1" CellUnion \
  "$SERV/config/matoubridge/packs.cfg" 4000 "$BLD/union.txt"
live_anvil_loop "$SERV" "$BLD"
live_compare_names "$BLD/union.txt" "$BLD/world.txt" "$SERV/config/matoubridge/packs.cfg"
