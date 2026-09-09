# Changelog — matou-dev/bridge-1201

Notable changes to this repo. The bridge jar is the only loadable Forge
mod and it never ships alone: releases are versioned source + server drops
(`dist/`, reproducible). Store listings stay DRAFT (see hub `NAMES.md`).
Full notes per tag: https://github.com/matou-dev/bridge-1201/releases.

## [Unreleased]

## [1.2.0] - 2026-09-09

Versioned server drop: https://github.com/matou-dev/bridge-1201/releases/tag/v1.2.0

- First drop (no v1.0.0/v1.1.0 tags on this repo; aligns with the
  unified v1.2.0 round over `matou-spi` `3e819a9`).
- D1 scaffold: `forge/` for MC 1.20.1 (Forge 47.2.0, modern sink), seam
  from `matou-spi` (see `SPI_PIN`), gate `tools/check.sh` (etages 1+2
  green without MC).
- D2 content wiring: `ForgeContentCheck` pure E2E (bridge-1122
  pattern), packs from `config/matoubridge/packs.cfg`.
- D3 live proof: `tools/run-live.sh` on Forge 47.2.0 (Mojmap classes +
  SRG members via the installer MERGE_MAPPING chain, FAT bridge +
  `mods.toml` with `@VERSION@` stamp, palette anvil probe), verdict
  world == pure union (1274 cells, stone only — same count as the
  1614/2860 proofs).
- Dev-client helpers as thin wrappers over hub `tools/run-client.sh`
  (untested).
