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
