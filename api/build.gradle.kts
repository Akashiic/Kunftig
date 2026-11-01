plugins {
    `java-library`
    `maven-publish`
}

group = "com.akashic.kunftig"
version = rootProject.version

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/groups/public/") {
        mavenContent { includeGroup("org.spigotmc"); includeGroup("org.bukkit"); includeGroup("net.md-5") }
    }
}

/*
 * kunftig-api: what plugins compile against (compileOnly). Interfaces, events and value types only; no Minecraft
 * classes. At runtime the same classes come from the Kunftig mod installed on the server, which embeds this module.
 */
dependencies {
    // Oldest Bukkit API on a public repository; checkLegacyBukkitApi verifies against 1.7.10's.
    compileOnly("org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT") { isTransitive = false }

    testImplementation("org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}

java {
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach {
    // Thermos and Crucible run on Java 8.
    options.release.set(8)
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-options", "-Werror"))
}

tasks.javadoc {
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        addBooleanOption("Xdoclint:none", true)
        addStringOption("source", "8")
    }
}

tasks.jar {
    archiveBaseName.set("kunftig-api")
}
tasks.named<Jar>("sourcesJar") { archiveBaseName.set("kunftig-api") }
tasks.named<Jar>("javadocJar") { archiveBaseName.set("kunftig-api") }

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("api") {
            artifactId = "kunftig-api"
            from(components["java"])
            pom {
                name.set("Kunftig API")
                description.set("API of Kunftig: features of recent Minecraft versions for Bukkit plugins on 1.7.10")
                url.set("https://github.com/Akashiic/Kunftig")
            }
        }
    }
}

/* Same gate as :bukkit: recompile against the Bukkit 1.7.10 API given by -PlegacyBukkitApi=<jar>. */
// Relative paths resolve from the repository root, not from this module (issue #4).
val legacyBukkitApi = providers.gradleProperty("legacyBukkitApi").map { rootProject.file(it) }
val checkLegacyBukkitApi by tasks.registering(JavaCompile::class) {
    description = "Recompiles the API against the Bukkit 1.7.10 API given by -PlegacyBukkitApi=<jar>."
    group = "verification"
    onlyIf("-PlegacyBukkitApi is set") { legacyBukkitApi.isPresent }
    doFirst {
        val jar = legacyBukkitApi.get()
        if (!jar.isFile) {
            throw GradleException("Bukkit 1.7.10 API jar not found: $jar (build it with scripts/build-legacy-bukkit-api.sh)")
        }
    }
    source = sourceSets.main.get().java
    classpath = files(legacyBukkitApi)
    destinationDirectory.set(layout.buildDirectory.dir("legacyApiCheck"))
    options.release.set(8)
}
