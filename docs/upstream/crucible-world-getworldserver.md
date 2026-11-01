# Draft issue for CrucibleMC/Crucible

> Ready to file: everything below was verified on the production jar (Kunftig issue #3). Kunftig cannot fix this:
> the defect is in Crucible's released jar. Copy from the title down.

---

**Title:** Released jar: `org.bukkit.World#getWorldServer()` is declared as returning the obfuscated class `mt`, so plugins calling it get `NoSuchMethodError`

**Version:** `1.7.10-staging-67b53034` (release `staging-67b5303`), Java 21. Reproduced on a copy of a production
server (216 mods, no third-party plugins) with a minimal test plugin.

### What happens

In the released server jar, `org/bukkit/World.class` declares:

```
public abstract mt getWorldServer();
```

`mt` is the obfuscated (notch) name of `net.minecraft.world.WorldServer`, and no class named `mt` exists at runtime.
The sources are correct: `patches/org/bukkit/World.java.patch` imports `net.minecraft.world.WorldServer` and declares
`public abstract WorldServer getWorldServer();`. So the descriptor is rewritten to the notch name when the jar is
built.

### Consequences (all observed)

A test plugin compiled against the production `server.jar` (as any plugin compiled against Crucible is) contains
`invokeinterface org/bukkit/World.getWorldServer:()Lmt;`. On the server:

```
world class: org.bukkit.craftbukkit.v1_7_R4.CraftWorld
1 direct call: java.lang.NoSuchMethodError: 'net.minecraft.world.WorldServer org.bukkit.World.getWorldServer()'
2 findVirtual(World, ()WorldServer): java.lang.NoSuchMethodException: no such method: org.bukkit.World.getWorldServer()WorldServer/invokeInterface
3 findVirtual(CraftWorld, ()WorldServer): OK -> net.minecraft.world.WorldServer
```

1. **Every direct call `world.getWorldServer()` fails with `NoSuchMethodError`.** The plugin remapper rewrites the
   call's `mt` to `net/minecraft/world/WorldServer`, but the `org.bukkit.World` interface is not remapped and still
   declares `()Lmt;`, so the call no longer matches the interface method.
2. **Reflection on `World` fails**: `World.class.getMethods()`, and also `World.class.getMethod("anyName", ...)`,
   throw `java.lang.NoClassDefFoundError: mt`, because the JDK resolves the signatures of every public method to look
   one up. This breaks any plugin or library that reflects over `World` (command frameworks, Skript addons, NMS
   helpers, feature detection), with an error that does not point at the cause.
3. `CraftWorld` itself is fine: it is remapped at load time and has `getWorldServer()` returning `WorldServer` (3
   above).

**Workaround until fixed:** resolve on the concrete class, never on the interface:
`MethodHandles.publicLookup().findVirtual(world.getClass(), "getWorldServer", MethodType.methodType(WorldServer.class))`.

### Same defect in other classes

Scanning the class files of the released jar (outside `org/bukkit/craftbukkit/`) for descriptors that name classes in
the default package finds 31 members. `World#getWorldServer` is the only one in the plugin API; the other 30 are in
`org.spigotmc` internals and very likely come from the same build step:

<details>
<summary>Full list (31)</summary>

```
public org.bukkit.World#getWorldServer()Lmt; -> [mt] (not in the jar)
private org.spigotmc.ActivationRange#activateChunkEntities(Lapx;)V -> [apx] (not in the jar)
public org.spigotmc.ActivationRange#activateEntities(Lahb;)V -> [ahb] (not in the jar)
package org.spigotmc.ActivationRange#animalBB:Lazt; -> [azt] (not in the jar)
public org.spigotmc.ActivationRange#checkEntityImmunities(Lsa;)Z -> [sa] (not in the jar)
public org.spigotmc.ActivationRange#checkIfActive(Lsa;)Z -> [sa] (not in the jar)
public org.spigotmc.ActivationRange#growBB(Lazt;Lazt;III)V -> [azt] (not in the jar)
public org.spigotmc.ActivationRange#initializeEntityActivationState(Lsa;Lorg/spigotmc/SpigotWorldConfig;)Z -> [sa] (not in the jar)
public org.spigotmc.ActivationRange#initializeEntityActivationType(Lsa;)B -> [sa] (not in the jar)
package org.spigotmc.ActivationRange#maxBB:Lazt; -> [azt] (not in the jar)
package org.spigotmc.ActivationRange#miscBB:Lazt; -> [azt] (not in the jar)
package org.spigotmc.ActivationRange#monsterBB:Lazt; -> [azt] (not in the jar)
private org.spigotmc.AntiXray#hasTransparentBlockAdjacent(Lahb;IIII)Z -> [ahb] (not in the jar)
private org.spigotmc.AntiXray#isLoaded(Lahb;IIII)Z -> [ahb] (not in the jar)
public org.spigotmc.AntiXray#obfuscate(III[BLahb;)V -> [ahb] (not in the jar)
public org.spigotmc.AntiXray#updateNearbyBlocks(Lahb;III)V -> [ahb] (not in the jar)
private org.spigotmc.AntiXray#updateNearbyBlocks(Lahb;IIIIZ)V -> [ahb] (not in the jar)
public org.spigotmc.TrackingRange#getEntityTrackingRange(Lsa;I)I -> [sa] (not in the jar)
public org.spigotmc.VanillaCommandWrapper$ConsoleListener#<class> -> [ac] (not in the jar)
public org.spigotmc.VanillaCommandWrapper$ConsoleListener#a(Lfj;)V -> [fj] (not in the jar)
public org.spigotmc.VanillaCommandWrapper$ConsoleListener#c_()Lfj; -> [fj] (not in the jar)
public org.spigotmc.VanillaCommandWrapper$ConsoleListener#d()Lahb; -> [ahb] (not in the jar)
public org.spigotmc.VanillaCommandWrapper$ConsoleListener#f_()Lr; -> [r] (not in the jar)
public org.spigotmc.VanillaCommandWrapper$PlayerListener#<class> -> [ac] (not in the jar)
public org.spigotmc.VanillaCommandWrapper$PlayerListener#<init>(Lac;)V -> [ac] (not in the jar)
public org.spigotmc.VanillaCommandWrapper$PlayerListener#a(Lfj;)V -> [fj] (not in the jar)
public org.spigotmc.VanillaCommandWrapper$PlayerListener#c_()Lfj; -> [fj] (not in the jar)
public org.spigotmc.VanillaCommandWrapper$PlayerListener#d()Lahb; -> [ahb] (not in the jar)
public org.spigotmc.VanillaCommandWrapper$PlayerListener#f_()Lr; -> [r] (not in the jar)
private org.spigotmc.VanillaCommandWrapper$PlayerListener#handle:Lac; -> [ac] (not in the jar)
private org.spigotmc.VanillaCommandWrapper#getListener(Lorg/bukkit/command/CommandSender;)Lac; -> [ac] (not in the jar)
31 member(s) name default-package classes
```

</details>

Produced with Kunftig's scanner, which reads the class files without loading them:
`java -cp kunftig-<version>.jar com.akashic.kunftig.diagnostics.ObfuscatedApiScanner server.jar`.

### Suggested fix

Make the build keep runtime names in `org/bukkit/` and `org/spigotmc/` (for `World`:
`()Lnet/minecraft/world/WorldServer;`), and consider a build check that fails when a class outside
`org/bukkit/craftbukkit/` and `net/minecraft/` names a class in the default package.
