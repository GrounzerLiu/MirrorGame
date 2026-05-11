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
import grouzerliu.mirrorgame.model.KeyMapping
import grouzerliu.mirrorgame.model.LaunchProject
import grouzerliu.mirrorgame.model.MappingType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo

data class ScreenUiState(
    val devices: List<AdbDevice> = emptyList(),
    val selectedDevice: AdbDevice? = null,
    val isStreaming: Boolean = false,
    val statusText: String = "",
    val currentFrame: ImageBitmap? = null,
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val containerWidth: Int = 0,
    val containerHeight: Int = 0,
    val error: String? = null,
    val isEditingMappings: Boolean = false,
    val showOverlays: Boolean = false,
    val editorMappings: List<KeyMapping> = emptyList(),
    val launchedProject: LaunchProject? = null,
    val touchIndicators: List<TouchPoint> = emptyList(),
    val showTouchIndicator: Boolean = false,
)

data class TouchPoint(val x: Int, val y: Int, val pointerId: Int)

class ScreenViewModel(
    private val adbService: AdbService = AdbService(),
) {
    var state by mutableStateOf(ScreenUiState())
        private set

    private var devicePollJob: Job? = null
    private var mirrorSession: MirrorSession? = null
    private var frameJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)
    private var emptyPollCount = 0

    // Key-to-touch simulation state
    private val activeClickKeys = mutableSetOf<String>()
    private val joystickH = mutableMapOf<String, MutableList<String>>() // mappingId -> ["left"|"right", ...] stack, max 2
    private val joystickV = mutableMapOf<String, MutableList<String>>() // mappingId -> ["up"|"down", ...] stack, max 2
    private val pointerIdMap = mutableMapOf<String, Int>() // mappingId -> pointerId
    private var nextPointerId = 0
    private var awtKeyDispatcher: java.awt.KeyEventDispatcher? = null

    private fun activeTouchCount(): Int {
        val joyIds = mutableSetOf<String>()
        joystickH.filter { it.value.isNotEmpty() }.keys.forEach { joyIds += it }
        joystickV.filter { it.value.isNotEmpty() }.keys.forEach { joyIds += it }
        return activeClickKeys.size + joyIds.size
    }

    private fun acquirePointerId(mappingId: String): Int =
        pointerIdMap.getOrPut(mappingId) { nextPointerId++ }

    private fun releasePointerId(mappingId: String): Int =
        pointerIdMap.remove(mappingId) ?: -1

    private fun startKeyDispatcher() {
        if (awtKeyDispatcher != null) return
        val dispatcher = java.awt.KeyEventDispatcher { event ->
            when (event.id) {
                java.awt.event.KeyEvent.KEY_PRESSED,
                java.awt.event.KeyEvent.KEY_RELEASED -> {
                    val keyName = java.awt.event.KeyEvent.getKeyText(event.keyCode)
                    if (keyName.isNotBlank() && keyName != "Unknown") {
                        val isDown = event.id == java.awt.event.KeyEvent.KEY_PRESSED
                        handleKeyEvent(keyName, isDown)
                    } else false
                }
                else -> false
            }
        }
        java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(dispatcher)
        awtKeyDispatcher = dispatcher
        println("[key] AWT dispatcher registered")
    }

    private fun stopKeyDispatcher() {
        awtKeyDispatcher?.let {
            java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(it)
            println("[key] AWT dispatcher removed")
        }
        awtKeyDispatcher = null
        activeClickKeys.clear()
        joystickH.clear()
        joystickV.clear()
    }

    fun startPolling() {
        devicePollJob?.cancel()
        devicePollJob = scope.launch {
            // Ensure ADB server is running once before polling
            adbService.ensureServer()

            adbService.listDevices().collect { devices ->
                val onlineDevices = devices.filter { it.isOnline }
                // Skip transient empty polls, clear after 3 consecutive = ~6s
                if (onlineDevices.isEmpty() && state.devices.isNotEmpty()) {
                    emptyPollCount++
                    if (emptyPollCount < 3) return@collect
                } else {
                    emptyPollCount = 0
                }
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

    /** Send touch DOWN (first finger). */
    fun handleTouchDown(x: Int, y: Int, pointerId: Int = 0) {
        println("[touch] DOWN id=$pointerId at $x,$y")
        state = state.copy(touchIndicators = state.touchIndicators + TouchPoint(x, y, pointerId))
        mirrorSession?.sendTouchDown(x, y, pointerId)
    }

    /** Send additional finger DOWN. */
    fun handlePointerDown(x: Int, y: Int, pointerId: Int) {
        println("[touch] POINTER_DOWN id=$pointerId at $x,$y")
        state = state.copy(touchIndicators = state.touchIndicators + TouchPoint(x, y, pointerId))
        mirrorSession?.sendPointerDown(x, y, pointerId)
    }

    /** Send touch MOVE. */
    fun handleTouchMove(x: Int, y: Int, pointerId: Int = 0) {
        state = state.copy(touchIndicators = state.touchIndicators.map {
            if (it.pointerId == pointerId) TouchPoint(x, y, pointerId) else it
        })
        mirrorSession?.sendTouchMove(x, y, pointerId)
    }

    /** Send last finger UP. */
    fun handleTouchUp(x: Int, y: Int, pointerId: Int = 0) {
        println("[touch] UP id=$pointerId at $x,$y")
        state = state.copy(touchIndicators = state.touchIndicators.filter { it.pointerId != pointerId })
        mirrorSession?.sendTouchUp(x, y, pointerId)
    }

    /** Send non-last finger UP. */
    fun handlePointerUp(x: Int, y: Int, pointerId: Int) {
        println("[touch] POINTER_UP id=$pointerId at $x,$y")
        state = state.copy(touchIndicators = state.touchIndicators.filter { it.pointerId != pointerId })
        mirrorSession?.sendPointerUp(x, y, pointerId)
    }

    /** Send BACK key via control channel (preferred) or ADB shell fallback. */
    fun handleBack() {
        val serial = state.selectedDevice?.serial
        val session = mirrorSession
        if (session?.controlChannel != null) {
            session.sendBack()
        } else if (serial != null) {
            scope.launch { adbService.sendBack(serial) }
        }
    }

    // === Key-to-touch simulation ===

    fun updateContainerSize(w: Int, h: Int) {
        if (w > 0 && h > 0) {
            state = state.copy(containerWidth = w, containerHeight = h)
        }
    }

    /**
     * Handle a keyboard event. Returns true if the key was consumed by a mapping.
     */
    fun handleKeyEvent(keyName: String, isDown: Boolean): Boolean {
        if (!state.isStreaming) { println("[key] not streaming"); return false }
        if (state.isEditingMappings) { println("[key] editing"); return false }
        val mappings = state.launchedProject?.keyMappings
        if (mappings == null) { println("[key] no project/mappings"); return false }
        val vw = state.frameWidth
        val vh = state.frameHeight
        if (vw <= 0 || vh <= 0) { println("[key] bad frame size $vw x $vh"); return false }

        println("[key] $keyName ${if (isDown) "DOWN" else "UP"} (${mappings.size} mappings, video=${vw}x${vh}, container=${state.containerWidth}x${state.containerHeight})")

        // Try CLICK mappings
        val clickMapping = mappings.find { it.type == MappingType.CLICK && it.keyName == keyName }
        if (clickMapping != null) {
            val (x, y) = toVideoCoords(clickMapping.x, clickMapping.y, vw, vh)
            println("[key] matched CLICK id=${clickMapping.id} key=${clickMapping.keyName} pos=${clickMapping.x},${clickMapping.y} => $x,$y")
            return handleClickKey(clickMapping, isDown, x, y)
        }

        // Try JOYSTICK mappings
        val joyMapping = mappings.find { m ->
            m.type == MappingType.JOYSTICK && keyName in listOf(m.keyUp, m.keyDown, m.keyLeft, m.keyRight)
        }
        if (joyMapping != null) {
            println("[key] matched JOYSTICK id=${joyMapping.id} dir=$keyName")
            return handleJoystickKey(joyMapping, keyName, isDown, vw, vh)
        }

        println("[key] no match for '$keyName'")
        return false
    }

    private fun handleClickKey(m: KeyMapping, isDown: Boolean, x: Int, y: Int): Boolean {
        if (isDown) {
            if (!activeClickKeys.add(m.id)) return true // already down
            val wasActive = activeTouchCount() - 1
            val pid = acquirePointerId(m.id)
            if (wasActive == 0) handleTouchDown(x, y, pid)
            else handlePointerDown(x, y, pid)
        } else {
            if (!activeClickKeys.remove(m.id)) return true
            val pid = releasePointerId(m.id)
            if (pid < 0) return true
            val remaining = activeTouchCount()
            if (remaining == 0) handleTouchUp(x, y, pid)
            else handlePointerUp(x, y, pid)
        }
        return true
    }

    private fun handleJoystickKey(m: KeyMapping, keyName: String, isDown: Boolean, vw: Int, vh: Int): Boolean {
        val isV = keyName == m.keyUp || keyName == m.keyDown
        val dir = when (keyName) {
            m.keyUp -> "up"; m.keyDown -> "down"; m.keyLeft -> "left"; m.keyRight -> "right"
            else -> return false
        }
        val stack = if (isV) joystickV.getOrPut(m.id) { mutableListOf() }
                   else joystickH.getOrPut(m.id) { mutableListOf() }
        val wasActive = activeTouchCount()
        val hadAxes = joystickH[m.id]?.isNotEmpty() == true || joystickV[m.id]?.isNotEmpty() == true

        if (isDown) {
            stack.remove(dir) // if already in stack, remove old position
            stack.add(dir)    // push to top (latest wins)
            if (stack.size > 2) stack.removeAt(0) // cap at 2

            val (x, y) = joystickPosFromAxes(m, vw, vh)
            if (!hadAxes) {
                val (cx, cy) = toVideoCoords(m.x, m.y, vw, vh)
                val pid = acquirePointerId(m.id)
                if (wasActive == 0) handleTouchDown(cx, cy, pid)
                else handlePointerDown(cx, cy, pid)
                if (x != cx || y != cy) handleTouchMove(x, y, pid)
            } else {
                val pid = pointerIdMap[m.id] ?: return true
                handleTouchMove(x, y, pid)
            }
        } else {
            stack.remove(dir)
            val hRemain = joystickH[m.id]?.isNotEmpty() == true
            val vRemain = joystickV[m.id]?.isNotEmpty() == true
            if (!hRemain && !vRemain) {
                joystickH.remove(m.id); joystickV.remove(m.id)
                val pid = releasePointerId(m.id)
                if (pid < 0) return true
                val (x, y) = joystickPosFromAxes(m, vw, vh)
                val remaining = activeTouchCount()
                if (remaining == 0) handleTouchUp(x, y, pid)
                else handlePointerUp(x, y, pid)
            } else {
                val pid = pointerIdMap[m.id] ?: return true
                val (x, y) = joystickPosFromAxes(m, vw, vh)
                handleTouchMove(x, y, pid)
            }
        }
        return true
    }

    /** Convert container-relative position [0,1] to video-frame coordinates, accounting for Fit letterboxing. */
    private fun toVideoCoords(mx: Float, my: Float, vw: Int, vh: Int): Pair<Int, Int> {
        val cw = state.containerWidth
        val ch = state.containerHeight
        if (cw <= 0 || ch <= 0) {
            return Pair((mx * vw).toInt().coerceIn(0, vw - 1), (my * vh).toInt().coerceIn(0, vh - 1))
        }
        val scale = kotlin.math.min(cw.toFloat() / vw, ch.toFloat() / vh)
        val imgW = vw * scale
        val imgH = vh * scale
        val offX = (cw - imgW) / 2f
        val offY = (ch - imgH) / 2f
        val vx = ((mx * cw - offX) / scale).toInt().coerceIn(0, vw - 1)
        val vy = ((my * ch - offY) / scale).toInt().coerceIn(0, vh - 1)
        return Pair(vx, vy)
    }

    /** Compute touch position from h/v stacks (last = active). Radius matches overlay ring. */
    private fun joystickPosFromAxes(m: KeyMapping, vw: Int, vh: Int): Pair<Int, Int> {
        val (cx, cy) = toVideoCoords(m.x, m.y, vw, vh)
        val cw = state.containerWidth; val ch = state.containerHeight
        val r = if (cw > 0 && ch > 0) {
            val scale = kotlin.math.min(cw.toFloat() / vw, ch.toFloat() / vh)
            val ringR = kotlin.math.min(cw, ch) * m.radius // same as overlay
            (ringR / scale).toInt()
        } else {
            (m.radius * kotlin.math.min(vw, vh)).toInt()
        }
        val h = joystickH[m.id]?.lastOrNull()
        val v = joystickV[m.id]?.lastOrNull()
        val dx = when (h) { "left" -> -r; "right" -> r; else -> 0 }
        val dy = when (v) { "up" -> -r; "down" -> r; else -> 0 }
        return Pair((cx + dx).coerceIn(0, vw - 1), (cy + dy).coerceIn(0, vh - 1))
    }

    // === Key mapping editor ===

    fun toggleOverlays() {
        state = state.copy(showOverlays = !state.showOverlays)
    }

    fun toggleTouchIndicator() {
        state = state.copy(showTouchIndicator = !state.showTouchIndicator)
    }

    private var wasShowingOverlays = false

    fun enterMappingEditor() {
        val project = state.launchedProject ?: return
        wasShowingOverlays = state.showOverlays
        state = state.copy(isEditingMappings = true, showOverlays = true, editorMappings = project.keyMappings)
    }

    fun exitMappingEditor(): Pair<String, List<KeyMapping>> {
        val pid = state.launchedProject?.id ?: ""
        val mappings = state.editorMappings
        state = state.copy(
            isEditingMappings = false,
            showOverlays = wasShowingOverlays,
            editorMappings = emptyList(),
            launchedProject = state.launchedProject?.copy(keyMappings = mappings),
        )
        return pid to mappings
    }

    fun addMappingInEditor(type: MappingType) {
        state = state.copy(editorMappings = state.editorMappings + KeyMapping(type = type))
    }

    fun removeMappingInEditor(id: String) {
        state = state.copy(editorMappings = state.editorMappings.filter { it.id != id })
    }

    fun updateMappingInEditor(id: String, keyName: String) {
        state = state.copy(editorMappings = state.editorMappings.map {
            if (it.id == id) it.copy(keyName = keyName) else it
        })
    }

    fun updateJoystickMappingInEditor(id: String, keyUp: String, keyDown: String, keyLeft: String, keyRight: String, radius: Float) {
        state = state.copy(editorMappings = state.editorMappings.map {
            if (it.id == id) it.copy(keyUp = keyUp, keyDown = keyDown, keyLeft = keyLeft, keyRight = keyRight, radius = radius) else it
        })
    }

    fun updateMappingPosition(id: String, x: Float, y: Float) {
        state = state.copy(editorMappings = state.editorMappings.map {
            if (it.id == id) it.copy(x = x.coerceIn(0f, 1f), y = y.coerceIn(0f, 1f)) else it
        })
    }

    fun toggleMirror() {
        if (state.isStreaming) {
            scope.launch { stopMirror() }
        } else {
            startMirror()
        }
    }

    private fun startFrameCollection(session: MirrorSession) {
        frameJob = CoroutineScope(Dispatchers.Main).launch {
            session.frames.collect { frameData ->
                try {
                    if (frameData.width <= 0 && frameData.height <= 0) {
                        state = state.copy(error = "投屏连接中断")
                        stopMirror()
                        return@collect
                    }
                    val bitmap = frameData.toImageBitmap()
                    state = state.copy(statusText = "", currentFrame = bitmap,
                        frameWidth = frameData.width, frameHeight = frameData.height)
                } catch (e: Exception) {
                    println("[mirror] frame error: ${e.message}")
                }
            }
        }
    }

    private fun startMirror() {
        val device = state.selectedDevice ?: return
        state = state.copy(isStreaming = true, error = null)
        startKeyDispatcher()

        scope.launch {
            val session = MirrorSession(device.serial)
            session.onStatus { state = state.copy(statusText = it) }
            mirrorSession = session
            startFrameCollection(session)

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

    fun launchProject(project: LaunchProject, serial: String, winWidthPx: Int = 0, winHeightPx: Int = 0) {
        state = state.copy(isStreaming = true, error = null, statusText = "启动应用中...", launchedProject = project)
        startKeyDispatcher()
        val newDisplay = if (project.useSecondaryDisplay) {
            val dw = if (project.useMaxResolution) winWidthPx else project.displayWidth
            val dh = if (project.useMaxResolution) winHeightPx else project.displayHeight
            "${dw}x${dh}/${project.displayDpi}"
        } else null

        scope.launch {
            // For secondary display: start mirror first to create virtual display
            if (newDisplay != null) {
                state = state.copy(statusText = "创建虚拟显示器...")
            }

            val session = MirrorSession(
                serial = serial,
                videoBitRate = project.bitrate.takeIf { it > 0 },
                maxFps = project.maxFps.takeIf { it > 0 },
                maxSize = null,
                newDisplay = newDisplay,
            )
            session.onStatus { state = state.copy(statusText = it) }
            mirrorSession = session
            startFrameCollection(session)

            val result = session.start()
            if (result.isSuccess) {
                var launchDisplay: Int? = null
                if (newDisplay != null) {
                    state = state.copy(statusText = "检测虚拟显示器...")
                    val before = adbService.getDisplayIds(serial)
                    delay(2000)
                    val after = adbService.getDisplayIds(serial)
                    launchDisplay = after.firstOrNull { it !in before }
                    println("[mirror] virtual display: before=$before after=$after new=$launchDisplay")
                }
                adbService.launchApp(serial, project.packageName, displayId = launchDisplay)
            } else {
                state = state.copy(isStreaming = false,
                    error = result.exceptionOrNull()?.message ?: "启动投屏失败")
                frameJob?.cancel()
            }
        }
    }

    private suspend fun stopMirror() {
        stopKeyDispatcher()
        mirrorSession?.stop()
        mirrorSession = null
        frameJob?.cancel()
        frameJob = null
        runCatching { lastBitmap?.close() }
        lastBitmap = null
        state = state.copy(
            isStreaming = false,
            statusText = "",
            currentFrame = null,
            frameWidth = 0,
            frameHeight = 0,
        )
    }

    private var lastBitmap: Bitmap? = null

    fun dispose() {
        kotlinx.coroutines.runBlocking {
            stopMirror()
        }
        runCatching { lastBitmap?.close() }
        lastBitmap = null
        devicePollJob?.cancel()
        scope.cancel()
    }

    private fun FrameData.toImageBitmap(): ImageBitmap {
        // Close previous bitmap to free native Skia memory (~6.7MB per frame)
        runCatching { lastBitmap?.close() }
        val imageInfo = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.PREMUL)
        val bitmap = Bitmap()
        bitmap.installPixels(imageInfo, pixels, width * 4)
        lastBitmap = bitmap
        return bitmap.asComposeImageBitmap()
    }
}
