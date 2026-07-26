pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.kikugie.dev/releases")
        maven("https://maven.kikugie.dev/snapshots")
    }
}

plugins {
    // Multi-version toolchain: one source tree, many Minecraft versions.
    // https://stonecutter.kikugie.dev
    id("dev.kikugie.stonecutter") version "0.8.4"
}

stonecutter {
    // These targets must be a superset of the targets of every mod that depends on kui,
    // because a consumer can only bundle a kui build for the version it is itself building.
    create(rootProject) {
        versions("1.21.8", "1.21.11")
        vcsVersion = "1.21.11"
    }
}

rootProject.name = "kui"
