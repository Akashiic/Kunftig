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

## Feature 8: input, screen HUD, lock-on and attack prediction (API 11)

For action combat on 1.7.10 (the Overkill plugin): **`KunftigInput`** gives the client keys of its own in Controls
(special, skill, dodge, lock on) that reach plugins as `KunftigInputEvent`, validated and rate limited;
**`KunftigHud`** draws text, bars and pictures on a player's screen; **`KunftigCamera`** locks the view on a target;
**displays** glide, fly by themselves and turn on every axis (`Display#moveTo`, `setMotion`, `setRotation`); and a
3D held model can start the next attack of a combo on the click (`ao_atacar`), confirmed by the server without
restarting. See [docs/API.md](docs/API.md), sections 13 and 16 to 20 (Portuguese).

## Feature 9: walking on displays (API 12)

A display can carry **solid boxes** (`Display#setCollision`): players walk on them, are stopped by the tall ones and
are carried along while the display moves, turns, pitches and rolls. It is the deck of the ships of the Expeditions
plugin. It runs on each player's client, against the pose the client draws. See [docs/API.md](docs/API.md),
section 21 (Portuguese).

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

## Feature 6: animated 3D fishing rods (client + server)

With **GeckoLib Unofficial 1.0.4** on the client (the TEOF modpack's), the rods a resource pack declares in
`assets/<namespace>/kunftig/item_models_3d.json` are drawn as animated Bedrock models in hand (first and third
person), on the ground and in item frames, with mutation stages, glowing and translucent parts, the fishing line
leaving the tip and the arm following each state. Plugins send what only the server knows (bite, fight with tension,
catch) through `KunftigRods` (API 7). Without GeckoLib or the pack, or with `-Dkunftig.rodModels.disable=true`, rods
keep their 2D icons. See [docs/rodmodels/README.md](docs/rodmodels/README.md) and [docs/API.md](docs/API.md),
section 12 (Portuguese).

## Feature 7: animated 3D displays in the world (client + server)

Plugins place a GeckoLib model at a location through `KunftigDisplays` (API 8): a state (animation), a texture
variant and up to 8 item stacks drawn at bones of the model. The server tracks who is in range (late joiners see the
animation where it is) and the client draws it with world light, glowing and translucent bones. Models come from
`assets/<namespace>/kunftig/displays.json`. AkashicFishing's Caixa da Sorte uses it. API 9 adds model and per-display
scale, bones hidden and shown at runtime, blends between states and glides between positions (AkashicFishing's lake
boss). See [docs/API.md](docs/API.md), section 13 (Portuguese).

## Feature 8: flight lock (server + client)

`KunftigPlayers.setFlightLocked` (API 9) keeps a player from flying, whatever gives the flight: the server switches
the creative-style flight off at the end of each of the player's ticks (rings, armour, baubles, spells, Bukkit's
`setAllowFlight`), and a Kunftig client also cuts upward speed that does not come from a jump or a push (jetpacks,
gliders). See [docs/API.md](docs/API.md), section 14 (Portuguese).

`ModernFishHook#setMaxDistance` (API 10) lets a hook go farther than vanilla's 32 blocks from its angler (up to 128),
for casts at far targets such as AkashicFishing's boss. See [docs/API.md](docs/API.md), section 15.

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
