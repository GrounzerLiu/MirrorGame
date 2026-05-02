package grouzerliu.mirrorgame.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import grouzerliu.mirrorgame.adb.AdbDevice
import grouzerliu.mirrorgame.adb.AdbService
import grouzerliu.mirrorgame.adb.FrameData
import grouzerliu.mirrorgame.adb.MirrorSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo

data class ScreenUiState(
    val devices: List<AdbDevice> = emptyList(),
    val selectedDevice: AdbDevice? = null,
    val isStreaming: Boolean = false,
    val currentFrame: ImageBitmap? = null,
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val error: String? = null,
)

class ScreenViewModel(
    private val adbService: AdbService = AdbService(),
) {
    var state by mutableStateOf(ScreenUiState())
        private set

    private var devicePollJob: Job? = null
    private var mirrorSession: MirrorSession? = null
    private var frameJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    fun startPolling() {
        devicePollJob?.cancel()
        devicePollJob = scope.launch {
            // Ensure ADB server is running once before polling
            adbService.ensureServer()

            adbService.listDevices().collect { devices ->
                val onlineDevices = devices.filter { it.isOnline }
                // Don't clear device list on transient empty polls
                if (onlineDevices.isEmpty() && state.devices.isNotEmpty()) return@collect
                state = state.copy(
                    devices = onlineDevices,
                    selectedDevice = state.selectedDevice?.let { sel ->
                        onlineDevices.find { it.serial == sel.serial } ?: onlineDevices.firstOrNull()
                    } ?: onlineDevices.firstOrNull(),
                )
            }
        }
    }

    fun selectDevice(device: AdbDevice) {
        if (!state.isStreaming) {
            state = state.copy(selectedDevice = device)
        }
    }

    fun toggleMirror() {
        if (state.isStreaming) {
            scope.launch { stopMirror() }
        } else {
            startMirror()
        }
    }

    private fun startMirror() {
        val device = state.selectedDevice ?: return
        state = state.copy(isStreaming = true, error = null)

        scope.launch {
            val session = MirrorSession(device.serial)
            mirrorSession = session

            // Collect frames
            frameJob = scope.launch {
                session.frames.collect { frameData ->
                    if (frameData.width <= 0 && frameData.height <= 0) {
                        state = state.copy(error = "投屏连接中断")
                        stopMirror()
                        return@collect
                    }
                    val bitmap = frameData.toImageBitmap()
                    state = state.copy(
                        currentFrame = bitmap,
                        frameWidth = frameData.width,
                        frameHeight = frameData.height,
                    )
                }
            }

            val result = session.start()
            if (result.isFailure) {
                state = state.copy(
                    isStreaming = false,
                    error = result.exceptionOrNull()?.message ?: "启动投屏失败"
                )
                frameJob?.cancel()
            }
        }
    }

    private suspend fun stopMirror() {
        mirrorSession?.stop()
        mirrorSession = null
        frameJob?.cancel()
        frameJob = null
        state = state.copy(
            isStreaming = false,
            currentFrame = null,
            frameWidth = 0,
            frameHeight = 0,
        )
    }

    fun dispose() {
        kotlinx.coroutines.runBlocking {
            stopMirror()
        }
        devicePollJob?.cancel()
        scope.cancel()
    }

    private fun FrameData.toImageBitmap(): ImageBitmap {
        val imageInfo = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.PREMUL)
        val bitmap = Bitmap()
        bitmap.installPixels(imageInfo, pixels, width * 4)
        return bitmap.asComposeImageBitmap()
    }
}
