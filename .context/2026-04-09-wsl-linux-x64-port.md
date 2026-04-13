# WSL / Linux X64 Port Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Support `linuxX64` architecture for WSL users and refactor source sets to avoid code duplication across Unix and Linux platforms.

**Architecture:** Reorganize Kotlin Multiplatform source sets into a hierarchy: `commonMain` -> `unixMain` -> `linuxMain`. Common POSIX logic moves to `unixMain`, while Linux-specific networking and terminal constants move to `linuxMain`.

**Tech Stack:** Kotlin Multiplatform, Gradle.

---

### Task 1: Reorganize Gradle Source Sets

**Files:**
- Modify: `build.gradle.kts`

- [ ] **Step 1: Add linuxX64 target and update source set hierarchy**

```kotlin
// build.gradle.kts
kotlin {
    macosArm64 { /* ... */ }
    linuxArm64 { /* ... */ }
    linuxX64 {
        binaries {
            executable {
                entryPoint = "main"
                baseName = "barbatos"
            }
        }
    }

    sourceSets {
        val commonMain by getting { /* ... */ }
        
        // New shared Unix source set
        val unixMain by creating {
            dependsOn(commonMain)
        }

        // New shared Linux source set
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
        // ...
    }
}
```

- [ ] **Step 2: Verify gradle sync succeeds**

Run: `./gradlew help`
Expected: SUCCESS

- [ ] **Step 3: Commit (Add only)**

Run: `git add build.gradle.kts`

---

### Task 2: Move Common Unix Logic to `unixMain`

**Files:**
- Move: `src/unixArm64Main/kotlin/Main.kt` -> `src/unixMain/kotlin/Main.kt`
- Move: `src/unixArm64Main/kotlin/Renderer.kt` -> `src/unixMain/kotlin/Renderer.kt`
- Move: `src/unixArm64Main/kotlin/ListRenderer.unixArm64.kt` -> `src/unixMain/kotlin/ListRenderer.unix.kt`
- Move: `src/linuxArm64Main/kotlin/TimeUtils.linuxArm64.kt` -> `src/unixMain/kotlin/TimeUtils.unix.kt`
- Delete: `src/macosArm64Main/kotlin/TimeUtils.macosArm64.kt`

- [ ] **Step 1: Move and rename files**

```bash
mkdir -p src/unixMain/kotlin
mv src/unixArm64Main/kotlin/Main.kt src/unixMain/kotlin/Main.kt
mv src/unixArm64Main/kotlin/Renderer.kt src/unixMain/kotlin/Renderer.kt
mv src/unixArm64Main/kotlin/ListRenderer.unixArm64.kt src/unixMain/kotlin/ListRenderer.unix.kt
mv src/linuxArm64Main/kotlin/TimeUtils.linuxArm64.kt src/unixMain/kotlin/TimeUtils.unix.kt
rm src/macosArm64Main/kotlin/TimeUtils.macosArm64.kt
rmdir src/unixArm64Main/kotlin 2>/dev/null || true
```

- [ ] **Step 2: Update actual declarations in ListRenderer.unix.kt**

Ensure it uses `actual` correctly in the new location.

- [ ] **Step 3: Verify build for Mac**

Run: `./gradlew linkDebugExecutableMacosArm64`
Expected: SUCCESS

---

### Task 3: Move Common Linux Logic to `linuxMain`

**Files:**
- Create: `src/linuxMain/kotlin/Terminal.linux.kt`
- Create: `src/linuxMain/kotlin/RpcClientEngine.linux.kt`
- Modify: `src/linuxArm64Main/kotlin/Terminal.linuxArm64.kt` (remove duplicated logic)

- [ ] **Step 1: Create linuxMain files**

```kotlin
// src/linuxMain/kotlin/Terminal.linux.kt
import platform.posix.termios
import kotlinx.cinterop.set
import platform.posix.ECHO
import platform.posix.ICANON
import platform.posix.ICRNL
import platform.posix.IEXTEN
import platform.posix.ISIG
import platform.posix.IXON
import platform.posix.VMIN

actual val TIOCGWINSZ: ULong = 0x5413u

actual fun termios.enableRawModePlatformFlags() {
    c_iflag = c_iflag and (ICRNL or IXON).toUInt().inv()
    c_lflag = c_lflag and (ECHO or ICANON or ISIG or IEXTEN).toUInt().inv()
    c_cc[VMIN] = 0u
}
```

```kotlin
// src/linuxMain/kotlin/RpcClientEngine.linux.kt
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.curl.Curl

actual fun getRpcClientEngine(): HttpClientEngineFactory<HttpClientEngineConfig> = Curl
```

- [ ] **Step 2: Clean up linuxArm64Main**

Remove `RpcClientEngine.linuxArm64.kt` and `Terminal.linuxArm64.kt` as they are now in `linuxMain`.

```bash
rm src/linuxArm64Main/kotlin/RpcClientEngine.linuxArm64.kt
rm src/linuxArm64Main/kotlin/Terminal.linuxArm64.kt
```

- [ ] **Step 3: Verify build for Linux ARM64**

Run: `./gradlew linkDebugExecutableLinuxArm64`
Expected: SUCCESS

---

### Task 4: Implement Linux X64 Target

**Files:**
- Create: `src/linuxX64Main/kotlin/TmuxManager.linuxX64.kt`

- [ ] **Step 1: Define binary path for X64**

```kotlin
// src/linuxX64Main/kotlin/TmuxManager.linuxX64.kt
actual val barbatosBinaryPath: String = "./build/bin/linuxX64/debugExecutable/barbatos.kexe"
```

- [ ] **Step 2: Verify build for Linux X64**

Run: `./gradlew linkDebugExecutableLinuxX64`
Expected: SUCCESS

- [ ] **Step 3: Commit all changes**

Run: `git add .`
