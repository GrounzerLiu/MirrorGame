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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import grouzerliu.mirrorgame.model.KeyMapping
import grouzerliu.mirrorgame.model.MappingType

@Composable
fun MappingEditorToolbar(
    onAddClick: () -> Unit,
    onAddJoystick: () -> Unit,
    onAddMouseJoy: () -> Unit,
    onDone: () -> Unit,
    mousePassthrough: Boolean = true,
    mouseModeToggleKey: String = "",
    onBindMouseToggle: () -> Unit = {},
    onMouseJoyCircleSettings: () -> Unit = {},
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 8.dp,
        color = Color(0xDD000000),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("编辑映射", color = Color.White, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            FilledTonalButton(onClick = onBindMouseToggle,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                Text(if (mousePassthrough) "鼠标直通" else "鼠标绑定", fontSize = 12.sp)
            }
            FilledTonalButton(onClick = onAddClick,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                Text("点击", fontSize = 12.sp)
            }
            FilledTonalButton(onClick = onAddJoystick,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                Text("摇杆", fontSize = 12.sp)
            }
            FilledTonalButton(onClick = onAddMouseJoy,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                Text("鼠杆", fontSize = 12.sp)
            }
            FilledTonalButton(onClick = onMouseJoyCircleSettings,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                Text("⊙", fontSize = 14.sp)
            }
            Button(onClick = onDone,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                Text("完成", fontSize = 12.sp)
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MappingEditOverlay(
    mapping: KeyMapping,
    containerSize: IntSize,
    onClick: () -> Unit,
    onMove: (id: String, x: Float, y: Float) -> Unit,
    interactive: Boolean = true,
) {
    if (containerSize.width <= 0 || containerSize.height <= 0) return

    val cw = containerSize.width.toFloat()
    val ch = containerSize.height.toFloat()
    val cx = cw * mapping.x
    val cy = ch * mapping.y
    val isJoy = mapping.type == MappingType.JOYSTICK || mapping.type == MappingType.MOUSE_JOYSTICK
    val dotSize = if (isJoy) 60f else 44f

    // Keep latest values accessible from pointerInput's stale closure
    val currentMapping by rememberUpdatedState(mapping)
    val currentCw by rememberUpdatedState(cw)
    val currentCh by rememberUpdatedState(ch)
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnMove by rememberUpdatedState(onMove)

    var isHovered by remember { mutableStateOf(false) }
    val dotDp = dotSize.toInt().dp
    val lastTapRef = remember { longArrayOf(0L) }

    Box(modifier = Modifier.offset(
        x = (cx - dotSize / 2f).toInt().dp,
        y = (cy - dotSize / 2f).toInt().dp,
    )) {
        // Joystick outer ring (also for mouse joystick)
        if (isJoy) {
            val ringR = cw.coerceAtMost(ch) * mapping.radius
            Box(
                modifier = Modifier
                    .offset(x = ((dotSize - ringR * 2) / 2f).toInt().dp,
                        y = ((dotSize - ringR * 2) / 2f).toInt().dp)
                    .size((ringR * 2).toInt().dp)
                    .clip(CircleShape)
                    .border(2.dp, if (mapping.type == MappingType.MOUSE_JOYSTICK) Color(0x88FF4444) else Color(0x664488FF), CircleShape),
            )
        }

        // Hover tooltip — shows key bindings
        if (isHovered) {
            val tipText = tooltipText(mapping)
            if (tipText.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .offset(x = dotDp / 2, y = -(dotDp + 6.dp))
                        .background(Color(0xEE333333), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                ) {
                    Text(tipText, color = Color.White, fontSize = 11.sp, maxLines = 2)
                }
            }
        }

        // Center dot — shows type name, interactive only in edit mode
        Box(
            modifier = Modifier
                .size(dotDp)
                .clip(CircleShape)
                .background(
                    if (mapping.type == MappingType.MOUSE_JOYSTICK) Color(0xCCFF8800)
                    else if (isJoy) Color(0xCC4488FF)
                    else Color(0xCCFF4444)
                )
                .onPointerEvent(PointerEventType.Enter) { isHovered = true }
                .onPointerEvent(PointerEventType.Exit) { isHovered = false }
                .then(
                    if (interactive) Modifier.pointerInput(currentMapping.id) {
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
                                            currentOnClick()
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
                                    val relDx = dx / currentCw
                                    val relDy = dy / currentCh
                                    currentOnMove(
                                        currentMapping.id,
                                        (currentMapping.x + relDx).coerceIn(0f, 1f),
                                        (currentMapping.y + relDy).coerceIn(0f, 1f),
                                    )
                                }
                            }
                        }
                    } else Modifier
                ),
            contentAlignment = Alignment.Center,
        ) {
            // Always show type name only
            Text(
                text = when (mapping.type) {
                    MappingType.JOYSTICK -> "摇"
                    MappingType.MOUSE_JOYSTICK -> "鼠"
                    else -> "点"
                },
                color = Color.White,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Build hover tooltip text from mapping key bindings. */
internal fun tooltipText(m: KeyMapping): String = when (m.type) {
    MappingType.CLICK -> m.keyName
    MappingType.MOUSE_JOYSTICK -> m.keyName
    MappingType.JOYSTICK -> {
        val parts = listOfNotNull(
            if (m.keyUp.isNotEmpty()) "↑${m.keyUp}" else null,
            if (m.keyDown.isNotEmpty()) "↓${m.keyDown}" else null,
            if (m.keyLeft.isNotEmpty()) "←${m.keyLeft}" else null,
            if (m.keyRight.isNotEmpty()) "→${m.keyRight}" else null,
        )
        parts.joinToString(" ")
    }
}
