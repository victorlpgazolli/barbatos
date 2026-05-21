import server.startServer
import bridge.NativeFridaBridge

fun main() {
    println("Starting KMP Bridge with Native Frida support...")
    val bridge = NativeFridaBridge()
    startServer(bridge)
}
