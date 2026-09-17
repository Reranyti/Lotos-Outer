# Changelog

Retroactive version report for the full rewrite of Lotus Blight. `mod_version`
was left at `1.0.0` for the entire rewrite instead of being bumped per batch
of work — this file assigns version numbers to the existing commit history
after the fact, without rewriting any past commits. Going forward,
`gradle.properties`' `mod_version` is bumped alongside the commit that
finishes each batch of work below.

## 1.6.0 — Inner voice system, glowing berries fixed
- `glowing_berry` was registered but unreachable (bush dropped itself, not the
  food item) — right-click the bush to harvest instead of breaking it.
- New True Light effect (opposite of Lotoniriya) and a second, small
  "dialogue system": eating a glowing_berry shows a short subtitle-style
  scene (player's own skin/name as the portrait, not the Lotus), advanced
  with Enter, with a gold vignette and its own music cue.

## 1.5.0 — Full art pass (sprite sheet)
- Replaced 12 item textures and ~19 block textures with hand-drawn sprite
  sheet art, replacing the earlier procedural Pillow textures.
- Split `infected_lotus.png` from the GeckoLib crown's box-UV atlas (they
  used to share one file, which would have scrambled the crown's geometry).
- `blessing_nodule` intentionally left on the old texture (new art has
  transparent gaps unsuitable for a cube_all block).

## 1.4.0 — Live bug-fix pass (config, cleansing, armor, ore, biome, clustering)
- `ACTIVE_CHUNK_RADIUS`, `SPREAD_RADIUS`, `MAX_PENDING_WORLDGEN_TASKS` config
  values wired up (previously read nowhere).
- Blessing biome's wiki-claimed spread slowdown actually implemented.
- Fixed JourneyMap version-range crash (`1.20.1-6.0.5` compared as < `6.0`).
- Cleansing powder now actually decreases outbreak progress.
- Armor texture lookup fixed (missing namespace) and a real texture added.
- `lotus_ore` given worldgen placement (previously could never spawn).
- Blessing biome moved out of TerraBlender's snow-generating climate zone.
- Mini-lotus shoot spam clustering fixed with a spacing check.

## 1.3.0 — Spread engine deep fixes
- Fixed the "pillars instead of lotuses" bug (ordinary spread was placing a
  full 4-tall anchor instead of a small shoot).
- Root-grown child mini-outbreaks now place a visible flower.
- Open-ocean spread sped up but capped below the mini-biome phase.
- Lotus Heart now only appears at real phase-4 maturity, not instantly from
  a planted seed.
- Guaranteed distant mini-biome epicenter (5000-9000 blocks from spawn).
- Admin `/lotus` command tree for testing outbreaks/branches/map state.

## 1.2.0 — Dialogue system rewrite
- Branch choice (Alliance/Resistance) now actually persists, locks
  permanently server-side, and influences real spread speed near the player
  — previously it only gated one tool and reset every time the dialogue
  screen reopened.
- Rewrote all dialogue lines for a more natural tone; wired previously dead
  lore content (scientist's journal, village water crisis) into the flow.

## 1.1.0 — Integrations and item audit
- GeckoLib bloom animation for the infected lotus crown.
- Soft TerraBlender integration for biome placement.
- JourneyMap waypoint integration (replacing the mod's own HUD map when
  JourneyMap is installed).
- YACL-based in-game config screen.
- Curios API investigated for the seer lens, reverted before shipping —
  would have hard-crashed the mod for anyone without Curios installed.
- Fixed a held-item render bug and an Embeddium chunk-mesh crash on
  infected water.
- Item/lang audit: missing translation keys, dead duplicate advancement key.

## 1.0.0 — Full rewrite baseline
- Complete rewrite from the broken `story-v11` prototype: persisted
  SavedData-backed outbreak/spread engine (replacing a non-persistent static
  map), full item/armor/tool set, worldgen (biomes, mini-biome, guaranteed
  spawn), lore/dialogue foundation, procedural art pass.
