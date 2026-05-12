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

    suspend fun resolveActivity(serial: String, packageName: String): String? = withContext(Dispatchers.IO) {
        try {
            val p = ProcessBuilder(adbPath, "-s", serial, "shell", "pm", "resolve-activity", "--brief", packageName)
                .redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor(5, TimeUnit.SECONDS)
            out.lines().firstOrNull { it.contains("/") && it.contains(".") }?.trim()
        } catch (e: Exception) { null }
    }

    suspend fun launchApp(serial: String, packageName: String, displayId: Int? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            val activity = resolveActivity(serial, packageName)
            val cmd = if (activity != null) {
                val base = mutableListOf(adbPath, "-s", serial, "shell", "am", "start", "-n", activity)
                if (displayId != null) { base.add("--display"); base.add(displayId.toString()) }
                base
            } else {
                mutableListOf(adbPath, "-s", serial, "shell", "monkey", "-p", packageName, "-c", "android.intent.category.LAUNCHER", "1")
            }
            val p = ProcessBuilder(cmd).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor(5, TimeUnit.SECONDS)
            println("[adb] launch $packageName: $out")
            true
        } catch (e: Exception) { println("[adb] launch error: ${e.message}"); false }
    }

    suspend fun getDisplayIds(serial: String): List<Int> = withContext(Dispatchers.IO) {
        try {
            val p = ProcessBuilder(adbPath, "-s", serial, "shell", "dumpsys", "display")
                .redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor(5, TimeUnit.SECONDS)
            Regex("""displayId=(\d+)""").findAll(out).map { it.groupValues[1].toInt() }.toList().distinct()
        } catch (e: Exception) { listOf(0) }
    }

    suspend fun sendTouch(serial: String, x: Int, y: Int) = withContext(Dispatchers.IO) {
        try {
            val p = ProcessBuilder(adbPath, "-s", serial, "shell", "input", "tap", x.toString(), y.toString())
                .redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            val ok = p.waitFor(2, TimeUnit.SECONDS) && p.exitValue() == 0
            if (!ok) println("[adb] sendTouch error: exit=${p.exitValue()} out=$out")
        } catch (e: Exception) {
            println("[adb] sendTouch exception: ${e.message}")
        }
    }

    suspend fun sendBack(serial: String) = withContext(Dispatchers.IO) {
        try {
            val p = ProcessBuilder(adbPath, "-s", serial, "shell", "input", "keyevent", "KEYCODE_BACK")
                .redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            val ok = p.waitFor(2, TimeUnit.SECONDS) && p.exitValue() == 0
            if (!ok) println("[adb] sendBack error: exit=${p.exitValue()} out=$out")
        } catch (e: Exception) {
            println("[adb] sendBack exception: ${e.message}")
        }
    }

    suspend fun listPackages(serial: String): List<InstalledApp> = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder(adbPath, "-s", serial, "shell", "pm", "list", "packages", "-3")
                .redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor(5, TimeUnit.SECONDS)
            output.lines().filter { it.startsWith("package:") }.map {
                InstalledApp(packageName = it.removePrefix("package:").trim())
            }
        } catch (e: Exception) {
            println("[adb] listPackages error: ${e.message}")
            emptyList()
        }
    }
}
