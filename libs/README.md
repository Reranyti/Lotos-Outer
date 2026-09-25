# libs/

Manually-placed compile-time-only jars consumed via Gradle's `flatDir` repository
(see `build.gradle`). Not committed to git.

- `journeymap-api-forge-1.20.1-2.0.0.jar` — the JourneyMap client/common API,
  extracted from `META-INF/jarjar/journeymap-api-forge-1.20.1-2.0.0.jar`
  inside the installed `journeymap-forge-1.20.1-6.0.5.jar` (no confirmed
  live public Maven repo serving the v2.0.0 API for 1.20.1 — the old
  `https://jm.gserv.me/repository/maven-public/` repo documented for
  JourneyMap only serves the pre-2.0.0 `@ClientPlugin` API, which this
  JourneyMap version no longer uses). If JourneyMap publishes a proper
  Maven artifact for this later, switch to that instead of this manual copy.
  To regenerate: unzip the jarjar entry out of any journeymap-forge-1.20.1-6.x.jar.

- `chatoverhaul-1.0-1.20.1.jar` — "Chat Overhaul" (modId `chatoverhaul`), a
  small third-party mod (author GrapeGG) that redraws chat with centered
  messages, player heads, and per-player nickname colors. Used by
  `ChatOverhaulBranchColor` to tint a player's chat name to match their
  locked-in dialogue branch. No public Maven artifact found; placed here as
  the distributed jar itself.

- Runtime-only jars for `runClient`/`runServer` (the mod's mandatory dependencies), copied from a
  normal game install's `mods/` folder: `worldedit-mod-7.2.15.jar` (Modrinth),
  `meteor_shower-0.0.4.2-1.20.1.jar`, `OctoLib-FORGE-0.5.0.1+1.20.1.jar` and
  `architectury-9.2.14-forge.jar` (Ex Meteor Shower needs OctoLib at runtime without declaring it,
  OctoLib needs Architectury), plus the `TerraBlender-forge-1.20.1-3.0.1.10.jar` above.
