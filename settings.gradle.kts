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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "idonthaveyourtime"

include(":app")
include(":feature:summarize:api")
include(":feature:summarize:impl")
include(":core:model")
include(":core:domain")
include(":core:data")
include(":core:database")
include(":core:audio")
include(":core:whisper")
include(":core:llm")
include(":core:common")
include(":core:designsystem")
include(":core:testing")
