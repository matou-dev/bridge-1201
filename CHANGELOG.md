# Changelog — matou-dev/bridge-1201

Notable changes to this repo. The bridge jar is the only loadable Forge
mod and it never ships alone: releases are versioned source + server drops
(`dist/`, reproducible). Store listings stay DRAFT (see hub `NAMES.md`).
Full notes per tag: https://github.com/matou-dev/bridge-1201/releases.

## [Unreleased]

- Custom entity E0 (hub `decisions/SPAWN.md`): `MatouEntity` shell
  replaced by the generic beast (`extends Pig`, pig shape/AI/sounds
  reused), `Example1Mod` queues it on a `DeferredRegister` over
  `ForgeRegistries.ENTITY_TYPES` (short mob name from the single-mob
  spawn table, pig-category/hitbox/tracking measured on the pinned
  47.2.0 bytes — the 1.7.10/1.12 `EntityRegistry` call does not exist
  here) with a setup-time `ENTITY_TYPES.getValue` tripwire on the
  registry id (never the SPI mob ref — 1165-measured) plus a
  `registered-entity` log line, the attribute map on the mod-bus
  `EntityAttributeCreationEvent` (vanilla pig map reused wholesale —
  1165-measured NPE without one), and the client-only vanilla
  `PigRenderer` mapping through the mod-bus `RegisterRenderers` event
  (single `(Context)` ctor, measured from the pinned SRG client jar —
  the 1.16.5 `RenderingRegistry` path does not exist here) in a
  dist-filtered nested subscriber (never loaded on servers);
  census/veto/reconcile/kill-hook/landing and the companion legs
  narrowed to the beast; companion loads AFTER `matoubridge` in
  autoplay-mods.toml (lead-measured, unproven on 47.2.0 until live).
  Live proof TODO.

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
