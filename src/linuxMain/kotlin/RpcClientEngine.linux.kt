import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.curl.Curl

actual fun getRpcClientEngine(): HttpClientEngineFactory<HttpClientEngineConfig> = Curl