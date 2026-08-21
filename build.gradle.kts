@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    `maven-publish`
}

group = "de.jonaswolf.geometry"
// CI (.github/workflows/publish.yml) sets LIBRARY_VERSION from the pushed
// tag (vX.Y.Z -> X.Y.Z); local/composite builds fall back to 0.1.0.
version = System.getenv("LIBRARY_VERSION") ?: "0.1.0"

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/jwtue/geometry-api-kotlin")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

kotlin {
    jvm()
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
