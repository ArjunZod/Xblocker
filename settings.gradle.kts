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

rootProject.name = "TaRZI"
include(":app")
include(":contracts")
include(":ui-kit")
include(":music")
include(":ai-agent")

// The three TaRZI projects live in their own top-level directories; the two
// shared modules (:contracts, :ui-kit) keep conventional names.
project(":app").projectDir = file("TaRZI-App")
project(":music").projectDir = file("TaRZI-Music")
project(":ai-agent").projectDir = file("TaRZI-AI-Agent")
