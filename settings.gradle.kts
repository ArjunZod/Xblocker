pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // NewPipeExtractor (YouTube stream resolution) is published here.
        maven { setUrl("https://jitpack.io") }
    }
}

rootProject.name = "ShieldX"
include(":app")
include(":contracts")
include(":ui-kit")
include(":music")
include(":ai-agent")
project(":ai-agent").projectDir = file("agent")
