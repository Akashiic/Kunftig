# Kunftig: context for whoever works in this repository

Forge 1.7.10 mod (client + server) of backports for the TEOF modpack, with a Bukkit-facing API (`api/`, the
`kunftig-api` that plugins compile against, `compileOnly`). Its main consumer is
[AkashicFishing](https://github.com/Akashiic/AkashicFishing); start there with `docs/HANDOFF.md` for the current state
of both projects (open PRs, merge order, what is still to be checked on the real server).

## Where things are

* **API** (`api/`, `KunftigApi.API_VERSION`, now **16**):

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
  | 12 | displays players walk on (`Display#setCollision`, `KunftigDisplays.walksOnDisplays`, display op `DECK` = 8, channel `kunftig:display3`): boxes in the display's frame, solid from every side for whoever walks, jumps, swims or flies, the local player carried and kept on them by `client/display/DeckClient` with the pure `display/DeckWalker`, other players standing on one marked `onGround` for 3D Futura Engine (Expeditions' ship decks); `Display#setVariant` (display op `VARIANT` = 9) |
  | 13 | bones turned by the server (`Display#setBoneRotation`, display op `POSE` = 10): degrees added to a bone's rotation, glided on the client and applied in `DisplayRenderer#renderRecursively` (Expeditions' doors and gangplank) |
  | 14 | stations (`KunftigInput.STATION`, bound with `KEYS`): at a ship's helm the movement keys stop moving the player, the hotbar is not drawn and the mouse wheel and the hotbar keys come as key bits (`KEY_SCROLL_UP`, `slotKey(n)`); nothing of the inventory is touched (`client/input/InputClient`, `StationInput`). To be checked in game |
  | 15 | postures (`KunftigPlayers.setPosture`, `Posture`, channel `kunftig:pose`, `posture/PostureWire` + `PostureServer`): a player sat or laid down at a point of the world or of a display's frame, held there each tick by `client/posture/PostureClient` (movement keys off, `DeckClient` skips him), the biped posed by `ModelBipedPostureMixin` and laid down by `RenderPlayerPostureMixin`, 3D Futura Engine's models through its `posture` state (futura3d API 7). To be checked in game |
  | 16 | zoom (`KunftigCamera.setZoom`, camera op `ZOOM` = 2): the field of view narrowed up to 10 times, through `FOVUpdateEvent` in `client/screen/CameraClient` (a spyglass; Expeditions' lookout). To be checked in game |

  API 9 and 11 were built at the same time on two branches (AkashicFishing's boss and Overkill), which both called
  their additions "API 9"; they were merged as 9 and 11, with one display wire (see `DisplayWire`). A client or server
  from either branch alone must be updated together.

  The player-model (YSM-style) study is in `docs/playermodel/ESTUDO-YSM.md`; the port itself is the separate mod
  3D Futura Engine (`futura3d`), on branch `main` of [Akashiic/YesSteveModel](https://github.com/Akashiic/YesSteveModel).

  The guide is in `docs/API.md` (Portuguese; the displays are in section 13, the flight lock in 14, the hook range in
  15, Overkill's keys, HUD, camera, attack prediction and blades in 16 to 20, walking on displays in 21, server-turned bones in 22, postures in 23, zoom in 24). Raise the
  version and update the version asserts in the API tests when adding to it.
* **Wire protocols** are raw plugin messages:
  * `kunftig:holo`, `kunftig:rod`, `kunftig:display`, `kunftig:flight`, and Overkill's `kunftig:input`, `kunftig:hud`,
    `kunftig:camera`, `kunftig:blade`, `kunftig:effect` and `kunftig:pose`;
  * a client registers a channel only when it can draw it, so plugins know who needs a fallback
    (`drawsDisplays`, `drawsRodModels`...).
* **The real client is the TEOF modpack (215 mods), and other mods touch the same player classes.** Never rely on
  owning `player.movementInput`: the Aether replaces it with its own at the end of every client tick (it broke the
  station in the first in-game test). Movement keys are held back and read at the key bindings themselves
  (`client/input/MoveKeys`), for stations and postures alike. Before calling a client feature done, check it against
  the modpack's jars. The server's CastleOfStone only admits mods whose jar SHA-256 is in its `WhitelistMods.yml`.
* **Input binds are per plugin** (`InputGate` owners; `BukkitInputBridge.caller` finds the plugin on the stack): the
  client is told the union, and `getBound` answers the caller's own. And a new pseudo-action has to be let through
  `KunftigInput.bind`'s own check, not only `InputWire.isAction`: `STATION` was refused there with an
  `IllegalArgumentException`, so no helm ever took the keys (`InputApiTest` binds it now).
* **A swapped `movementInput` must not read as a double tap** (`client/input/InputCarry`): in TEOF 6.1 the pool
  table mod wraps the input at the start of every tick and the Aether replaces it at the end, so the player gets a
  blank object each tick and a held Space toggled creative flight. The new object receives what the last one held,
  at the start of the player's tick. Read from both mods' bytecode; in game, to be verified.
* **Displays follow the world's light**: they are drawn in `RenderWorldLastEvent`, when vanilla has already switched
  the light map off, so `DisplayClient` switches it on around them (a ship was as bright at midnight as at noon, seen
  in game); a display with a deck is lit by the brightest of three points up from its origin (the keel is under
  water).
* **A step is only a step with room to stand on it** (`DeckWalker`): a ledge under a deck, a gun port, the slices of
  a raked side are walls. Other players on a deck that moves have the deck's motion taken out of their limb swing and
  told to futura3d (`DeckClient.still`, the `carrier` state, futura3d API 8).
* **Client rendering** of rods and displays goes through GeckoLib Unofficial 1.0.4, loaded reflectively; without it,
  nothing breaks.
* **Displays are drawn from a compiled mesh**: `client/display/gecko/DisplayRenderer` transforms the bones that never
  move once into a vertex buffer (the pure split is `display/StaticBones`) and walks only the moving ones each frame
  (animated, turned by the server, item bones). A still bone is compiled in its rest pose (GeckoLib's first animated
  frame leaves garbage in the bones without a track). `-Dkunftig.displayMesh=false` is the old path. Seen in the dev
  client with the `navio` harness (same pictures, about 2.5 times faster on software GL); on a GPU and under
  Angelica it is still to be checked.

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
  `server`, `observer`, `fish`, `cells`, `edge`, `perf`, `navio [model] [first case]`: what a big display costs
  per frame, single player, with the pack that declares it among the client's resource packs; the model is the
  display id, `expeditions:fragata`, and the headless dev client has left the world about 105 s after joining in
  every run of this harness, cause not found, so the later cases are run by naming the first one):
  * it is **never in the release jar**: `shadowJar` excludes it;
  * for a test jar, comment out that exclude, build, then `git checkout build.gradle.kts`.
* Work on the branch named in the session; no force-push; a merged PR is not reused (merge `main` into the branch and
  open a new PR).
* What is not verified in game is written as "to be checked" in the docs.
