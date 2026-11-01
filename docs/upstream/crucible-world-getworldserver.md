# Draft issue for CrucibleMC/Crucible

> Not filed yet. Kunftig is not the place to fix this: the defect is in Crucible's released jar. Review the
> "Verified" list against your own server before filing, and paste the output of the scanner command below.

---

**Title:** `org.bukkit.World#getWorldServer()` returns the obfuscated type `mt` in the released jar, so reflection on `World` throws `NoClassDefFoundError`

**Version:** `1.7.10-staging-67b53034` (release `staging-67b5303`), Java 21.

### What happens

In the released server jar, `org/bukkit/World.class` declares:

```
public abstract mt getWorldServer();
```

`mt` is the obfuscated (notch) name of `net.minecraft.world.WorldServer`. No class named `mt` exists at runtime, so any
reflection that resolves the method list of `World` fails:

```java
World.class.getMethods();                 // java.lang.NoClassDefFoundError: mt
World.class.getMethod("getName");         // also NoClassDefFoundError: mt
```

`Class#getMethod` fails too because the JDK resolves the signatures of every public method to look one up. Direct
calls (`world.getName()`) work, since the JVM resolves only the method that is called.

### Why it matters

Plugins and libraries that reflect over `World` (command frameworks, Skript addons, NMS helper libraries, feature
detection code) fail on Crucible with an error that does not point at the cause.

### Source vs. jar

The source is correct: `patches/org/bukkit/World.java.patch` imports `net.minecraft.world.WorldServer` and declares
`public abstract WorldServer getWorldServer();`, and `CraftWorld#getWorldServer()` returns `WorldServer`. So the
descriptor appears to be rewritten to the notch name when the jar is built (reobfuscation applied to
`org/bukkit/World` with notch names instead of the runtime names).

In the sources, this is the only public Bukkit API signature that exposes a Minecraft type: the other patches under
`patches/org/bukkit/` (`Material`, `JavaPluginLoader`, `PluginClassLoader`, `ReloadCommand`) use Minecraft/Forge types
only inside method bodies.

### Reproduce

```java
try {
    org.bukkit.World.class.getMethods();
} catch (NoClassDefFoundError e) {
    getLogger().severe("World reflection broken: " + e);   // prints: NoClassDefFoundError: mt
}
```

Or list every affected member of the jar without starting the server (reads class files, loads nothing):

```bash
java -cp kunftig-<version>.jar com.akashic.kunftig.diagnostics.ObfuscatedApiScanner crucible-server.jar
```

### Suggested fix

Make the build keep the runtime name in the Bukkit API classes (`Lnet/minecraft/world/WorldServer;`), and consider a
build check that fails when a class under `org/bukkit/` (outside `craftbukkit`) names a class in the default package.

### Worth checking while there

With the interface declaring `()Lmt;` and `CraftWorld` implementing `()Lnet/minecraft/world/WorldServer;`, a call
through the interface (`player.getWorld().getWorldServer()`, as in `CrucibleCommand`) may not reach the
implementation (`AbstractMethodError`). This was not tested.

### Verified

- [x] `NoClassDefFoundError: mt` from `World.class.getMethods()` and `getMethod(...)` on the production jar
  (AkashicFishing diagnostic probe, twice).
- [x] Source declares `WorldServer` (`patches/org/bukkit/World.java.patch`).
- [ ] Scanner output on the production jar (paste here).
- [ ] `AbstractMethodError` on `getWorld().getWorldServer()` (see above).
