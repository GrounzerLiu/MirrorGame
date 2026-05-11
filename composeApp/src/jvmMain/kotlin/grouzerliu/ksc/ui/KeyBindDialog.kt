package grouzerliu.mirrorgame.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun KeyBindDialog(
    currentKeyName: String,
    onBind: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var key by remember { mutableStateOf(currentKeyName) }
    var capturing by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(340.dp).onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp && capturing) {
                    val name = event.key.toString().let { raw ->
                        when {
                            raw.startsWith("Key(") -> raw.removePrefix("Key(").removeSuffix(")")
                            raw.startsWith("Key: ") -> raw.removePrefix("Key: ")
                            else -> raw
                        }
                    }
                    if (name.isNotBlank() && name != "Unknown") {
                        key = name
                        capturing = false
                        true
                    } else false
                } else false
            },
            shape = MaterialTheme.shapes.large,
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("绑定按键", style = MaterialTheme.typography.titleMedium)

                Text(
                    text = if (capturing) "请按下按键..." else "点击按钮来绑定按键",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Key row — same style as DirectionKeyRow
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("按键", fontSize = 14.sp, modifier = Modifier.width(60.dp))
                    OutlinedButton(
                        onClick = { capturing = true },
                        modifier = Modifier.weight(1f),
                        colors = if (capturing) ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ) else ButtonDefaults.outlinedButtonColors(),
                    ) {
                        Text(
                            text = if (capturing) "按下按键..." else key.ifEmpty { "未绑定" },
                            fontSize = 13.sp,
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onBind(key) }) { Text("确定") }
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    )) { Text("删除") }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun JoystickKeyBindDialog(
    keyUp: String,
    keyDown: String,
    keyLeft: String,
    keyRight: String,
    radius: Float,
    onBind: (up: String, down: String, left: String, right: String, radius: Float) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var up by remember { mutableStateOf(keyUp) }
    var down by remember { mutableStateOf(keyDown) }
    var left by remember { mutableStateOf(keyLeft) }
    var right by remember { mutableStateOf(keyRight) }
    var r by remember { mutableFloatStateOf(radius) }
    var capturing by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(340.dp).onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp && capturing != null) {
                    val name = event.key.toString().let { raw ->
                        when {
                            raw.startsWith("Key(") -> raw.removePrefix("Key(").removeSuffix(")")
                            raw.startsWith("Key: ") -> raw.removePrefix("Key: ")
                            else -> raw
                        }
                    }
                    if (name.isNotBlank() && name != "Unknown") {
                        when (capturing) {
                            "up" -> up = name
                            "down" -> down = name
                            "left" -> left = name
                            "right" -> right = name
                        }
                        capturing = null
                        true
                    } else false
                } else false
            },
            shape = MaterialTheme.shapes.large,
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("绑定摇杆按键", style = MaterialTheme.typography.titleMedium)

                Text(
                    text = if (capturing != null) "请按下按键..." else "点击方向按钮来绑定按键",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                DirectionKeyRow(label = "↑ 上", key = up, isCapturing = capturing == "up") { capturing = "up" }
                DirectionKeyRow(label = "↓ 下", key = down, isCapturing = capturing == "down") { capturing = "down" }
                DirectionKeyRow(label = "← 左", key = left, isCapturing = capturing == "left") { capturing = "left" }
                DirectionKeyRow(label = "→ 右", key = right, isCapturing = capturing == "right") { capturing = "right" }

                HorizontalDivider()
                Text("摇杆范围", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("%.0f%%".format(r * 100), fontSize = 13.sp, modifier = Modifier.width(44.dp))
                    Slider(
                        value = r,
                        onValueChange = { r = it },
                        valueRange = 0.05f..0.5f,
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onBind(up, down, left, right, r) }) { Text("确定") }
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    )) { Text("删除") }
                }
            }
        }
    }
}

@Composable
private fun DirectionKeyRow(
    label: String,
    key: String,
    isCapturing: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 14.sp, modifier = Modifier.width(60.dp))
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.weight(1f),
            colors = if (isCapturing) ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) else ButtonDefaults.outlinedButtonColors(),
        ) {
            Text(
                text = if (isCapturing) "按下按键..." else key.ifEmpty { "未绑定" },
                fontSize = 13.sp,
            )
        }
    }
}
