plugins {
    id("fabric-loom") version "1.13.6"
    id("maven-publish")
}

// This build script is shared by every Stonecutter version node (versions/<mc>/). The Minecraft,
// yarn, Fabric API and dependency-range values are read per node from versions/<mc>/gradle.properties;
// the shared mod identity and loader version come from the root gradle.properties.
//
// The published coordinate is dev.kui:kui:<mod_version>+mc<minecraft_version>, so a consumer mod
// bundles the kui build matching the Minecraft version it is itself compiling against.
version = "${property("mod_version")}+mc${property("minecraft_version")}"
group = property("maven_group") as String

base {
    archivesName = property("archives_base_name") as String
}

repositories {
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    mappings("net.fabricmc:yarn:${property("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_version")}")
}

// Read at project scope: inside the task block `property(...)` would resolve against the task, not the project.
val fabricModExpansions = mapOf(
    "version" to project.version.toString(),
    "minecraft" to property("minecraft_dep").toString(),
)

tasks.processResources {
    inputs.properties(fabricModExpansions)
    filesMatching("fabric.mod.json") { expand(fabricModExpansions) }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
    options.encoding = "UTF-8"
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            // Without this the artifactId would default to the Stonecutter node's project name
            // ("1.21.11"), producing coordinates like dev.kui:1.21.11 instead of dev.kui:kui.
            artifactId = property("archives_base_name") as String
            // Loom swaps in the remapped (production) jar, which is what consumers must bundle.
            from(components["java"])
        }
    }
    repositories {
        // Local-only for now: `./gradlew chiseledPublishToMavenLocal` makes every target
        // resolvable from the other mods on this machine. Add a real repository here when
        // kui starts shipping to other people.
        mavenLocal()
    }
}
