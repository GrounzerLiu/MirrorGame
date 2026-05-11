package grouzerliu.mirrorgame.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import grouzerliu.mirrorgame.model.KeyMapping
import grouzerliu.mirrorgame.model.MappingType

@Composable
fun KeyMappingEditor(
    mappings: List<KeyMapping>,
    onAddClick: () -> Unit,
    onAddJoystick: () -> Unit,
    onEditMapping: (KeyMapping) -> Unit,
    onRemoveMapping: (String) -> Unit,
    onDone: () -> Unit,
) {
    var editingMapping by remember { mutableStateOf<KeyMapping?>(null) }
    var showRemoveConfirm by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        Surface(modifier = Modifier.fillMaxWidth(), shadowElevation = 4.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("按键映射", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                FilledTonalButton(onClick = onAddClick) { Text("添加点击", fontSize = 12.sp) }
                FilledTonalButton(onClick = onAddJoystick) { Text("添加摇杆", fontSize = 12.sp) }
                Button(onClick = onDone) { Text("完成", fontSize = 12.sp) }
            }
        }

        // Phone canvas area
        Box(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            val phoneWidth = 280.dp
            val phoneHeight = phoneWidth * 16f / 9f

            Box(
                modifier = Modifier
                    .width(phoneWidth)
                    .height(phoneHeight)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1a1a2e))
                    .border(1.dp, Color(0xFF333333), RoundedCornerShape(12.dp)),
            ) {
                // Draw mappings
                mappings.forEach { mapping ->
                    MappingOverlay(
                        mapping = mapping,
                        containerWidth = phoneWidth,
                        containerHeight = phoneHeight,
                        onEdit = { editingMapping = mapping },
                        onRemove = { showRemoveConfirm = mapping.id },
                        onMove = { x, y ->
                            onEditMapping(mapping.copy(
                                x = x.coerceIn(0f, 1f),
                                y = y.coerceIn(0f, 1f),
                            ))
                        },
                    )
                }

                if (mappings.isEmpty()) {
                    Text("暂无映射，点击上方「添加」按钮",
                        color = Color(0xFF888888), fontSize = 14.sp,
                        modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }

    // Key bind dialog
    editingMapping?.let { mapping ->
        when (mapping.type) {
            MappingType.CLICK -> KeyBindDialog(
                currentKeyName = mapping.keyName,
                onBind = { keyName ->
                    onEditMapping(mapping.copy(keyName = keyName))
                    editingMapping = null
                },
                onDelete = {
                    onRemoveMapping(mapping.id)
                    editingMapping = null
                },
                onDismiss = { editingMapping = null },
            )
            MappingType.JOYSTICK -> JoystickKeyBindDialog(
                keyUp = mapping.keyUp,
                keyDown = mapping.keyDown,
                keyLeft = mapping.keyLeft,
                keyRight = mapping.keyRight,
                radius = mapping.radius,
                onBind = { up, down, left, right, r ->
                    onEditMapping(mapping.copy(keyUp = up, keyDown = down, keyLeft = left, keyRight = right, radius = r))
                    editingMapping = null
                },
                onDelete = {
                    onRemoveMapping(mapping.id)
                    editingMapping = null
                },
                onDismiss = { editingMapping = null },
            )
        }
    }

    // Remove confirm
    showRemoveConfirm?.let { id ->
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = null },
            title = { Text("删除映射") },
            text = { Text("确定删除此映射？") },
            confirmButton = {
                Button(onClick = { onRemoveMapping(id); showRemoveConfirm = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirm = null }) { Text("取消") }
            },
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun MappingOverlay(
    mapping: KeyMapping,
    containerWidth: Dp,
    containerHeight: Dp,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onMove: (x: Float, y: Float) -> Unit,
) {
    val density = LocalDensity.current
    val cwPx = with(density) { containerWidth.toPx() }
    val chPx = with(density) { containerHeight.toPx() }

    val dotSize = if (mapping.type == MappingType.JOYSTICK) 44.dp else 36.dp
    val xOff = containerWidth * mapping.x
    val yOff = containerHeight * mapping.y

    // Keep latest values accessible from pointerInput's stale closure
    val currentMapping by rememberUpdatedState(mapping)
    val currentCwPx by rememberUpdatedState(cwPx)
    val currentChPx by rememberUpdatedState(chPx)
    val currentOnEdit by rememberUpdatedState(onEdit)
    val currentOnMove by rememberUpdatedState(onMove)

    var isHovered by remember { mutableStateOf(false) }
    val lastTapRef = remember { longArrayOf(0L) }

    Box(
        modifier = Modifier
            .offset(x = xOff - dotSize / 2, y = yOff - dotSize / 2)
            .size(dotSize)
    ) {
        // Joystick: outer ring + inner dot
        if (mapping.type == MappingType.JOYSTICK) {
            val ringSize = containerWidth * mapping.radius * 2
            Box(
                modifier = Modifier
                    .offset(x = -ringSize / 2 + dotSize / 2, y = -ringSize / 2 + dotSize / 2)
                    .size(ringSize)
                    .clip(CircleShape)
                    .border(2.dp, Color(0x88FFFFFF), CircleShape),
            )
        }

        // Hover tooltip
        if (isHovered) {
            val tipText = tooltipText(mapping)
            if (tipText.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .offset(x = dotSize / 2, y = -(dotSize + 6.dp))
                        .background(Color(0xEE333333), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                ) {
                    Text(tipText, color = Color.White, fontSize = 11.sp, maxLines = 2)
                }
            }
        }

        // Center dot with drag-to-move + double-tap-to-bind
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(
                    if (mapping.type == MappingType.JOYSTICK) Color(0xCC4488FF)
                    else Color(0xCCFF4444)
                )
                .onPointerEvent(PointerEventType.Enter) { isHovered = true }
                .onPointerEvent(PointerEventType.Exit) { isHovered = false }
                .pointerInput(currentMapping.id) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var dragged = false
                        var prevX = down.position.x
                        var prevY = down.position.y

                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull() ?: break

                            if (!change.pressed) {
                                if (!dragged) {
                                    val now = System.currentTimeMillis()
                                    if (now - lastTapRef[0] < 350) {
                                        currentOnEdit()
                                        lastTapRef[0] = 0L
                                    } else {
                                        lastTapRef[0] = now
                                    }
                                }
                                change.consume()
                                break
                            }

                            val dx = change.position.x - prevX
                            val dy = change.position.y - prevY
                            prevX = change.position.x
                            prevY = change.position.y

                            if (dx != 0f || dy != 0f) {
                                change.consume()
                                dragged = true
                                val relDx = dx / currentCwPx
                                val relDy = dy / currentChPx
                                currentOnMove(
                                    (currentMapping.x + relDx).coerceIn(0f, 1f),
                                    (currentMapping.y + relDy).coerceIn(0f, 1f),
                                )
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (mapping.type == MappingType.JOYSTICK) "摇" else "点",
                color = Color.White,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
