plugins {
    kotlin("multiplatform") version "2.0.0"
    kotlin("plugin.serialization") version "2.0.0"
}

repositories {
    mavenCentral()
}

kotlin {
    val hostOs = System.getProperty("os.name")
    val isMac = hostOs.startsWith("Mac")
    val isLinux = hostOs.startsWith("Linux")

    val nativeTargets = listOf(
        macosArm64(),
        linuxX64(),
        linuxArm64()
    )

    nativeTargets.forEach { target ->
        target.compilations.getByName("main") {
            cinterops.create("frida") {
                defFile("src/nativeInterop/cinterop/frida.def")
                // Note: Actual header parsing requires frida-core-devkit to be present.
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
                implementation("io.ktor:ktor-network:2.3.11") // Required for JDWP
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
