# Changelog

Retroactive version report for the full rewrite of Lotus Blight. `mod_version`
was left at `1.0.0` for the entire rewrite instead of being bumped per batch
of work — this file assigns version numbers to the existing commit history
after the fact, without rewriting any past commits. Going forward,
`gradle.properties`' `mod_version` is bumped alongside the commit that
finishes each batch of work below.

Versioning switched from `major.minor.patch` to a flat `1.NNNN` counter at
`1.1010` — every entry below `1.1010` still uses the old three-part numbers
as they were actually committed at the time.

*Note: commit messages between `1.7.0` and `1.67.x` didn't embed a version
number, so that stretch isn't individually reconstructable here — see
`git log` for the raw history of that range. Everything from `1.68.0` onward
is transcribed directly from its own commit message.*

## 1.1042 — Beta: full audit release (1.1017 – 1.1042)
A full read-through of the whole mod (175 classes, resources, design docs) with every confirmed
bug fixed, then checked by building the jar and starting a dedicated server until `Done`. One
commit per fix, kept on the `fix/audit-2026-09` branch for reference.

Critical:
- Player state (branch, traitor flag, Honcho progress, StarFall, reputation, one-time gifts) was
  wiped on every death and End exit - Forge only carries over `PlayerPersisted`. Now copied on clone.
- Meteorite stone could never be obtained: no `mineable` tags existed for any block of the mod,
  and mining progress read an always-empty client state. The contact kill for the war branch never
  fired either (`entityInside` on a full cube) - now `stepOn` and punching it.
- Honcho, a Zombie underneath, burned at dawn, turned into a Drowned underwater, was attacked by
  golems and could call reinforcements. All disabled; he now spawns once per world.
- StarFall: the grafting rod was never removed (`removeItem` compares by reference), and the
  client-sent line packet could be replayed to drop the radius-24 meteor patch at will.
- Black hearts for golden items now apply to the Alliance branch (the one Star Light warns).

Major:
- Phase 4 biome rewrite only changed the Y=0 biome cell (WorldEdit's 2D `setBiome`); now every
  4x4x4 cell over the full height.
- Guardians turned into passive wolves after a restart or chunk reload (goals aren't saved).
- All six HUD overlays were drawn once per HUD layer (20+ times a frame); the chase clock counted
  frames instead of ticks and ran out in seconds.
- The traitor advancement branch never loaded (missing parent `follow_the_current`).
- Lotus spores and cold blight damage almost never landed (two unrelated counters).
- Starter kit handed out again when logging in from another dimension.
- The phase 4 tree burst, biome rewrite and announcement replayed whenever the block count dipped
  under a threshold and climbed back - outbreaks now remember their peak phase.
- Synchronous chunk loads in the spread engine; vine barriers and the boss arena overwrote chests.
- Traitor boss arena was built inside the player; leftover wave mobs stayed in the world forever.
- Roots and leaves never applied spores on contact.

Found by starting a dedicated server:
- The mod failed to construct without TerraBlender (class verification needed `terrablender.api.Region`
  before the `isLoaded` check). TerraBlender is now a required dependency.
- Client-only rendering, color handlers and the wiki/journal screens were referenced from common
  classes, so the mod never loaded on a dedicated server.

Dependencies:
- Ex Meteor Shower needs OctoLib (and OctoLib needs Architectury) without declaring it - both are
  now listed as required, so a missing one is reported by the loader instead of crashing.
- Chat Overhaul declared as an optional dependency.

Smaller fixes: packet directions and server-side validation, Honcho scenes re-shown if left
unanswered, client state and music reset when leaving a world, the Honcho "N" answer no longer
opens the atlas, the chase lab waits for its whole footprint to load and leaving mid-run counts as
being caught, roots queue, lily pads on dry land, a second heart from the epicenter, scientist
pages counted on pickup, `en_us.json` fallback, Honcho idle pose (the animation never played
because of a name mismatch), boss wave count corrected to 5 in the docs.

## 1.1011 – 1.1016
- Faction reputation; Honcho moves closer after the 3rd vial (1.1011).
- Honcho gets his own GeckoLib model instead of the zombie one, plus the trip-meeting scene (1.1012).
- Infected water tint per biome fixed; Mossy Glands could fail to appear at all (1.1013).
- Lotus territory as a real spread bonus, `honcho.geo.json` fix, own calendar (1.1014).
- Real phase 4 biome transition through WorldEdit; infected water removed as a separate fluid (1.1016).

## 1.1010 — Versioning switch
- Switched `mod_version` from `major.minor.patch` to a flat `1.NNNN` counter.

## 1.101.0 — Honcho ambient line
- Rare ambient line for whoever is currently feeding Honcho, echoing "the
  light used to guide him, now the player does" without quoting anything.

## 1.100.0 — Meteorite patches, Blessing territory, map waypoints
- StarFall's meteorite impact patch radius widened substantially (9 → 24).
- Impact sites now carry their own "territory" flag (not a real Minecraft
  biome) recognized by the same checks that already treat the real Blessing
  biome specially (guardian spawn skip, infection spread penalty) — set the
  instant the site is seeded, ahead of the slower block-by-block spread.
- Meteorite impact positions now show as waypoints on the minimap.

## 1.96.0 – 1.99.0 — Honcho
- New original quest NPC appearing once after a player survives StarFall:
  brings him a Star Light Vial, gets a permanent health blessing and lore in
  return. A short-lived duplicate NPC ("Horichoniy", a WanderingTrader-based
  vendor) was added by mistake and removed again the same day.
- War-branch-only "let me be your assistant" scene on first meeting, with a
  Yes/No choice each granting its own advancement.
- The vial quest became repeatable instead of one-time: every vial deepens a
  real dependency — Honcho follows whoever fed him and visibly weakens
  (Weakness + Slowness) if 20 minutes pass without another one.

## 1.93.0 – 1.94.1 — Quarantine world border
- World border capped at 1,000,000 blocks, centered on spawn, for story
  reasons ("the site was sealed under quarantine").
- Fully custom (not vanilla) purple force-field wall rendered at the nearest
  edge, plus a bounce-back-and-can't-die-from-it mechanic replacing vanilla's
  own border damage, plus a blood-red sky tint on approach.

## 1.91.0 – 1.92.0 — Per-biome infection color overhaul
- Every biome with a dictated fog gradient now derives its own ground/wood
  tint from that gradient instead of one of 8 shared group colors.
- Stone/sand/gravel intentionally kept one shared tone across every biome
  (they're minerals, not biome-tied organics) — fixed from an accidental
  identical color bug, not turned into 50 more variants.
- Infected water's underwater fog now follows the same per-biome gradient
  instead of one fixed dark green everywhere.
- Infection spread speed now fades smoothly with distance from the nearest
  player instead of freezing outbreaks outside a fixed radius entirely.

## 1.87.0 – 1.90.0 — Post-audit bug-fix wave
- A second full-mod workflow audit found and fixed: an unguarded
  synchronous-chunk-load deadlock (also present in three other spots besides
  the one already fixed in the Escape lab), a starter-water-search fairness
  bug starving later-joining players, grass blocks almost never actually
  converting during infection spread, and tree canopies starving ground
  conversion of frontier attempts.
- Several one-off crashes/bugs on specific Forge builds and StarFall's own
  branch mix-up were fixed the same week.

## 1.85.0 – 1.86.0 — Meteorite gear, real StarFall trigger
- Crafting recipes for the whole `lotus_alloy` tool/armor set, the grafting
  rod, seer lens and vitamin — previously creative-only.
- StarFall now fires on its own once world infection crosses 30%, instead of
  only via the admin test command.
- Full meteorite tool/armor tier (between diamond and netherite).

## 1.68.0 – 1.84.0 — Traitor branch, Escape, StarFall
- Full traitor storyline: guardian-kill/cleanse-powder betrayal triggers, a
  5-wave boss fight, and its own incineration cutscene.
- "Побег от лотоса" — a persistent, sealed escape-the-lab event with a
  real/decoy junction, unlocked only once world infection crosses 15%.
- StarFall — a one-time Star Light encounter with a full alliance/war branch
  script, ending in either a permanent resource or a real consequence
  depending on the player's choices, backed by the Ex Meteor Shower mod for
  its comet visuals.

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
