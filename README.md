# matou-dev/bridge-1201 — SPI ↔ Minecraft 1.20.1 translator

The only module allowed to touch MC/Forge 1.20.1 (Forge 47.2.0) on
this side. Translates `matou-spi` into the game (world, registries).

Modid: `matoubridge` (see hub `NAMES.md` — reused from the other bridges,
safe: two bridges never load in the same MC instance).

The decide/apply seam (`fr.iamacat.bridge`: `SpiBridge`, `CellSink`,
`ForgeCells`, `ForgeSnapshot`, `ForgeContent`, `Packs`) ships from
`matou-spi` (see `SPI_PIN`) at identical FQNs — this repo carries only its
Forge side below. Pure coverage lives in SPI (`BridgeCheck`); content E2E
(`ForgeContentCheck` against `../example1`) is wired in D2 below.

## D1 Forge wiring (Forge 47.2.0)

Only `forge/` touches MC/Forge (`World.setBlockState` + `BlockPos` + `IBlockState`,
overworld y `0..255`):

- `forge/src/fr/iamacat/bridge/forge`: `MatouBridgeMod`
  (`@Mod(modid="matoubridge")`, FML server tick `END` dim 0 → snapshot
  `matou:tick` → pure decide), `PackWire` (reflective bind + block
  resolve + y check, fail fast), `WorldCellSink` (`CellSink` into the
  world, volume cells resolve their block by name, cached, unknown refused
  loudly). Passive until `packs.cfg` exists (Q1 coexistence).
- `tools/live/stub`: shape-only 1.20.1 API used by `forge/` (compile
  classpath only, never runs). Etage 2 compiles `forge/` against it —
  green with no MC jars. `run-live.sh` (D3) asserts these members
  against the provisioned 1.20.1-47.2.0 jars once wired.

Gate: `tools/check.sh` (etage 1 siblings-spi-ex1 compile + pure E2E,
etage 2 forge-vs-stub compile, etage 3 `LIVE=1` runs `tools/run-live.sh`).

## D2 content wiring (packs)

Packs come from `config/matoubridge/packs.cfg`
(`<class> <y> <block> [k=v ...]`, `#` comments, missing file = passive
like D1). Contract in `matou-spi` (`ContentPack` + optional
`ConfigurablePack` for operator args, `ForgeContent` for decide +
apply), `Packs` (strict config parse + reflective `load`) from the
shared seam, E2E `java/test/.../ForgeContentCheck` (real example1 jobs
from the source files + fake world; `ForgeContent.merge ==
AdditiveScatterJob.merge` comparator). Body kept in sync with the other
bridges by convention.

## D3 live proof

`tools/run-live.sh` (LIVE=1, Java 17): provisions Forge 1.20.1-47.2.0
(checksum-verified), derives the narrow Mojmap→SRG map from pinned bytes
(Mojang server.txt + mcp_config joined.tsrg v2 + javap on the vanilla jar),
pins every stub member against the provisioned jars (vanilla members against
the installer-renamed game jar, Forge members against universal/fmlcore/
javafmllanguage/eventbus), builds versioned jars, reobfuscates the bridge,
boots the server 150s (`run.sh nogui`, flat world), then proves
world == pure union (stone only).

Production naming (measured, not assumed): Mojmap classes with SRG members
— the bridge ships FAT (spi + example1 embedded: 1.20.1 modules isolate
every mods/ jar). Stub annotation retention mirrors the real annotations
(RUNTIME): eventbus discovers handlers through RuntimeVisibleAnnotations
only. Last green proof: hub STATE.md.
