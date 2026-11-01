plugins {
    java
    // Version comes from the GTNH convention, which already has Shadow on the build classpath.
    id("com.gradleup.shadow")
}

group = "com.akashic.kunftig"
version = rootProject.version

repositories {
    mavenCentral()
    // paper-api for the MockBukkit integration tests.
    maven("https://repo.papermc.io/repository/maven-public/") {
        mavenContent { includeGroup("io.papermc.paper"); includeGroup("com.mojang"); includeGroup("net.md-5") }
    }
    // spigot-api 1.8.8 and its bungeecord-chat dependency.
    maven("https://hub.spigotmc.org/nexus/content/groups/public/") {
        mavenContent { includeGroup("org.spigotmc"); includeGroup("org.bukkit"); includeGroup("net.md-5") }
    }
}

val adventureVersion = "4.26.1"

dependencies {
    // Oldest Bukkit API on a public repository. The plugin must also link against the 1.7.10 API
    // (Thermos/Crucible): see the checkLegacyBukkitApi task below.
    compileOnly("org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT")

    implementation(project(":common"))
    // MiniMessage for admin-written text; shaded and relocated so it never clashes with Paper's own Adventure.
    implementation("net.kyori:adventure-api:$adventureVersion")
    implementation("net.kyori:adventure-text-minimessage:$adventureVersion")

    testImplementation("org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}

tasks.withType<JavaCompile>().configureEach {
    // Thermos and Crucible run on Java 8.
    options.release.set(8)
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-options", "-Xlint:-deprecation", "-Werror"))
}

tasks.processResources {
    val version = project.version.toString()
    inputs.property("version", version)
    filesMatching("plugin.yml") {
        expand("version" to version)
    }
}

tasks.shadowJar {
    archiveBaseName.set("Kunftig-Bukkit")
    archiveClassifier.set("")
    relocate("net.kyori", "com.akashic.kunftig.libs.kyori")
    // Annotations are compile-time only.
    dependencies {
        exclude(dependency("org.jetbrains:annotations"))
        exclude(dependency("org.jspecify:jspecify"))
    }
    mergeServiceFiles()
}

tasks.jar {
    // Only the shaded jar is a usable plugin.
    archiveClassifier.set("unshaded")
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

tasks.test {
    useJUnitPlatform()
}

/*
 * Integration tests: the shaded plugin jar (the real artifact) loaded by MockBukkit, a Paper 1.21 API
 * implementation that needs no Minecraft server. Test code compiles against the plugin's classes but runs against
 * the shaded jar, so relocation and plugin.yml are exercised too. MockBukkit needs Java 21.
 */
val integrationTest: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
}
val integrationTestImplementation by configurations.getting
val integrationTestRuntimeOnly by configurations.getting
dependencies {
    integrationTestImplementation(project(":common"))
    integrationTestImplementation("org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.116.3")
    integrationTestImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    integrationTestImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    integrationTestRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.3")
}
tasks.named<JavaCompile>(integrationTest.compileJavaTaskName) {
    options.release.set(21)
}
val java21 = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) }
val integrationTestTask = tasks.register<Test>("integrationTest") {
    description = "Runs the shaded plugin inside MockBukkit (Paper 1.21 API)."
    group = "verification"
    useJUnitPlatform()
    javaLauncher.set(java21)
    testClassesDirs = integrationTest.output.classesDirs
    // Shaded jar first: it must provide every plugin class, exactly as a server would load it.
    classpath = files(tasks.shadowJar) + integrationTest.output + integrationTest.runtimeClasspath
    dependsOn(tasks.shadowJar)
    shouldRunAfter(tasks.test)
}
tasks.check {
    dependsOn(integrationTestTask)
}

/*
 * Binary-compatibility gate against the Bukkit 1.7.10 API that Thermos/Crucible actually ship.
 * No public repository hosts it, so the jar is passed explicitly, e.g. one built from Crucible's bukkit/ module:
 *   scripts/build-legacy-bukkit-api.sh build/legacy-bukkit/bukkit-1.7.10.jar
 *   ./gradlew :bukkit:checkLegacyBukkitApi -PlegacyBukkitApi=build/legacy-bukkit/bukkit-1.7.10.jar   (relative to the repository root)
 * Recompiling the sources against it fails on any member that does not exist in 1.7.10 (Team#addEntry, ...).
 */
// Relative paths resolve from the repository root, not from this module (issue #4).
val legacyBukkitApi = providers.gradleProperty("legacyBukkitApi").map { rootProject.file(it) }
val checkLegacyBukkitApi by tasks.registering(JavaCompile::class) {
    description = "Recompiles the plugin against the Bukkit 1.7.10 API given by -PlegacyBukkitApi=<jar>."
    group = "verification"
    onlyIf("-PlegacyBukkitApi is set") { legacyBukkitApi.isPresent }
    doFirst {
        val jar = legacyBukkitApi.get()
        if (!jar.isFile) {
            throw GradleException("Bukkit 1.7.10 API jar not found: $jar (build it with scripts/build-legacy-bukkit-api.sh)")
        }
    }
    source = sourceSets.main.get().java
    // Same classpath as the real compilation, with the 1.8.8 API swapped for the 1.7.10 one.
    classpath = files(legacyBukkitApi) + configurations.compileClasspath.get().filter {
        !it.name.startsWith("spigot-api-") && !it.name.startsWith("bungeecord-chat-")
    }
    destinationDirectory.set(layout.buildDirectory.dir("legacyApiCheck"))
    options.release.set(8)
}
