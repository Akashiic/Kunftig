# Kunftig: context for whoever works in this repository

Forge 1.7.10 mod (client + server) of backports for the TEOF modpack, with a Bukkit-facing API (`api/`, the
`kunftig-api` that plugins compile against, `compileOnly`). Its main consumer is
[AkashicFishing](https://github.com/Akashiic/AkashicFishing); start there with `docs/HANDOFF.md` for the current state
of both projects (open PRs, merge order, what is still to be checked on the real server).

## Where things are

* **API** (`api/`, `KunftigApi.API_VERSION`, now **13**):

  | API | Feature |
  |---|---|
  | fishing | `KunftigFishEvent`, `ModernFishHook` |
  | 2 | particles, ping |
  | 4 | item icons |
  | 5 | line lock, aimed cast |
  | 6 | holograms |
  | 7 | 3D rods (`KunftigRods`) |
  | 8 | displays (`KunftigDisplays`): GeckoLib models placed in the world, with item stacks drawn at bones (AkashicFishing's Caixa da Sorte) |
  | 9 | displays: model and per-display scale, hidden bones, state blends, glides (`drawsExtendedDisplays`, channel `kunftig:display2`); flight lock (`KunftigPlayers.setFlightLocked`, channel `kunftig:flight`, `flight/FlightLock` + `FlightEnvelope`) |
  | 10 | hook range (`ModernFishHook#setMaxDistance`, a `@ModifyConstant` on vanilla's 32-block check) |
  | 11 | Overkill's combat: client keys (`KunftigInput`, channel `kunftig:input`), screen HUD and camera lock-on (`KunftigHud`, `KunftigCamera`), displays that fly and turn on every axis (`Display#moveTo`, `setMotion`, `setRotation`, display op `MOTION` = 7), attack prediction (`ao_atacar`), SlashBlade-style blades and effects (`KunftigBlades`, `KunftigEffects`), `KunftigPlayers.pushVelocity` |
  | 12 | displays players walk on (`Display#setCollision`, `KunftigDisplays.walksOnDisplays`, display op `DECK` = 8, channel `kunftig:display3`): solid boxes in the display's frame, the local player carried and kept on them by `client/display/DeckClient` with the pure `display/DeckWalker` (Expeditions' ship decks); `Display#setVariant` (display op `VARIANT` = 9) |
  | 13 | bones turned by the server (`Display#setBoneRotation`, display op `POSE` = 10): degrees added to a bone's rotation, glided on the client and applied in `DisplayRenderer#renderRecursively` (Expeditions' doors and gangplank) |

  API 9 and 11 were built at the same time on two branches (AkashicFishing's boss and Overkill), which both called
  their additions "API 9"; they were merged as 9 and 11, with one display wire (see `DisplayWire`). A client or server
  from either branch alone must be updated together.

  The player-model (YSM-style) study is in `docs/playermodel/ESTUDO-YSM.md`; the port itself is the separate mod
  3D Futura Engine (`futura3d`), on branch `main` of [Akashiic/YesSteveModel](https://github.com/Akashiic/YesSteveModel).

  The guide is in `docs/API.md` (Portuguese; the displays are in section 13, the flight lock in 14, the hook range in
  15, Overkill's keys, HUD, camera, attack prediction and blades in 16 to 20, walking on displays in 21, server-turned bones in 22). Raise the
  version and update the version asserts in the API tests when adding to it.
* **Wire protocols** are raw plugin messages:
  * `kunftig:holo`, `kunftig:rod`, `kunftig:display`, `kunftig:flight`, and Overkill's `kunftig:input`, `kunftig:hud`,
    `kunftig:camera`, `kunftig:blade` and `kunftig:effect`;
  * a client registers a channel only when it can draw it, so plugins know who needs a fallback
    (`drawsDisplays`, `drawsRodModels`...).
* **Client rendering** of rods and displays goes through GeckoLib Unofficial 1.0.4, loaded reflectively; without it,
  nothing breaks.

## Build and rules

* Gradle (GTNH convention / RetroFuturaGradle). Runtime code targets **Java 8 bytecode** (Jabel): modern syntax is fine,
  but no `record` or other Java 9+ library types in `src/`.
* `./gradlew spotlessApply build -x javadoc`: spotless must pass.
* The API jar for AkashicFishing:
  ```bash
  VERSION=0.1.0-dev-<commit> ./gradlew :api:jar :api:sourcesJar
  ```
  It is then pinned in AkashicFishing's `libs/`.
* **Dev harness** (`com.akashic.kunftig.dev`, enabled with `-Dkunftig.devHarness=true` and a `kunftig-rod-harness`
  file in the client's run directory: `caixa`, `skins`, `boss`, `overkill`, `blades`, `sb`, `models`, `modelcombos`,
  `server`, `observer`, `fish`, `cells`, `edge`, `perf`):
  * it is **never in the release jar**: `shadowJar` excludes it;
  * for a test jar, comment out that exclude, build, then `git checkout build.gradle.kts`.
* Work on the branch named in the session; no force-push; a merged PR is not reused (merge `main` into the branch and
  open a new PR).
* What is not verified in game is written as "to be checked" in the docs.
