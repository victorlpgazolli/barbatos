plugins {
    kotlin("multiplatform") version "2.1.21"
    kotlin("plugin.serialization") version "2.1.21"
}

repositories {
    mavenCentral()
}

kotlin {
    macosArm64 {
        binaries {
            executable {
                entryPoint = "main"
                baseName = "barbatos"
            }
        }
    }
    linuxArm64 {
        binaries {
            executable {
                entryPoint = "main"
                baseName = "barbatos"
                val libgccPath = System.getenv("LIBGCC_PATH")
                linkerOpts(
                    "-L/usr/lib/aarch64-linux-gnu",
                    "-L/usr/aarch64-linux-gnu/lib",
                    "--allow-shlib-undefined",
                    "-lssl", "-lcrypto",
                    "-lssh",
                    "-lbrotlidec",
                    "-lgssapi_krb5",
                    "-lidn2",
                    "-lldap", "-llber",
                    "-lnghttp2",
                    "-lpsl",
                    "-lrtmp",
                    "-lzstd",
                    "-lz",
                    if (libgccPath != null && libgccPath.isNotEmpty()) libgccPath else "-lgcc"
                )
            }
        }
        compilation["main"].compilerOptions.configure {
            freeCompilerArgs.add("-Xcompiler-option=-mno-outline-atomics")
        }
    }
    linuxX64 {
        binaries {
            executable {
                entryPoint = "main"
                baseName = "barbatos"
            }
        }
    }


    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        optIn.add("kotlinx.cinterop.ExperimentalForeignApi")
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
                implementation("io.ktor:ktor-client-core:3.0.0")
                implementation("io.ktor:ktor-client-content-negotiation:3.0.0")
                implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.0")
            }
        }
        val unixMain by creating {
            dependsOn(commonMain)
        }

        val linuxMain by creating {
            dependsOn(unixMain)
            dependencies {
                implementation("io.ktor:ktor-client-curl:3.0.0")
            }
        }

        val linuxArm64Main by getting {
            dependsOn(linuxMain)
        }

        val linuxX64Main by getting {
            dependsOn(linuxMain)
        }

        val macosArm64Main by getting {
            dependsOn(unixMain)
            dependencies {
                implementation("io.ktor:ktor-client-darwin:3.0.0")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("io.ktor:ktor-client-mock:3.0.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
            }
        }
    }
}
