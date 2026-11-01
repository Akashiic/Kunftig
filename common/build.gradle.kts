plugins {
    `java-library`
}

group = "com.akashic.kunftig"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}

tasks.withType<JavaCompile>().configureEach {
    // Runs inside Minecraft 1.7.10 clients and Thermos/Crucible servers: Java 8 bytecode, Java 8 API.
    options.release.set(8)
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-options", "-Werror"))
}

tasks.test {
    useJUnitPlatform()
}
