import java.io.File

plugins {
    kotlin("multiplatform") version "2.1.21"
    kotlin("plugin.serialization") version "2.1.21"
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}


val downloadFridaDevkitTask = tasks.register<Exec>("downloadFridaDevkit") {
    group = "setup"
    description = "Download Frida Devkit for the current platform"
    commandLine("./scripts/download_frida_devkit.sh")
    
    // Only run if the devkit headers/libs are missing
    outputs.file("src/nativeInterop/cinterop/frida-core.h")
    outputs.file("src/nativeInterop/cinterop/libfrida-core.a")
}

val generateResourcesTask = tasks.register("generateResources") {
    val resourcesDir = file("src/commonMain/resources")
    val outputDir = layout.buildDirectory.dir("generated/resources/src/utils").get().asFile

    inputs.dir(resourcesDir)
    outputs.dir(outputDir)

    doLast {
        outputDir.mkdirs()
        val ktFile = File(outputDir, "EmbeddedScripts.kt")

        val scriptBuilder = StringBuilder()
        scriptBuilder.appendLine("package utils")
        scriptBuilder.appendLine()
        scriptBuilder.appendLine("object EmbeddedScripts {")

        if (resourcesDir.exists()) {
            resourcesDir.walkTopDown().filter { it.isFile && it.extension == "js" }.forEach { file ->
                val variableName = file.nameWithoutExtension.replace(".", "_")
                val content = file.readText().replace("$", "${"$"}{'$'}")

                scriptBuilder.appendLine("    val $variableName = \"\"\"")
                scriptBuilder.appendLine(content)
                scriptBuilder.appendLine("    \"\"\".trimIndent()")
                scriptBuilder.appendLine()
            }
        }

        scriptBuilder.appendLine("}")
        ktFile.writeText(scriptBuilder.toString())
    }
}
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    dependsOn(generateResourcesTask)
}

tasks.matching { it.name == "prepareKotlinIdeaImport" }.configureEach {
    dependsOn(generateResourcesTask)
    dependsOn(downloadFridaDevkitTask)
}

tasks.matching { it.name == "build" || it.name == "assemble" }.configureEach {
    dependsOn(generateResourcesTask)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.CInteropProcess>().configureEach {
    dependsOn(downloadFridaDevkitTask)
}
kotlin {
    macosArm64 {
        binaries {
            executable {
                entryPoint = "main"
                baseName = "barbatos"
                linkerOpts("-framework", "IOKit", "-framework", "AppKit")
            }
        }
        compilations.getByName("main") {
            val frida by cinterops.creating
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
        compilations.getByName("main") {
            val frida by cinterops.creating
        }
    }
    linuxX64 {
        binaries {
            executable {
                entryPoint = "main"
                baseName = "barbatos"
            }
        }
        compilations.getByName("main") {
            val frida by cinterops.creating
        }
    }


    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        optIn.add("kotlinx.cinterop.ExperimentalForeignApi")
    }

    sourceSets {
        val commonMain by getting {
            kotlin.srcDir(generateResourcesTask.map { it.outputs.files.singleFile })
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
                implementation("io.ktor:ktor-client-core:3.0.0")
                implementation("io.ktor:ktor-client-content-negotiation:3.0.0")
                implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.0")
                implementation("io.ktor:ktor-server-core:3.0.0")
                implementation("io.ktor:ktor-server-cio:3.0.0")
                implementation("io.ktor:ktor-server-content-negotiation:3.0.0")
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
                implementation("io.ktor:ktor-server-test-host:3.0.0")
            }
        }
    }
}

fun registerRpcTask(taskName: String, methodName: String, params: Map<String, Any?> = emptyMap()) {
    tasks.register<Exec>(taskName) {
        group = "barbatos-rpc"
        description = "Execute JSON-RPC method $methodName via curl"
        
        // Use standard Exec configuration instead of setting commandLine inside doFirst
        executable = "curl"
        
        argumentProviders.add(CommandLineArgumentProvider {
            val resolvedParams = params.mapValues { (key, default) ->
                val prop = project.findProperty(key)
                if (prop != null) {
                    when (default) {
                        is Int -> prop.toString().toIntOrNull() ?: default
                        is Boolean -> prop.toString().toBoolean()
                        else -> prop.toString()
                    }
                } else {
                    default
                }
            }

            val paramsJson = if (resolvedParams.isEmpty()) {
                "{}"
            } else {
                resolvedParams.entries.joinToString(",") { (k, v) ->
                    val valueJson = when (v) {
                        is String -> "\"${v.replace("\"", "\\\"")}\""
                        else -> v.toString()
                    }
                    "\"$k\":$valueJson"
                }
                .let { "{$it}" }
            }

            listOf(
                "-s", "--connect-timeout", "5", "--max-time", "15",
                "-X", "POST", "http://localhost:8080/rpc",
                "-H", "Content-Type: application/json",
                "-d", """{"jsonrpc":"2.0","method":"$methodName","params":$paramsJson,"id":1}"""
            )
        })

        // Print a nice message before execution
        doFirst {
            println("> Executing RPC Method: $methodName")
        }
    }
}

// Exploration Tools
registerRpcTask("rpcListClasses", "listClasses", mapOf("search_param" to "", "app_package" to "", "offset" to 0, "limit" to 200))
registerRpcTask("rpcCountInstances", "countInstances", mapOf("className" to ""))
registerRpcTask("rpcInspectClass", "inspectClass", mapOf("className" to ""))
registerRpcTask("rpcListInstances", "listInstances", mapOf("className" to ""))
registerRpcTask("rpcInspectInstance", "inspectInstance", mapOf("className" to "", "id" to "", "offset" to 0, "limit" to 50))
registerRpcTask("rpcGetInstanceAddresses", "getInstanceAddresses", mapOf("className" to ""))

// Modification Tools
registerRpcTask("rpcSetFieldValue", "setFieldValue", mapOf("className" to "", "id" to "", "fieldName" to "", "type" to "", "newValue" to ""))
registerRpcTask("rpcSetMethodImplementation", "setMethodImplementation", mapOf("className" to "", "methodSig" to "", "code" to ""))
registerRpcTask("rpcRunOnce", "runOnce", mapOf("className" to "", "methodSig" to "", "code" to ""))

// Monitoring Tools
registerRpcTask("rpcHookMethod", "hookMethod", mapOf("className" to "", "methodSig" to ""))
registerRpcTask("rpcGetHookEvents", "getHookEvents")

// Environment Tools
registerRpcTask("rpcPrepareEnvironment", "prepareEnvironment", mapOf("target" to "Gadget"))
registerRpcTask("rpcInjectGadgetFromScratch", "injectGadgetFromScratch", mapOf("with_logs" to true, "limit" to 100))
registerRpcTask("rpcInjectJdwp", "injectJdwp", mapOf("target" to "127.0.0.1", "port" to 5005, "package_name" to ""))
registerRpcTask("rpcHealthCheck", "healthCheck")

// iOS Specific Tools
registerRpcTask("rpcPatchAndInstallIosApp", "patchAndInstallIosApp", mapOf("appPath" to ""))
registerRpcTask("rpcCheckIosJailbreakStatus", "checkIosJailbreakStatus", mapOf("serial" to ""))
registerRpcTask("rpcInjectJailbrokenIos", "injectJailbrokenIos", mapOf("serial" to ""))
registerRpcTask("rpcCheckIosDeployStatus", "checkIosDeployStatus")

tasks.register<Exec>("runDebug") {
    group = "application"
    description = "Compile and run the bridge in debug mode"
    
    val isMac = org.gradle.internal.os.OperatingSystem.current().isMacOsX
    val linkTask = if (isMac) "linkDebugExecutableMacosArm64" else "linkDebugExecutableLinuxX64"
    val binaryPath = if (isMac) {
        "build/bin/macosArm64/debugExecutable/barbatos.kexe"
    } else {
        "build/bin/linuxX64/debugExecutable/barbatos.kexe"
    }

    dependsOn(linkTask)
    commandLine(file(binaryPath).absolutePath)
}

tasks.register<Exec>("runMock") {
    group = "application"
    description = "Compile and run the bridge with MockFridaBridge"
    
    val isMac = org.gradle.internal.os.OperatingSystem.current().isMacOsX
    val linkTask = if (isMac) "linkDebugExecutableMacosArm64" else "linkDebugExecutableLinuxX64"
    val binaryPath = if (isMac) {
        "build/bin/macosArm64/debugExecutable/barbatos.kexe"
    } else {
        "build/bin/linuxX64/debugExecutable/barbatos.kexe"
    }

    dependsOn(linkTask)
    commandLine(file(binaryPath).absolutePath, "--mock")
}
