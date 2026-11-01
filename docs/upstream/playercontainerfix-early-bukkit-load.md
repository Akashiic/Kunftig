# Draft issue for PlayerContainerFix

> Not filed yet. PlayerContainerFix 2.0.0 (`com.juanmuscaria.playercontainerfix`), source reported as
> [GTNewHorizons/BQ3-Thermos-patch](https://github.com/GTNewHorizons/BQ3-Thermos-patch); confirm the repository
> before filing. Everything below was observed on a copy of the TEOF server (Kunftig issue #3). Copy from the title down.

---

**Title:** Coremod loads `org.bukkit.World` with `Class.forName` before FML's deobfuscation, leaving it with obfuscated names (`getWorldServer()` returns `mt`) for every plugin

**Environment:** Crucible `1.7.10-staging-67b53034`, Java 21, PlayerContainerFix 2.0.0, ~216 mods.

### What happens

`FMLCoreMod.getMixins` detects Bukkit with `Class.forName("org.bukkit.World")` (line 52). It runs while coremods are
injected:

```
org/bukkit/World by net.minecraft.launchwrapper.LaunchClassLoader
  at net.minecraft.launchwrapper.LaunchClassLoader.findClass(LaunchClassLoader.java:334)
  at com.juanmuscaria.playercontainerfix.FMLCoreMod.getMixins(FMLCoreMod.java:52)
  at com.gtnewhorizon.gtnhmixins.core.GTNHMixinsCore.injectData(GTNHMixinsCore.java:112)
  at io.github.legacymoddingmc.unimixins.all.AllCore.injectData(AllCore.java:68)
  at cpw.mods.fml.relauncher.CoreModManager$FMLPluginWrapper.injectIntoClassLoader(CoreModManager.java:128)
  ...
```

Crucible's jar stores its classes with obfuscated (notch) names, and FML renames them as they load through
`DeobfuscationTransformer`. FML registers that transformer last (`FMLDeobfTweaker`, after the coremods are injected).
So `org.bukkit.World`, loaded here, is never deobfuscated and keeps `public abstract mt getWorldServer();` for the rest
of the server's life. `mt` does not exist at runtime.

### Consequences (observed)

With a test plugin compiled against the server jar:

| | With PlayerContainerFix | Without it |
|---|---|---|
| `world.getWorldServer()` | `NoSuchMethodError` | OK |
| `MethodHandles.publicLookup().findVirtual(World.class, "getWorldServer", methodType(WorldServer.class))` | `NoSuchMethodException` | OK |
| `World.class.getMethods()` (and any `World.class.getMethod(...)`) | `NoClassDefFoundError: mt` | OK (128 methods) |

Any plugin or library that calls `getWorldServer()` or reflects over `World` (command frameworks, Skript addons, NMS
helpers, feature detection) breaks, with an error that does not point at the cause.

### Suggested fix

Detect Bukkit without loading the class, for example:

```java
boolean bukkit = Launch.classLoader.getClassBytes("org.bukkit.World") != null;
// or
boolean bukkit = FMLCoreMod.class.getClassLoader().getResource("org/bukkit/World.class") != null;
```

`Class.forName("org.bukkit.World", false, loader)` is not enough: it skips initialization but still loads (and so
freezes) the class.
