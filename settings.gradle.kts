rootProject.name = "geometry-api-kotlin"

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    // Not FAIL_ON_PROJECT_REPOS: the Kotlin/Wasm plugin registers its own
    // project-level repository for downloading Node.js, which that mode blocks.
    repositories {
        google()
        mavenCentral()
    }
}
