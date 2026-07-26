plugins {
    id("dev.kikugie.stonecutter")
}

// Stonecutter controller. `active` is the version the IDE and the plain `build` task target;
// Stonecutter rewrites it as you switch versions, so leave the marker comment in place.
//
// Build every version at once with `./gradlew chiseledBuild`.
// Publish every version to ~/.m2 (so the other mods can bundle it) with
// `./gradlew publishAllToMavenLocal`.
stonecutter active "1.21.11" /* [SC] DO NOT EDIT */

// Each Stonecutter version node is a subproject (:1.21.8, :1.21.11). Publishing all of them puts
// one kui artifact per Minecraft version in ~/.m2, which is what lets each consumer mod bundle the
// build matching the version it is itself compiling against.
tasks.register("publishAllToMavenLocal") {
    group = "kui"
    description = "Publishes every Minecraft target to ~/.m2 for the mods that depend on kui."
    dependsOn(subprojects.map { "${it.path}:publishToMavenLocal" })
}

// Every target writes the same ~/.m2 maven-metadata-local.xml. With org.gradle.parallel=true the
// version nodes race on that file and leave it corrupt, which then fails every later publish, so
// the publication tasks are forced into a sequence.
gradle.projectsEvaluated {
    subprojects
        .mapNotNull { it.tasks.findByName("publishMavenJavaPublicationToMavenLocal") }
        .zipWithNext { earlier, later -> later.mustRunAfter(earlier) }
}
