import me.modmuss50.mpp.ReleaseType
import me.modmuss50.mpp.platforms.modrinth.ModrinthEnvironment

plugins {
    id("dev.kikugie.stonecutter")
    id("me.modmuss50.mod-publish-plugin") version "2.1.1"
}

stonecutter active file(".sc_active_version")

stonecutter parameters {
    val loader = current.project.substringAfterLast('-')
    val version = current.project.removeSuffix("-$loader")

    properties {
        tags(version, loader)
    }

    constants {
        match(loader, "fabric", "neoforge")
    }

    swaps["mod_version"] = "\"${property("mod.version")}\";"
    swaps["minecraft"] = "\"${node.metadata.version}\";"
}

val versionTargets = stonecutter.tree.nodes.map { node ->
    node.metadata.version to stonecutter.properties.raw(node.metadata.version, "mod", "mc_releases").asList()
        .map { it.toString() }
}.distinctBy { it.first }

val availableVersions = versionTargets.map { it.first }.toSet()
val publishVersions =
    providers.environmentVariable("PUBLISH_VERSIONS").orNull?.takeUnless { it.isBlank() }?.split(Regex("[,\\s]+"))
        ?.filter { it.isNotBlank() }?.toSet() ?: availableVersions
val unknownPublishVersions = publishVersions - availableVersions

require(unknownPublishVersions.isEmpty()) {
    "Unknown publish versions: ${unknownPublishVersions.joinToString()}"
}

val publishTargets = versionTargets.filter { (version, _) ->
    version in publishVersions
}

require(publishTargets.isNotEmpty()) {
    "PUBLISH_VERSIONS did not select any Minecraft versions"
}

val modSlug = property("mod.slug") as String
val modName = property("mod.name") as String
val modVersion = property("mod.version") as String
val loaders = listOf("fabric", "neoforge")
val publishingAllVersions = publishTargets.map { it.first }.toSet() == availableVersions
val githubVersion = if (publishingAllVersions) modVersion else "$modVersion+${publishTargets.joinToString("-") { it.first }}"
val publishCurseForge = providers.gradleProperty("publishCurseForge").map(String::toBoolean).orElse(true).get()
val publishModrinth = providers.gradleProperty("publishModrinth").map(String::toBoolean).orElse(true).get()
val publishGitHub = providers.gradleProperty("publishGitHub").map(String::toBoolean).orElse(true).get()
val artifactDirectory = layout.buildDirectory.dir("libs/$modVersion")

fun artifactVersion(version: String) = "$modVersion+$version"
fun artifactFile(loader: String, version: String) = "$modSlug-$loader-${artifactVersion(version)}.jar"
fun publishTaskSuffix(loader: String, version: String) =
    "$loader${version.replace('.', '_')}".replaceFirstChar(Char::uppercase)
fun artifactProvider(loader: String, version: String) = artifactDirectory.map {
    it.file(artifactFile(loader, version))
}

val buildAll = tasks.register<Sync>("buildAll") {
    group = "build"
    description = "Builds and collects jars for every supported Minecraft version"
    into(artifactDirectory)
    outputs.upToDateWhen { false }
}

gradle.projectsEvaluated {
    buildAll.configure {
        dependsOn(subprojects.map { it.tasks.named("build") })
        subprojects.forEach { target ->
            from(target.tasks.named<Jar>("jar").flatMap { it.archiveFile })
            from(target.tasks.named<Jar>("sourcesJar").flatMap { it.archiveFile })
        }
    }
}

publishMods {
    changelog.set(providers.environmentVariable("CHANGELOG"))
    version.set(modVersion)
    displayName.set("$modName $modVersion")
    type.set(ReleaseType.valueOf(providers.gradleProperty("releaseType").orElse("STABLE").get().uppercase()))

    dryRun.set(
        providers.gradleProperty("publishDryRun").map(String::toBoolean).orElse(false)
    )

    val curseForgeOptions = curseforgeOptions {
        projectId.set("1676491")
        accessToken.set(providers.environmentVariable("CURSEFORGE_API_KEY"))
        client.set(false)
        server.set(true)
    }

    val modrinthOptions = modrinthOptions {
        projectId.set("zDBsarpj")
        accessToken.set(providers.environmentVariable("MODRINTH_TOKEN"))
        environment.set(ModrinthEnvironment.SERVER_ONLY)
    }

    publishTargets.forEach { (mcVersion, mcReleases) ->
        loaders.forEach { loader ->
            val targetVersion = artifactVersion(mcVersion)
            val artifact = artifactProvider(loader, mcVersion)
            val taskSuffix = publishTaskSuffix(loader, mcVersion)

            if (publishCurseForge) {
                curseforge("curseforge$taskSuffix") {
                    from(curseForgeOptions)
                    file.set(artifact)
                    version.set(targetVersion)
                    displayName.set("$modName $targetVersion")
                    modLoaders.add(loader)
                    minecraftVersions.addAll(mcReleases)
                    javaVersions.add(JavaVersion.VERSION_25)
                }
            }

            if (publishModrinth) {
                modrinth("modrinth$taskSuffix") {
                    from(modrinthOptions)
                    file.set(artifact)
                    version.set(targetVersion)
                    displayName.set("$modName $targetVersion")
                    modLoaders.add(loader)
                    minecraftVersions.addAll(mcReleases)
                }
            }
        }
    }

    if (publishGitHub) {
        github {
            repository.set(
                providers.environmentVariable("GITHUB_REPOSITORY")
                    .orElse("khazoda-mods/better-zombie-leaders")
            )
            accessToken.set(providers.environmentVariable("GITHUB_TOKEN"))
            commitish.set(
                providers.environmentVariable("GITHUB_REF_NAME").orElse("main")
            )
            version.set(githubVersion)
            displayName.set("$modName $githubVersion")
            tagName.set("v$githubVersion")

            val githubArtifacts = publishTargets.flatMap { (mcVersion, _) ->
                loaders.map { loader ->
                    artifactProvider(loader, mcVersion)
                }
            }

            file.set(githubArtifacts.first())

            additionalFiles.from(
                githubArtifacts.drop(1)
            )
        }
    }
}

tasks.matching { it.name.startsWith("publish") }.configureEach {
    dependsOn(buildAll)
}
