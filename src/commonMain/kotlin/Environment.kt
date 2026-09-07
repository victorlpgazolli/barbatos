import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.KotlinLoggingConfiguration
import io.ktor.server.application.Application
import io.ktor.server.engine.applicationEnvironment
import io.ktor.util.logging.LogLevel
import io.ktor.util.logging.Logger

private class QuietLogger : Logger {
    override val level: LogLevel = LogLevel.ERROR

    override fun error(message: String) {}
    override fun error(message: String, cause: Throwable) {}
    override fun warn(message: String) {}
    override fun warn(message: String, cause: Throwable) {}
    override fun info(message: String) {}
    override fun info(message: String, cause: Throwable) {}
    override fun debug(message: String) {}
    override fun debug(message: String, cause: Throwable) {}
    override fun trace(message: String) {}
    override fun trace(message: String, cause: Throwable) {}
}

private val log =
    run {
        KotlinLoggingConfiguration.logStartupMessage = false
        KotlinLogging.logger { }
    }
internal val environment = applicationEnvironment {
    log = QuietLogger()
}
