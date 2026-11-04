# Künftig

Backports of features from recent Minecraft versions to **1.7.10** (Forge + UniMixins): one mod jar for the client
and the server (Thermos/Crucible 1.7.10), plus the `kunftig-api` artifact for plugins.

Each CI run (Actions) uploads two artifacts: **`kunftig-mod`**, the jar for `mods/` (server and client), and
**`kunftig-api`**, only for compiling plugins. The API jar is not a mod: in `mods/` it does nothing.

> Versão em português: [README.pt-BR.md](README.pt-BR.md). The design study is in Portuguese:
> [docs/nametags/ESTUDO.md](docs/nametags/ESTUDO.md); the protocol spec is in English:
> [docs/nametags/PROTOCOL.md](docs/nametags/PROTOCOL.md).

## Feature 1: modern nametag engine on the client (experimental)

> Experimental, client side only. The server plugin (Kunftig-Bukkit) was removed: the network uses TAB. How the server
> feeds the engine is still open.

A 1.7.10 client with Kunftig accepts team prefixes, suffixes and names of **any length**, with **24-bit colors** and
**gradients**, in the tab list and above heads, never broken mid-color, as teams behave from 1.13/1.16 on. It receives
them on the `kunftig:nametag` channel ([protocol](docs/nametags/PROTOCOL.md)) and overrides the team's data, not each
screen, so it works with **NecroTempus** and **Angelica**.

## Feature 2: the 1.21.1 fishing API (server side)

1.21.1's fishing cycle for Bukkit plugins on Thermos/Crucible: the `LURED`, `BITE`, `FAILED_ATTEMPT` and `REEL_IN`
states (`KunftigFishEvent`) and the modern `FishHook` (`ModernFishHook`: wait and lure times, lure angle, Lure
enchantment, rain/sky influence, open water, hook state), plus the bite window for minigames and lava fishing. The default profile
keeps fishing exactly vanilla; `MODERN_1_21_1` uses 1.21.1's timings and the open-water treasure rule. Install the same
mod jar in the server's `mods/`. Guide (Portuguese): [docs/fishing/API.md](docs/fishing/API.md); study:
[docs/fishing/ESTUDO.md](docs/fishing/ESTUDO.md).

## Feature 3: persistent data on items (server side)

Bukkit 1.14's `PersistentDataContainer` (with `NamespacedKey` and `PersistentDataType`) for items on Thermos/Crucible,
stored with the modern layout. For tagging custom rods, baits and fish without parsing lore or touching NMS.
See [docs/API.md](docs/API.md).

## Feature 4: world and modern particles (server + client)

`KunftigParticles` sends particles to every player nearby (production Crucible has no `World.spawnParticle`), with
colors on any 1.7.10 client and modern particles (`END_ROD`, `SOUL_FIRE_FLAME`, `FALLING_DUST`, `CHERRY_LEAVES`,
`BUBBLE_COLUMN_UP`...) drawn by **Et Futurum Requiem** on clients with Kunftig; everyone else sees the closest vanilla
particle. Plus `KunftigPlayers.getPing(player)`, 1.17's `Player#getPing`. Study (Portuguese):
[docs/particles/ESTUDO.md](docs/particles/ESTUDO.md).

## Mods that get in the way on Crucible

**PlayerContainerFix (BQ3-Thermos-patch): remove it on Crucible.** It fixes BetterQuesting's `ClassCastException` in
`ContainerPlayer.onCraftMatrixChanged` on **Thermos** ([BetterQuesting#605](https://github.com/Funwayguy/BetterQuesting/issues/605)).
Crucible's own `ContainerPlayer` patch already carries that fix (same `instanceof EntityPlayerMP` check). Worse, its
coremod loads `org.bukkit.World` with `Class.forName` before FML enables deobfuscation, so `World` keeps obfuscated
names: `getWorldServer()` throws `NoSuchMethodError` and reflection on `World` throws `NoClassDefFoundError: mt` for
every plugin. Check before removing it in production: `javap -c -cp server.jar aap | sed -n '/public void a(rb);/,/^$/p' | grep instanceof`
must show `instanceof` of `class mw` (`ContainerPlayer.onCraftMatrixChanged` checking for `EntityPlayerMP`), and
crafting in the 2x2 grid with BetterQuesting and without the mod must not log a `ClassCastException`. Details (Portuguese): [README.pt-BR.md](README.pt-BR.md).

## Feature 5: icons for plugin items (client + server)

1.14's `CustomModelData` for 1.7.10: `KunftigItems.setModel(item, new NamespacedKey(plugin, "tucunare"))` shows the
item with an icon from the resource pack (listed in `assets/<namespace>/kunftig/item_models.json`) in inventories, in
hand, on the ground and in item frames, on clients with Kunftig; other clients see the base item's icon. See
[docs/API.md](docs/API.md), section 9 (Portuguese).

## Repository layout

| Path | Contents |
|---|---|
| `src/` | Forge 1.7.10 mod (GTNH convention / RetroFuturaGradle): client nametags, server fishing engine and its Bukkit API |
| `api/` | `kunftig-api`: what Bukkit plugins compile against (`compileOnly`); embedded in the mod for runtime. See [docs/API.md](docs/API.md) |
| `common/` | dependency-free Java 8 library used by the mod: protocol codec, text model, JSON, legacy renderer |
| `scripts/` | builds the Bukkit 1.7.10 API from Crucible's sources for the compatibility gate |

## Building

```bash
./gradlew build
```

Produces `build/libs/kunftig-<version>.jar` and `api/build/libs/kunftig-api-<version>.jar`, and runs the tests. The version comes from git tags; without one, set `VERSION=...`.

Bukkit 1.7.10 binary-compatibility gate (mod and API):

```bash
scripts/build-legacy-bukkit-api.sh build/legacy-bukkit/bukkit-1.7.10.jar
```

```bash
./gradlew checkLegacyBukkitApi -PlegacyBukkitApi=build/legacy-bukkit/bukkit-1.7.10.jar
```

## License

Not chosen yet. Third-party libraries bundled in the plugin (Adventure, MIT) are listed in
`META-INF/THIRD-PARTY-NOTICES.txt` inside the jar.
