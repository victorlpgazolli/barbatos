# Developer Logs

This file documents errors encountered during development and how they were resolved.

---

## 001 — Gradle wrapper requires settings file

**Error:** `gradle wrapper` failed with "Directory does not contain a Gradle build."

**Solution:** Create `settings.gradle.kts` first with `rootProject.name`, then run `gradle wrapper`.

---

## 002 — Kotlin Native target name "native" clashes with hierarchy template

**Error:** Warning about "Default Kotlin Hierarchy Template Misconfiguration Due to Illegal Target Names" when using `macosArm64("native")`.

**Solution:** Use `macosArm64` without a custom name alias. The Gradle tasks become `linkDebugExecutableMacosArm64` instead of `linkDebugExecutableNative`.

---

## 003 — Cannot resolve kotlin-native-prebuilt

**Error:** `Could not resolve external dependency org.jetbrains.kotlin:kotlin-native-prebuilt:2.1.21 because no repositories are defined.`

**Solution:** Add `repositories { mavenCentral() }` block to `build.gradle.kts`.

---

## 004 — termios c_cc array set operator not resolved

**Error:** `Unresolved reference. None of the following candidates is applicable because of a receiver type mismatch: fun StringBuilder.set(...)` when doing `raw.c_cc[VMIN] = 1u`.

**Solution:** Add `import kotlinx.cinterop.set` to bring the indexed set operator for CArrayPointer into scope.

---

## 005 — TIOCGWINSZ not found in platform.darwin

**Error:** `Unresolved reference 'TIOCGWINSZ'` when trying `platform.darwin.TIOCGWINSZ`.

**Solution:** Hardcode the ioctl constant `0x40087468u` for macOS. This is the value of TIOCGWINSZ on macOS ARM64.

---

## 006 — Runtime.getRuntime() not available in Kotlin Native

**Error:** `Unresolved reference 'Runtime'` — this is a JVM-only API.

**Solution:** Remove shutdown hooks. Handle cleanup explicitly before exiting the main loop. Use `platform.posix.gettimeofday` for time instead of JVM System.currentTimeMillis().

---

## 007 — Manual UTC epoch math gives wrong timezone

**Error:** Using `gettimeofday` and manually computing day/month/hour from epoch seconds always gives UTC time, not the user's local time.

**Solution:** Use POSIX `localtime()` with `time()` / `time_tVar`. This automatically respects the system timezone. Import `kotlinx.cinterop.pointed` to dereference the `tm` struct pointer.

---

## 008 — Kotlin Native Ktor Client Blocking TUI Main Loop

**Error:** Fetching JSON RPC APIs sequentially froze the TUI completely.

**Solution:** Setup `CoroutineScope(Dispatchers.Default)` and launch background network requests via Ktor Client Darwin. Use concurrent Kotlin Native strategies like `AtomicReference` inside the TUI `AppState` to coordinate and pass fetched data back to the main loop `Renderer` without blocking user inputs.

---

## 009 — Frida RPC Method Name Mapping in Python

**Error:** `frida.core.RPCException: unable to find method 'listclasses'` when calling `script.exports_sync.listClasses()`.

**Solution:** Frida's Python bindings try to auto-map snake_case Python method calls to camelCase JS functions, which led to lookup missmatches (it looked for `list_classes` -> `listClasses` but mapping might not be 1:1 depending on naming). By exporting the function purely in lowercase `listclasses` in JS and calling `script.exports_sync.listclasses()` in Python, we avoid the fragile auto-mapping behavior.
