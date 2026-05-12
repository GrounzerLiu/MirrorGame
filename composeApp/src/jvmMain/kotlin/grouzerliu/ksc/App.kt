package grouzerliu.mirrorgame

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowState
import grouzerliu.mirrorgame.adb.AdbDevice
import grouzerliu.mirrorgame.adb.AdbService
import grouzerliu.mirrorgame.model.KeyMapping
import grouzerliu.mirrorgame.model.MappingType
import grouzerliu.mirrorgame.ui.JoystickKeyBindDialog
import grouzerliu.mirrorgame.ui.KeyBindDialog
import grouzerliu.mirrorgame.ui.MappingEditOverlay
import grouzerliu.mirrorgame.ui.MappingEditorToolbar
import grouzerliu.mirrorgame.ui.ProjectEditDialog
import grouzerliu.mirrorgame.ui.ProjectListPanel
import grouzerliu.mirrorgame.viewmodel.ProjectViewModel
import grouzerliu.mirrorgame.viewmodel.ScreenViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun App(
    adbService: AdbService = remember { AdbService() },
    screenVm: ScreenViewModel = remember { ScreenViewModel(adbService).also { it.startPolling() } },
    projectVm: ProjectViewModel = remember { ProjectViewModel() },
    windowState: WindowState? = null,
) {
    MaterialTheme {
        val screenState = screenVm.state
        val projectState = projectVm.state

        DisposableEffect(Unit) {
            onDispose { screenVm.dispose() }
        }

        if (screenState.isStreaming) {
            // Full screen mirroring
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { focusRequester.requestFocus() }

            Box(modifier = Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().imePadding()
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { event ->
                    if (event.key == Key.Escape && event.type == KeyEventType.KeyUp) {
                        screenVm.handleBack(); true
                    } else false
                }) {
                val scope = rememberCoroutineScope()
                var editingMappingId by remember { mutableStateOf<String?>(null) }

                // Show overlays either from editor (edit mode) or from launched project (view mode)
                val displayMappings = if (screenState.isEditingMappings) {
                    screenState.editorMappings
                } else {
                    screenState.launchedProject?.keyMappings ?: emptyList()
                }
                val editingMapping = editingMappingId?.let { id ->
                    displayMappings.find { it.id == id }
                }

                if (screenState.currentFrame != null) {
                    val dispW = screenState.frameWidth
                    val dispH = screenState.frameHeight
                    val serial = screenState.selectedDevice?.serial
                    var containerSize by remember { mutableStateOf(IntSize.Zero) }

                    Box(modifier = Modifier.fillMaxSize()) {
                        Image(
                            bitmap = screenState.currentFrame,
                            contentDescription = "设备画面",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                                .onSizeChanged {
                                    containerSize = it
                                    screenVm.updateContainerSize(it.width, it.height)
                                }
                                .pointerInput(dispW, dispH, serial) {
                                    if (serial != null && dispW > 0 && dispH > 0) {
                                        awaitPointerEventScope {
                                            var active = false
                                            var activeIsKeyBind = false
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val change = event.changes.firstOrNull() ?: continue
                                                if (screenState.isEditingMappings) {
                                                    change.consume()
                                                    continue
                                                }
                                                if (!active && event.type == PointerEventType.Press) {
                                                    if (event.button == PointerButton.Secondary) {
                                                        scope.launch { screenVm.handleBack() }
                                                        change.consume()
                                                        continue
                                                    }
                                                    // Check mouse button bindings first
                                                    val mouseName = event.button?.let { mouseButtonName(it) }
                                                    if (mouseName != null && screenVm.handleKeyEvent(mouseName, true)) {
                                                        active = true
                                                        activeIsKeyBind = true
                                                        change.consume()
                                                        continue
                                                    }
                                                    // Mouse passthrough: simulate touch
                                                    if (!screenState.mousePassthrough) {
                                                        change.consume()
                                                        continue
                                                    }
                                                    val (dx, dy) = mapCoords(change.position.x, change.position.y, containerSize, dispW, dispH)
                                                    if (dx in 0..dispW && dy in 0..dispH) {
                                                        scope.launch { screenVm.handleTouchDown(dx, dy) }
                                                        active = true
                                                    }
                                                    change.consume()
                                                } else if (active && !activeIsKeyBind && event.type == PointerEventType.Move) {
                                                    val (dx, dy) = mapCoords(change.position.x, change.position.y, containerSize, dispW, dispH)
                                                    scope.launch { screenVm.handleTouchMove(dx, dy) }
                                                    change.consume()
                                                } else if (active && event.type == PointerEventType.Release) {
                                                    val mouseName = event.button?.let { mouseButtonName(it) }
                                                    if (activeIsKeyBind && mouseName != null) {
                                                        scope.launch { screenVm.handleKeyEvent(mouseName, false) }
                                                    } else if (!activeIsKeyBind) {
                                                        val (dx, dy) = mapCoords(change.position.x, change.position.y, containerSize, dispW, dispH)
                                                        scope.launch { screenVm.handleTouchUp(dx, dy) }
                                                    }
                                                    active = false
                                                    activeIsKeyBind = false
                                                    change.consume()
                                                }
                                            }
                                        }
                                    }
                                },
                            contentScale = ContentScale.Fit,
                        )

                        // Touch indicator — shows simulated touch positions (menu toggle)
                        if (screenState.showTouchIndicator) {
                            screenState.touchIndicators.forEach { tp ->
                                val (ix, iy) = videoToContainerCoords(tp.x, tp.y, dispW, dispH, containerSize)
                                val color = pointerColor(tp.pointerId)
                                Box(
                                    modifier = Modifier
                                        .offset { IntOffset(ix - 12, iy - 12) }
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(2.dp, Color.White, CircleShape),
                                )
                            }
                        }

                        // Mapping overlays — always visible when showOverlays is on
                        if (screenState.showOverlays && displayMappings.isNotEmpty()) {
                            displayMappings.forEach { mapping ->
                                MappingEditOverlay(
                                    mapping = mapping,
                                    containerSize = containerSize,
                                    interactive = screenState.isEditingMappings,
                                    onClick = { editingMappingId = mapping.id },
                                    onMove = { id, x, y -> screenVm.updateMappingPosition(id, x, y) },
                                )
                            }
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center) {
                        CircularProgressIndicator(color = Color.White)
                        if (screenState.statusText.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(screenState.statusText, color = Color.White, fontSize = 14.sp)
                        }
                    }
                }

                // Edit mode top toolbar
                if (screenState.isEditingMappings) {
                    var showMouseToggleBind by remember { mutableStateOf(false) }

                    MappingEditorToolbar(
                        onAddClick = { screenVm.addMappingInEditor(MappingType.CLICK) },
                        onAddJoystick = { screenVm.addMappingInEditor(MappingType.JOYSTICK) },
                        onDone = {
                            val (pid, mappings) = screenVm.exitMappingEditor()
                            projectVm.saveProjectMappings(pid, mappings)
                        },
                        mousePassthrough = screenState.mousePassthrough,
                        mouseModeToggleKey = screenState.mouseModeToggleKey,
                        onBindMouseToggle = { showMouseToggleBind = true },
                    )

                    if (showMouseToggleBind) {
                        KeyBindDialog(
                            currentKeyName = screenState.mouseModeToggleKey,
                            onBind = { keyName ->
                                screenVm.setMouseModeToggleKey(keyName)
                                showMouseToggleBind = false
                            },
                            onDelete = {
                                screenVm.setMouseModeToggleKey("")
                                showMouseToggleBind = false
                            },
                            onDismiss = { showMouseToggleBind = false },
                        )
                    }
                }

                // Draggable floating menu button
                MirrorMenuOverlay(
                    onStop = { screenVm.toggleMirror() },
                    onBack = { screenVm.handleBack() },
                    onEditMappings = { screenVm.enterMappingEditor() },
                    onToggleOverlays = { screenVm.toggleOverlays() },
                    onToggleTouchIndicator = { screenVm.toggleTouchIndicator() },
                    isEditingMappings = screenState.isEditingMappings,
                    showOverlays = screenState.showOverlays,
                    showTouchIndicator = screenState.showTouchIndicator,
                )

                // Key bind dialog for mapping editor
                editingMapping?.let { mapping ->
                    when (mapping.type) {
                        MappingType.CLICK -> KeyBindDialog(
                            currentKeyName = mapping.keyName,
                            onBind = { keyName: String ->
                                screenVm.updateMappingInEditor(mapping.id, keyName)
                                editingMappingId = null
                            },
                            onDelete = {
                                screenVm.removeMappingInEditor(mapping.id)
                                editingMappingId = null
                            },
                            onDismiss = { editingMappingId = null },
                        )
                        MappingType.JOYSTICK -> JoystickKeyBindDialog(
                            keyUp = mapping.keyUp,
                            keyDown = mapping.keyDown,
                            keyLeft = mapping.keyLeft,
                            keyRight = mapping.keyRight,
                            radius = mapping.radius,
                            onBind = { up, down, left, right, r ->
                                screenVm.updateJoystickMappingInEditor(mapping.id, up, down, left, right, r)
                                editingMappingId = null
                            },
                            onDelete = {
                                screenVm.removeMappingInEditor(mapping.id)
                                editingMappingId = null
                            },
                            onDismiss = { editingMappingId = null },
                        )
                    }
                }
            }
        } else {
            // Normal UI
            Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                .statusBarsPadding().imePadding()) {
                DeviceToolbar(
                    devices = screenState.devices,
                    selectedDevice = screenState.selectedDevice,
                    isStreaming = false,
                    onDeviceSelected = { screenVm.selectDevice(it) },
                    onToggleClick = { screenVm.toggleMirror() },
                )

                Box(modifier = Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
                    if (screenState.error != null) {
                        Text(screenState.error, color = MaterialTheme.colorScheme.error,
                            fontSize = 16.sp, textAlign = TextAlign.Center)
                    } else {
                        val density = LocalDensity.current
                        val winW = windowState?.let {
                            with(density) { it.size.width.roundToPx() }
                        } ?: 600
                        val winH = windowState?.let {
                            with(density) { it.size.height.roundToPx() }
                        } ?: 800
                        ProjectListPanel(
                            projects = projectState.projects,
                            onAdd = { projectVm.addProject() },
                            onEdit = { projectVm.editProject(it) },
                            onDelete = { projectVm.deleteProject(it) },
                            onLaunch = { project ->
                                val serial = screenState.selectedDevice?.serial
                                if (serial != null) screenVm.launchProject(project, serial, winW, winH)
                            },
                        )
                    }
                }
            }
        }

        if (projectState.showEditor && projectState.editingProject != null) {
            ProjectEditDialog(
                project = projectState.editingProject,
                serial = screenState.selectedDevice?.serial,
                adbService = adbService,
                onSave = { projectVm.saveProject(it) },
                onDismiss = { projectVm.cancelEdit() },
            )
        }
    }
}

private fun mouseButtonName(button: PointerButton): String? = when (button) {
    PointerButton.Primary -> "MouseLeft"
    PointerButton.Secondary -> "MouseRight"
    PointerButton.Tertiary -> "MouseMiddle"
    PointerButton.Back -> "Mouse4"
    PointerButton.Forward -> "Mouse5"
    else -> null
}

/** Map composable coordinates to device video coordinates (ContentScale.Fit). */
private fun mapCoords(
    px: Float, py: Float, container: IntSize, videoW: Int, videoH: Int,
): Pair<Int, Int> {
    val scale = kotlin.math.min(container.width.toFloat() / videoW, container.height.toFloat() / videoH)
    val imgW = videoW * scale; val imgH = videoH * scale
    val offX = (container.width - imgW) / 2f
    val offY = (container.height - imgH) / 2f
    return Pair(((px - offX) / scale).toInt(), ((py - offY) / scale).toInt())
}

private val POINTER_COLORS = listOf(
    Color(0xAAFF4444), // 0 red
    Color(0xAA44FF44), // 1 green
    Color(0xAA4488FF), // 2 blue
    Color(0xAAFFAA00), // 3 orange
    Color(0xAAFF44FF), // 4 magenta
    Color(0xAA00CCCC), // 5 cyan
    Color(0xAAFFFF44), // 6 yellow
    Color(0xAAFF88AA), // 7 pink
    Color(0xAA88FF88), // 8 light green
    Color(0xAACCCCFF), // 9 lavender
)

private fun pointerColor(pointerId: Int): Color =
    POINTER_COLORS[pointerId % POINTER_COLORS.size]

/** Reverse: video-frame coordinates → composable coordinates (ContentScale.Fit). */
private fun videoToContainerCoords(
    vx: Int, vy: Int, videoW: Int, videoH: Int, container: IntSize,
): Pair<Int, Int> {
    val scale = kotlin.math.min(container.width.toFloat() / videoW, container.height.toFloat() / videoH)
    val imgW = videoW * scale; val imgH = videoH * scale
    val offX = (container.width - imgW) / 2f
    val offY = (container.height - imgH) / 2f
    return Pair((vx * scale + offX).roundToInt(), (vy * scale + offY).roundToInt())
}

@Composable
private fun MirrorMenuOverlay(
    onStop: () -> Unit,
    onBack: () -> Unit,
    onEditMappings: () -> Unit,
    onToggleOverlays: () -> Unit,
    onToggleTouchIndicator: () -> Unit,
    isEditingMappings: Boolean = false,
    showOverlays: Boolean = true,
    showTouchIndicator: Boolean = false,
) {
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var showMenu by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    }
                }
        ) {
            SmallFloatingActionButton(
                onClick = { showMenu = true },
                containerColor = Color(0x88000000),
                contentColor = Color.White,
                shape = CircleShape,
            ) {
                Text("≡", fontSize = 20.sp)
            }

            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(text = { Text("返回") }, onClick = { showMenu = false; onBack() })
                DropdownMenuItem(
                    text = { Text(if (showOverlays) "隐藏映射" else "显示映射") },
                    onClick = { showMenu = false; onToggleOverlays() },
                )
                DropdownMenuItem(
                    text = { Text(if (showTouchIndicator) "隐藏触控指示" else "显示触控指示") },
                    onClick = { showMenu = false; onToggleTouchIndicator() },
                )
                DropdownMenuItem(
                    text = { Text(if (isEditingMappings) "完成编辑" else "编辑映射") },
                    onClick = { showMenu = false; onEditMappings() },
                )
                DropdownMenuItem(text = { Text("停止") }, onClick = { showMenu = false; onStop() })
            }
        }
    }
}

@Composable
private fun DeviceToolbar(
    devices: List<AdbDevice>,
    selectedDevice: AdbDevice?,
    isStreaming: Boolean,
    onDeviceSelected: (AdbDevice) -> Unit,
    onToggleClick: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxWidth(), shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.surface) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                DeviceDropdown(devices = devices, selectedDevice = selectedDevice,
                    enabled = true, onDeviceSelected = onDeviceSelected)
            }
            Button(onClick = onToggleClick,
                enabled = selectedDevice != null && devices.isNotEmpty()) {
                Text("启动")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceDropdown(devices: List<AdbDevice>, selectedDevice: AdbDevice?,
                           enabled: Boolean, onDeviceSelected: (AdbDevice) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = !expanded }) {
        OutlinedTextField(value = selectedDevice?.displayName ?: "选择设备",
            onValueChange = {}, readOnly = true, enabled = enabled,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled).fillMaxWidth(),
            singleLine = true)
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (devices.isEmpty()) {
                DropdownMenuItem(text = { Text("无可用设备") }, onClick = { expanded = false })
            } else {
                devices.forEach { device ->
                    DropdownMenuItem(text = { Text(device.displayName) },
                        onClick = { onDeviceSelected(device); expanded = false })
                }
            }
        }
    }
}
