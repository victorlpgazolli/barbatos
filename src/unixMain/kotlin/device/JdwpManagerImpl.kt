package device

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import model.device.AdbManager
import model.device.JdwpManager
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fputs
import utils.BinaryManager

class JdwpManagerImpl(private val adbManager: AdbManager) : JdwpManager {
    override fun load(
        target: String,
        port: Int,
        libraryPath: String,
        breakOn: String?,
        packageName: String,
        serial: String
    ): Result<Unit> {
        val finalBreakOn = breakOn ?: "android.os.Handler.dispatchMessage"
        val lastDot = finalBreakOn.lastIndexOf('.')
        if (lastDot == -1) return Result.failure(Exception("Invalid breakOn format"))
        
        val className = "L" + finalBreakOn.substring(0, lastDot).replace('.', '/') + ";"
        val methodName = finalBreakOn.substring(lastDot + 1)
        
        // Check if gadget is already running
        try {
            val ssOutput = adbManager.executeShellCommand(serial, "ss -ltn 2>/dev/null")
            if (ssOutput.contains("127.0.0.1:27042")) {
                return Result.success(Unit)
            }
        } catch (e: Exception) {
            // Ignore failure in ss command
        }
        
        return runBlocking {
            val selectorManager = SelectorManager(Dispatchers.Default)
            val socket = aSocket(selectorManager).tcp().connect(target, port)
            try {
                val client = JdwpClient(socket)
                client.start()
                
                val runtimeClass = client.getClassByName("Ljava/lang/Runtime;") 
                    ?: throw Exception("Cannot find class Runtime")
                val getRuntimeMeth = client.getMethods(runtimeClass.refTypeId).find { it.name == "getRuntime" }
                    ?: throw Exception("Cannot find method Runtime.getRuntime()")
                    
                val c = client.getClassByName(className) 
                    ?: throw Exception("Could not access class '$className'")
                val m = client.getMethods(c.refTypeId).find { it.name == methodName }
                    ?: throw Exception("Could not access method '$methodName'")
                
                // Breakpoint Event: Type 2 (EVENT_BREAKPOINT)
                // location: TYPE_CLASS (1), classId, methodId, loc (8 bytes of 0)
                val loc = ByteArray(1 + client.referenceTypeIDSize + client.methodIDSize + 8)
                loc[0] = 1 // TYPE_CLASS
                client.format(client.referenceTypeIDSize, c.refTypeId).copyInto(loc, 1)
                client.format(client.methodIDSize, m.methodId).copyInto(loc, 1 + client.referenceTypeIDSize)
                
                val rId = client.sendEvent(2.toByte(), 7.toByte(), loc) // 7 = MODKIND_LOCATIONONLY
                
                client.resumeVm()
                
                var eventRet: JdwpClient.EventBreakpoint? = null
                for (i in 0..60) {
                    val buf = client.waitForEvent()
                    if (buf.isNotEmpty()) {
                        eventRet = client.parseEventBreakpoint(buf, rId)
                        if (eventRet != null) break
                    }
                    delay(500)
                }
                if (eventRet == null) throw Exception("Timeout waiting for event")
                
                delay(1000)
                client.clearEvent(2.toByte(), rId)
                
                // Push Gadget and Config using ADB to /data/local/tmp/
                adbManager.pushFile(serial, libraryPath, "/data/local/tmp/frida-gadget.so")
                
                val configJson = "{\"interaction\":{\"type\":\"listen\",\"address\":\"127.0.0.1\",\"port\":27042,\"on_port_conflict\":\"replace\",\"on_load\":\"resume\"}}"
                val localConfigPath = BinaryManager.getLocalPath("frida-gadget", "config", "json")
                
                // Write local config file
                val file = fopen(localConfigPath, "w")
                if (file != null) {
                    fputs(configJson, file)
                    fclose(file)
                }
                
                adbManager.pushFile(serial, localConfigPath, "/data/local/tmp/frida-gadget.config")
                
                // Copy files inside the process to bypass permission issues on non-rooted devices
                val cpGadget = "cp /data/local/tmp/frida-gadget.so /data/data/$packageName/frida-gadget.so"
                val cpConfig = "cp /data/local/tmp/frida-gadget.config /data/data/$packageName/frida-gadget.config"
                
                client.runtimeExecPayload(eventRet.tId, runtimeClass.refTypeId, getRuntimeMeth.methodId, cpGadget)
                delay(500)
                client.runtimeExecPayload(eventRet.tId, runtimeClass.refTypeId, getRuntimeMeth.methodId, cpConfig)
                delay(500)
                
                val dstLocation = "/data/data/$packageName/frida-gadget.so"
                
                client.runtimeLoadPayload(eventRet.tId, runtimeClass.refTypeId, getRuntimeMeth.methodId, dstLocation)
                
                client.resumeVm()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                socket.close()
                selectorManager.close()
            }
        }
    }
}

class JdwpClient(private val socket: Socket) {
    private val readChannel = socket.openReadChannel()
    private val writeChannel = socket.openWriteChannel(autoFlush = true)
    
    var fieldIDSize = 4
    var methodIDSize = 4
    var objectIDSize = 8
    var referenceTypeIDSize = 8
    var frameIDSize = 8
    
    var classes = listOf<ClassInfo>()
    val methods = mutableMapOf<Long, List<MethodInfo>>()
    
    private var idCounter = 1
    
    data class ClassInfo(val refTypeTag: Byte, val refTypeId: Long, val signature: String, val status: Int)
    data class MethodInfo(val methodId: Long, val name: String, val signature: String, val modBits: Int)
    data class EventBreakpoint(val rId: Int, val tId: Long, val loc: Long)
    
    suspend fun start() {
        handshake()
        idsizes()
        allclasses()
    }
    
    suspend fun handshake() {
        val handshakeStr = "JDWP-Handshake".encodeToByteArray()
        writeChannel.writeFully(handshakeStr)
        val buf = ByteArray(14)
        readChannel.readFully(buf, 0, 14)
        if (!buf.contentEquals(handshakeStr)) throw Exception("Failed to handshake")
    }
    
    suspend fun createPacket(cmdSet: Byte, cmd: Byte, data: ByteArray = ByteArray(0)): ByteArray {
        val pktLen = data.size + 11
        val buf = ByteArray(pktLen)
        writeInt(buf, 0, pktLen)
        writeInt(buf, 4, idCounter)
        idCounter += 2
        buf[8] = 0 // flags
        buf[9] = cmdSet
        buf[10] = cmd
        data.copyInto(buf, 11)
        return buf
    }
    
    suspend fun readReply(): ByteArray {
        val header = ByteArray(11)
        readChannel.readFully(header, 0, 11)
        val pktLen = readInt(header, 0)
        val flags = header[8]
        val errCode = (header[9].toInt() and 0xFF shl 8) or (header[10].toInt() and 0xFF)
        if (flags == 0x80.toByte() && errCode != 0) throw Exception("Received errcode $errCode")
        
        val dataLen = pktLen - 11
        val buf = ByteArray(dataLen)
        if (dataLen > 0) readChannel.readFully(buf, 0, dataLen)
        return buf
    }
    
    suspend fun idsizes() {
        writeChannel.writeFully(createPacket(1, 7))
        val buf = readReply()
        fieldIDSize = readInt(buf, 0)
        methodIDSize = readInt(buf, 4)
        objectIDSize = readInt(buf, 8)
        referenceTypeIDSize = readInt(buf, 12)
        frameIDSize = readInt(buf, 16)
    }
    
    suspend fun allclasses() {
        writeChannel.writeFully(createPacket(1, 3))
        val buf = readReply()
        val count = readInt(buf, 0)
        var offset = 4
        val list = mutableListOf<ClassInfo>()
        for (i in 0 until count) {
            val refTypeTag = buf[offset++]
            val refTypeId = unformat(referenceTypeIDSize, buf, offset)
            offset += referenceTypeIDSize
            val sigLen = readInt(buf, offset)
            offset += 4
            val signature = buf.decodeToString(offset, offset + sigLen)
            offset += sigLen
            val status = readInt(buf, offset)
            offset += 4
            list.add(ClassInfo(refTypeTag, refTypeId, signature, status))
        }
        classes = list
    }
    
    fun getClassByName(name: String): ClassInfo? = classes.find { it.signature.equals(name, ignoreCase = true) }
    
    suspend fun getMethods(refTypeId: Long): List<MethodInfo> {
        if (methods.containsKey(refTypeId)) return methods[refTypeId]!!
        
        val data = format(referenceTypeIDSize, refTypeId)
        writeChannel.writeFully(createPacket(2, 5, data))
        val buf = readReply()
        val count = readInt(buf, 0)
        var offset = 4
        val list = mutableListOf<MethodInfo>()
        for (i in 0 until count) {
            val methodId = unformat(methodIDSize, buf, offset)
            offset += methodIDSize
            val nameLen = readInt(buf, offset)
            offset += 4
            val name = buf.decodeToString(offset, offset + nameLen)
            offset += nameLen
            val sigLen = readInt(buf, offset)
            offset += 4
            val signature = buf.decodeToString(offset, offset + sigLen)
            offset += sigLen
            val modBits = readInt(buf, offset)
            offset += 4
            list.add(MethodInfo(methodId, name, signature, modBits))
        }
        methods[refTypeId] = list
        return list
    }
    
    suspend fun sendEvent(eventCode: Byte, kind: Byte, option: ByteArray): Int {
        val data = ByteArray(1 + 1 + 4 + 1 + option.size)
        data[0] = eventCode
        data[1] = 2 // SUSPEND_ALL
        writeInt(data, 2, 1)
        data[6] = kind
        option.copyInto(data, 7)
        
        writeChannel.writeFully(createPacket(15, 1, data))
        val buf = readReply()
        return readInt(buf, 0)
    }
    
    suspend fun waitForEvent(): ByteArray {
        val header = ByteArray(11)
        val readCount = withTimeoutOrNull(2000) {
            readChannel.readFully(header, 0, 11)
            true
        } ?: return ByteArray(0)
        
        val pktLen = readInt(header, 0)
        val dataLen = pktLen - 11
        val buf = ByteArray(dataLen)
        if (dataLen > 0) readChannel.readFully(buf, 0, dataLen)
        return buf
    }
    
    fun parseEventBreakpoint(buf: ByteArray, eventId: Int): EventBreakpoint? {
        if (buf.size < 10 + objectIDSize) return null
        val rId = readInt(buf, 6)
        if (rId != eventId) return null
        val tId = unformat(objectIDSize, buf, 10)
        return EventBreakpoint(rId, tId, -1L)
    }
    
    suspend fun clearEvent(eventCode: Byte, rId: Int) {
        val data = ByteArray(5)
        data[0] = eventCode
        writeInt(data, 1, rId)
        writeChannel.writeFully(createPacket(15, 2, data))
        readReply()
    }
    
    suspend fun resumeVm() {
        writeChannel.writeFully(createPacket(1, 9))
        readReply()
    }
    
    suspend fun createString(dataStr: String): Long {
        val strBytes = dataStr.encodeToByteArray()
        val data = ByteArray(4 + strBytes.size)
        writeInt(data, 0, strBytes.size)
        strBytes.copyInto(data, 4)
        writeChannel.writeFully(createPacket(1, 11, data))
        val buf = readReply()
        return unformat(objectIDSize, buf, 0)
    }
    
    suspend fun invokeStatic(classId: Long, threadId: Long, methId: Long, vararg args: ByteArray): ByteArray {
        var data = format(referenceTypeIDSize, classId) + format(objectIDSize, threadId) + format(methodIDSize, methId)
        var argsLenBuf = ByteArray(4)
        writeInt(argsLenBuf, 0, args.size)
        data += argsLenBuf
        for (arg in args) data += arg
        var optsBuf = ByteArray(4)
        writeInt(optsBuf, 0, 0)
        data += optsBuf
        
        writeChannel.writeFully(createPacket(3, 3, data))
        return readReply()
    }
    
    suspend fun invokeVoid(objId: Long, threadId: Long, classId: Long, methId: Long, vararg args: ByteArray) {
        var data = format(objectIDSize, objId) + format(objectIDSize, threadId) + format(referenceTypeIDSize, classId) + format(methodIDSize, methId)
        var argsLenBuf = ByteArray(4)
        writeInt(argsLenBuf, 0, args.size)
        data += argsLenBuf
        for (arg in args) data += arg
        var optsBuf = ByteArray(4)
        writeInt(optsBuf, 0, 0)
        data += optsBuf
        
        writeChannel.writeFully(createPacket(9, 6, data))
        readReply()
    }
    
    suspend fun invoke(objId: Long, threadId: Long, classId: Long, methId: Long, vararg args: ByteArray): ByteArray {
        var data = format(objectIDSize, objId) + format(objectIDSize, threadId) + format(referenceTypeIDSize, classId) + format(methodIDSize, methId)
        var argsLenBuf = ByteArray(4)
        writeInt(argsLenBuf, 0, args.size)
        data += argsLenBuf
        for (arg in args) data += arg
        var optsBuf = ByteArray(4)
        writeInt(optsBuf, 0, 0)
        data += optsBuf
        
        writeChannel.writeFully(createPacket(9, 6, data))
        return readReply()
    }
    
    suspend fun runtimeExecPayload(threadId: Long, runtimeClassId: Long, getRuntimeMethId: Long, command: String): Boolean {
        val cmdObjId = createString(command)
        val buf = invokeStatic(runtimeClassId, threadId, getRuntimeMethId)
        if (buf.isEmpty() || buf[0] != 76.toByte()) return false
        val rt = unformat(objectIDSize, buf, 1)
        
        val execMeth = getMethods(runtimeClassId).find { it.name == "exec" && it.signature.contains("String") } ?: return false
        
        val dataArg = ByteArray(1 + objectIDSize)
        dataArg[0] = 76 // TAG_OBJECT
        format(objectIDSize, cmdObjId).copyInto(dataArg, 1)
        
        invoke(rt, threadId, runtimeClassId, execMeth.methodId, dataArg)
        return true
    }

    suspend fun runtimeLoadPayload(threadId: Long, runtimeClassId: Long, getRuntimeMethId: Long, library: String): Boolean {
        val cmdObjId = createString(library)
        val buf = invokeStatic(runtimeClassId, threadId, getRuntimeMethId)
        if (buf.isEmpty() || buf[0] != 76.toByte()) return false // TAG_OBJECT = 76
        val rt = unformat(objectIDSize, buf, 1)
        
        val loadMeth = getMethods(runtimeClassId).find { it.name == "load" } ?: return false
        
        val dataArg = ByteArray(1 + objectIDSize)
        dataArg[0] = 76 // TAG_OBJECT
        format(objectIDSize, cmdObjId).copyInto(dataArg, 1)
        
        invokeVoid(rt, threadId, runtimeClassId, loadMeth.methodId, dataArg)
        return true
    }
    
    fun format(size: Int, value: Long): ByteArray {
        val b = ByteArray(size)
        for (i in size - 1 downTo 0) {
            b[i] = (value ushr ((size - 1 - i) * 8)).toByte()
        }
        return b
    }
    
    fun unformat(size: Int, buf: ByteArray, offset: Int): Long {
        var v = 0L
        for (i in 0 until size) {
            v = (v shl 8) or (buf[offset + i].toLong() and 0xFF)
        }
        return v
    }
    
    fun writeInt(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value ushr 24).toByte()
        buf[offset + 1] = (value ushr 16).toByte()
        buf[offset + 2] = (value ushr 8).toByte()
        buf[offset + 3] = value.toByte()
    }
    
    fun readInt(buf: ByteArray, offset: Int): Int {
        return ((buf[offset].toInt() and 0xFF) shl 24) or
               ((buf[offset + 1].toInt() and 0xFF) shl 16) or
               ((buf[offset + 2].toInt() and 0xFF) shl 8) or
               (buf[offset + 3].toInt() and 0xFF)
    }
}