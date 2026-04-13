import platform.posix.F_OK
import platform.posix.access

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
fun getBinaryPath(architectureName: String): String {
    check(architectureName in listOf("linuxX64", "linuxArm64", "macosArm64")) {
        "Unsupported architecture: $architectureName"
    }

    val debugBinary = "./build/bin/${architectureName}/debugExecutable/barbatos.kexe"
    if (access(debugBinary, F_OK) == 0) {
        return debugBinary
    }

    val releaseBinary = "./build/bin/${architectureName}/releaseExecutable/barbatos.kexe"
    if (access(releaseBinary, F_OK) == 0) {
        return releaseBinary
    }

    return "barbatos"
}