plugins {
    id("net.neoforged.moddev") version "2.0.147"
    id("neoforge-mutex")
}

version = "${property("mod.version")}+${sc.current.version}"
base.archivesName = "${property("mod.slug") as String}-neoforge"

neoForge {
    version = property("deps.neo_loader") as String

    mods {
        register(property("mod.id") as String) {
            sourceSet(sourceSets.main.get())
        }
    }

    runs {
        register("client") {
            ideName = "NC - ${sc.current.version}"
            gameDirectory = file("../../run/")
            client()

            taskBefore(tasks.named("prepareClientRun"))
        }

        register("server") {
            ideName = "NS - ${sc.current.version}"
            gameDirectory = file("../../run/")
            server()

            taskBefore(tasks.named("prepareServerRun"))
        }
    }
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25

    toolchain {
        vendor = JvmVendorSpec.ADOPTIUM
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks {
    processResources {
        fun MutableMap<String, String>.register(key: String, property: String) {
            val value: String = sc.properties[property]
            inputs.property(key, value)
            set(key, value)
        }

        val props = buildMap {
            register("id", "mod.id")
            register("name", "mod.name")
            register("version", "mod.version")
            register("minecraft", "mod.mc_compat")
            register("description", "mod.description")
            register("authors", "mod.authors")
            register("license", "mod.license")
            register("homepage", "mod.homepage")
            register("issues", "mod.issues")
            register("neoforge_loader", "deps.neoforge_loader")

            register("loot_entry_modifier_field", "loot.entry_modifier_field")
            register("loot_function_type_field", "loot.function_type_field")
        }

        filesMatching("META-INF/neoforge.mods.toml") { expand(props) }
        filesMatching("data/betterzombieleaders/loot_table/entities/leader_zombie_bonus.json") { expand(props) }
        filesMatching("*.mixins.json") { expand("java" to "JAVA_25") }
        exclude("fabric.mod.json")
    }

    named("createMinecraftArtifacts") {
        dependsOn("stonecutterGenerate")
    }

}
