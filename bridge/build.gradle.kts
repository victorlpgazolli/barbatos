plugins {
    kotlin("multiplatform") apply false
    kotlin("plugin.serialization") apply false
}

repositories {
    mavenCentral()
}

kotlin {
    val nativeTargets = listOf(
        macosArm64(),
        linuxX64(),
        linuxArm64()
    )

    nativeTargets.forEach { target ->
        target.compilations.getByName("main") {
            cinterops.create("frida") {
                defFile("src/nativeInterop/cinterop/frida.def")
            }
        }
        target.binaries {
            executable {
                entryPoint = "main"
            }
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
                implementation("io.ktor:ktor-network:2.3.11")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
