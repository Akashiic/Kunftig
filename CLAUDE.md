# Kunftig: context for whoever works in this repository

Forge 1.7.10 mod (client + server) of backports for the TEOF modpack, with a Bukkit-facing API (`api/`, the
`kunftig-api` that plugins compile against, `compileOnly`). Its main consumer is
[AkashicFishing](https://github.com/Akashiic/AkashicFishing); start there with `docs/HANDOFF.md` for the current state
of both projects (open PRs, merge order, what is still to be checked on the real server).

## Where things are

* **API** (`api/`, `KunftigApi.API_VERSION`, now **8**):

  | API | Feature |
  |---|---|
  | fishing | `KunftigFishEvent`, `ModernFishHook` |
  | 2 | particles, ping |
  | 4 | item icons |
  | 5 | line lock, aimed cast |
  | 6 | holograms |
  | 7 | 3D rods (`KunftigRods`) |
  | 8 | displays (`KunftigDisplays`): GeckoLib models placed in the world, with item stacks drawn at bones (AkashicFishing's Caixa da Sorte) |

  The guide is in `docs/API.md` (Portuguese; the displays are in section 13). Raise the version and update the version
  asserts in the API tests when adding to it.
* **Wire protocols** are raw plugin messages:
  * `kunftig:holo`, `kunftig:rod` and `kunftig:display`;
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
  file in the client's run directory: `caixa`, `skins`, `server`, `observer`, `fish`, `cells`, `edge`, `perf`):
  * it is **never in the release jar**: `shadowJar` excludes it;
  * for a test jar, comment out that exclude, build, then `git checkout build.gradle.kts`.
* Work on the branch named in the session; no force-push; a merged PR is not reused (merge `main` into the branch and
  open a new PR).
* What is not verified in game is written as "to be checked" in the docs.
