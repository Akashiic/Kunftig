# Kunftig

Backports of features from recent Minecraft versions to **1.7.10** (Forge + UniMixins), with the server side for
**Bukkit** (Thermos/Crucible 1.7.10 and Paper/Spigot up to 1.21+).

> Versão em português: [README.pt-BR.md](README.pt-BR.md). The design study is in Portuguese:
> [docs/nametags/ESTUDO.md](docs/nametags/ESTUDO.md); the protocol spec is in English:
> [docs/nametags/PROTOCOL.md](docs/nametags/PROTOCOL.md).

## Feature 1: modern nametags (experimental)

> Experimental: covered by automated tests, not yet validated on a live server with real clients
> (see the manual test plan in `docs/nametags/ESTUDO.md`, section 9).

Team prefixes, suffixes and names of **any length**, with **24-bit colors** and **gradients**, in the tab list and
above heads, never broken mid-color. This is how teams behave from Minecraft 1.13/1.16 on, brought to 1.7.10
clients.

* Clients with the mod see the full text; everyone else sees a safe vanilla version (16 chars below 1.13, cut
  without ever splitting a color code, or a short text chosen by the admin).
* Works behind **Velocity** and through **ViaVersion** (a Paper 1.21 lobby) with nothing installed on the proxy.
* Compatible with **NecroTempus** (tab list), **Angelica** (RGB detected automatically) and other mods: Kunftig
  overrides the team's data, not each screen that draws it.

| Where | Jar | Requirements |
|---|---|---|
| Client | `kunftig-<version>.jar` in `mods/` | Forge 10.13.4.1614, UniMixins; for RGB, Angelica with `enableRGBColors` |
| Server | `Kunftig-Bukkit-<version>.jar` in `plugins/` | Thermos/Crucible 1.7.10 or Spigot/Paper 1.8+, Java 8+ |
| Proxy | nothing | — |

Configuration, commands and API: see [README.pt-BR.md](README.pt-BR.md) and the commented
`bukkit/src/main/resources/config.yml`.

## Feature 2: the 1.21.1 fishing API (server side)

1.21.1's fishing cycle for Bukkit plugins on Thermos/Crucible: the `LURED`, `BITE`, `FAILED_ATTEMPT` and `REEL_IN`
states (`KunftigFishEvent`) and the modern `FishHook` (`ModernFishHook`: wait and lure times, lure angle, Lure
enchantment, rain/sky influence, open water, hook state), plus the bite window for minigames. The default profile
keeps fishing exactly vanilla; `MODERN_1_21_1` uses 1.21.1's timings and the open-water treasure rule. Install the same
mod jar in the server's `mods/`. Guide (Portuguese): [docs/fishing/API.md](docs/fishing/API.md); study:
[docs/fishing/ESTUDO.md](docs/fishing/ESTUDO.md).

## Repository layout

| Path | Contents |
|---|---|
| `src/` | Forge 1.7.10 mod (GTNH convention / RetroFuturaGradle): client nametags, server fishing engine and its Bukkit API |
| `common/` | dependency-free Java 8 library shared by mod and plugin: protocol codec, text model, JSON, legacy renderer |
| `bukkit/` | Bukkit plugin, one jar for 1.7.10 through 1.21+ |
| `scripts/` | builds the Bukkit 1.7.10 API from Crucible's sources for the compatibility gate |

## Building

```bash
./gradlew build
```

Produces `build/libs/kunftig-<version>.jar` and `bukkit/build/libs/Kunftig-Bukkit-<version>.jar`, and runs the
unit and MockBukkit integration tests. The version comes from git tags; without one, set `VERSION=...`.

Bukkit 1.7.10 binary-compatibility gate (mod and plugin):

```bash
scripts/build-legacy-bukkit-api.sh build/legacy-bukkit/bukkit-1.7.10.jar
```

```bash
./gradlew checkLegacyBukkitApi -PlegacyBukkitApi=build/legacy-bukkit/bukkit-1.7.10.jar
```

## License

Not chosen yet. Third-party libraries bundled in the plugin (Adventure, MIT) are listed in
`META-INF/THIRD-PARTY-NOTICES.txt` inside the jar.
