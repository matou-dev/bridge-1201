#!/bin/sh
# D3 live gate (TODO, scaffolded): Forge 1.20.1 (1.20.1-47.2.0) server run
# proving PackWire.bind (real Block resolve) plus world-tick apply on a real
# world, then comparing the world against the pure decision union.
#
# Scaffold placeholder: the live run is NOT wired yet. Port tools/run-live.sh
# from bridge-1122 (same phases, new pins) before claiming D3 :
#   1. FORGE_URL (default below) + reproducibility pins (installer /
#      universal / mappings / ASM sha1) measured from one provisioned run.
#   2. narrow MCP->SRG map derived in-run from pinned vanilla server +
#      mappings (no ForgeGradle cache, no SRG file to mount).
#   3. Dockerfile cache notes + live-proof.yml dispatch.
# Refuse-loud contract: with LIVE=1 this placeholder fails (never a silent
# green); without LIVE=1 tools/check.sh skips it (never blocking).
#
# Env (no machine paths hardcoded):
#   D3_DIR   work dir (default ${TMPDIR:-/tmp}/matou-d3-live)
#   JAVA8_HOME Java 8 home (default /usr/lib/jvm/java-8-openjdk)
#   FORGE_URL  installer URL (default Maven 1.20.1-47.2.0 installer)
#   BOOT_SECS  server run time (default 150; short runs fail coverage loudly)
set -eu
cd "$(dirname "$0")/.."
D3_DIR="${D3_DIR:-${TMPDIR:-/tmp}/matou-d3-live}"
JAVA8_HOME="${JAVA8_HOME:-/usr/lib/jvm/java-8-openjdk}"
FORGE_URL="${FORGE_URL:-https://maven.minecraftforge.net/net/minecraftforge/forge/1.20.1-47.2.0/forge-1.20.1-47.2.0-installer.jar}"
BOOT_SECS="${BOOT_SECS:-150}"
echo "FAIL d3-live : live not wired yet (phase D3 todo) — port tools/run-live.sh from bridge-1122, fill FORGE_URL/pins, then re-run with LIVE=1"
exit 1
