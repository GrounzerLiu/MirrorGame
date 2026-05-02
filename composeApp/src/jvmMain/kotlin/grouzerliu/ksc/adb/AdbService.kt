package grouzerliu.mirrorgame.adb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class AdbService(private val adbPath: String = "adb") {

    private var serverStarted = false

    /** Start ADB server once. Call before first device list poll. */
    suspend fun ensureServer() = withContext(Dispatchers.IO) {
        if (serverStarted) return@withContext
        println("[adb] starting server...")
        runCatching {
            val p = ProcessBuilder(adbPath, "start-server").redirectErrorStream(true).start()
            p.waitFor(5, TimeUnit.SECONDS)
        }
        serverStarted = true
        delay(1000)
    }

    fun listDevices(intervalMs: Long = 2000): Flow<List<AdbDevice>> = flow {
        while (true) {
            val devices = fetchDevices()
            emit(devices)
            delay(intervalMs)
        }
    }

    private suspend fun fetchDevices(): List<AdbDevice> = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder(adbPath, "devices", "-l")
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                process.waitFor()
            }

            println("[adb] devices -l raw:\n$output")
            val devices = parseDevices(output)
            println("[adb] parsed: $devices")
            return@withContext devices
        } catch (e: Exception) {
            println("[adb] fetchDevices error: ${e.message}")
            emptyList()
        }
    }

    private fun parseDevices(output: String): List<AdbDevice> {
        val lines = output.lines().drop(1) // skip "List of devices attached"
        return lines.filter { it.isNotBlank() }
            .map { line ->
                val parts = line.split("\\s+".toRegex())
                val serial = parts.firstOrNull() ?: ""
                val status = parts.getOrNull(1) ?: ""
                val model = parts.find { it.startsWith("model:") }?.removePrefix("model:") ?: ""
                AdbDevice(serial, status, model)
            }
    }

    suspend fun pushFile(serial: String, local: String, remote: String): Boolean = withContext(Dispatchers.IO) {
        try {
            println("[adb] push $local -> $serial:$remote")
            val process = ProcessBuilder(adbPath, "-s", serial, "push", local, remote)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                process.waitFor()
            }
            val ok = process.exitValue() == 0
            println("[adb] push result: exit=$ok, output=$output")
            ok
        } catch (e: Exception) {
            println("[adb] push error: ${e.message}")
            false
        }
    }

    suspend fun forward(serial: String, localPort: Int, remotePort: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            println("[adb] forward tcp:$localPort tcp:$remotePort on $serial")
            val process = ProcessBuilder(
                adbPath, "-s", serial,
                "forward", "tcp:$localPort", "tcp:$remotePort"
            ).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                process.waitFor()
            }
            val ok = process.exitValue() == 0
            println("[adb] forward result: exit=$ok, output=$output")
            ok
        } catch (e: Exception) {
            println("[adb] forward error: ${e.message}")
            false
        }
    }

    suspend fun removeForward(serial: String, localPort: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            println("[adb] remove forward tcp:$localPort on $serial")
            val process = ProcessBuilder(
                adbPath, "-s", serial,
                "forward", "--remove", "tcp:$localPort"
            ).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                process.waitFor()
            }
            println("[adb] remove forward result: exit=${process.exitValue()}, output=$output")
            process.exitValue() == 0
        } catch (e: Exception) {
            println("[adb] remove forward error: ${e.message}")
            false
        }
    }

    suspend fun startServer(serial: String, serverPath: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            // Kill any previous server instance first
            println("[adb] kill previous scrcpy-server...")
            runCatching {
                val kill = ProcessBuilder(adbPath, "-s", serial, "shell", "killall", "-9", "com.genymobile.scrcpy.Server")
                    .redirectErrorStream(true).start()
                kill.waitFor(3, TimeUnit.SECONDS)
            }

            println("[adb] start scrcpy-server on $serial (tunnel_forward=false, scid=1)")
            val args = listOf(
                adbPath, "-s", serial, "shell",
                "CLASSPATH=$serverPath",
                "app_process", "/", "com.genymobile.scrcpy.Server",
                "3.3.4",
                "scid=1",
                "tunnel_forward=false",
                "send_device_meta=true",
                "send_dummy_byte=true",
                "send_codec_meta=true",
                "log_level=debug",
                "max_size=1920",
                "video_codec=h264"
            )
            println("[adb] server cmd: ${args.joinToString(" ")}")
            val process = ProcessBuilder(args).redirectErrorStream(true).start()

            // Read server output in background to prevent buffer blocking
            val outputReader = Thread {
                try {
                    process.inputStream.bufferedReader().use { reader ->
                        reader.lines().forEach { line ->
                            println("[adb:server] $line")
                        }
                    }
                } catch (_: Exception) { }
            }.also { it.name = "scrcpy-output"; it.start() }

            kotlinx.coroutines.delay(2000)
            val alive = process.isAlive
            println("[adb] scrcpy-server alive=$alive (port=$port, exitValue=${if (alive) "N/A" else process.exitValue()})")
            alive
        } catch (e: Exception) {
            println("[adb] startServer error: ${e.message}")
            false
        }
    }
}
