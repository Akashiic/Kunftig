
plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

// The resource pack for RodPackCheckTest (3D rods, issue #13): ./gradlew :test -ProdPack=<folder with assets/>
val rodPack: String? = providers.gradleProperty("rodPack").orNull?.let { rootProject.file(it).absolutePath }

// SlashBlade's own files for SlashBladeAssetsTest (not in this repository): ./gradlew :test -PsbAssets=<assets/slashblade>
val sbAssets: String? = providers.gradleProperty("sbAssets").orNull?.let { rootProject.file(it).absolutePath }

tasks.test {
    useJUnitPlatform()
    rodPack?.let { systemProperty("kunftig.rodPack", it) }
    sbAssets?.let { systemProperty("kunftig.sbAssets", it) }
}

/*
 * The mod implements kunftig-api (:api) on Thermos/Crucible in com.akashic.kunftig.bukkit. It compiles against
 * spigot-api 1.8.8; this gate recompiles that package against the Bukkit 1.7.10 API those servers ship, so a member
 * that only exists in 1.8+ fails the build instead of a server (:api has the same gate):
 *   ./gradlew checkLegacyBukkitApi -PlegacyBukkitApi=build/legacy-bukkit/bukkit-1.7.10.jar   (relative to the repository root)
 * (scripts/build-legacy-bukkit-api.sh builds that jar from Crucible's sources.)
 */
// Relative paths resolve from the repository root, not from this module (issue #4).
// Resolved here, not in task actions: the configuration cache cannot serialize lambdas that capture the script.
val legacyBukkitApi: File? = providers.gradleProperty("legacyBukkitApi").orNull?.let { rootProject.file(it) }
val checkLegacyBukkitApi by tasks.registering(JavaCompile::class) {
    description = "Recompiles the mod's Bukkit-facing code against the Bukkit 1.7.10 API (-PlegacyBukkitApi=<jar>)."
    group = "verification"
    val jar = legacyBukkitApi
    onlyIf("-PlegacyBukkitApi is set") { jar != null }
    doFirst {
        if (jar == null || !jar.isFile) {
            throw GradleException("Bukkit 1.7.10 API jar not found: $jar (build it with scripts/build-legacy-bukkit-api.sh)")
        }
    }
    val main = sourceSets.main.get()
    source = fileTree("src/main/java") {
        include("com/akashic/kunftig/bukkit/**")
    }
    classpath = files(listOfNotNull(jar)) +
        main.compileClasspath.filter { !it.name.startsWith("spigot-api-") && !it.name.contains("jabel") } +
        main.output
    destinationDirectory.set(layout.buildDirectory.dir("legacyApiCheck"))
    options.release.set(8)
    options.compilerArgs.add("-proc:none")
    // The convention wires Jabel into every JavaCompile; the gated code is plain Java 8 and needs no plugin.
    options.annotationProcessorPath = files()
    doFirst {
        (this as JavaCompile).options.compilerArgs.removeIf { it.startsWith("-Xplugin:") }
    }
    dependsOn(tasks.compileJava)
}

// The development test harness (com.akashic.kunftig.dev, -Dkunftig.devHarness=true) never ships.
tasks.named<Jar>("shadowJar") {
    exclude("com/akashic/kunftig/dev/**")
}
