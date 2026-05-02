package grouzerliu.mirrorgame

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import grouzerliu.mirrorgame.adb.AdbDevice
import grouzerliu.mirrorgame.viewmodel.ScreenViewModel

@Composable
fun App(viewModel: ScreenViewModel = remember { ScreenViewModel().also { it.startPolling() } }) {
    MaterialTheme {
        val state = viewModel.state

        DisposableEffect(Unit) {
            onDispose { viewModel.dispose() }
        }

        Column(
            modifier = Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .imePadding()
        ) {
            // Toolbar
            DeviceToolbar(
                devices = state.devices,
                selectedDevice = state.selectedDevice,
                isStreaming = state.isStreaming,
                onDeviceSelected = { viewModel.selectDevice(it) },
                onToggleClick = { viewModel.toggleMirror() },
            )

            // Screen display area
            Box(
                modifier = Modifier.fillMaxSize()
                    .padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (state.isStreaming && state.currentFrame != null) {
                    Image(
                        bitmap = state.currentFrame,
                        contentDescription = "设备画面",
                        modifier = Modifier.fillMaxSize()
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit,
                    )
                } else if (state.isStreaming) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        if (state.statusText.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(state.statusText, fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else if (state.error != null) {
                    Text(
                        text = state.error,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    Text(
                        text = if (state.devices.isEmpty()) {
                            "未检测到设备\n请连接 Android 设备"
                        } else {
                            "选择设备后点击「启动」开始投屏"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                    )
                }
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Device dropdown
            Box(modifier = Modifier.weight(1f)) {
                DeviceDropdown(
                    devices = devices,
                    selectedDevice = selectedDevice,
                    enabled = !isStreaming,
                    onDeviceSelected = onDeviceSelected,
                )
            }

            // Toggle button
            Button(
                onClick = onToggleClick,
                enabled = selectedDevice != null && devices.isNotEmpty(),
                colors = if (isStreaming) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) {
                Text(if (isStreaming) "停止" else "启动")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceDropdown(
    devices: List<AdbDevice>,
    selectedDevice: AdbDevice?,
    enabled: Boolean,
    onDeviceSelected: (AdbDevice) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded },
    ) {
        OutlinedTextField(
            value = selectedDevice?.displayName ?: "选择设备",
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled).fillMaxWidth(),
            singleLine = true,
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            if (devices.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("无可用设备") },
                    onClick = { expanded = false },
                )
            } else {
                devices.forEach { device ->
                    DropdownMenuItem(
                        text = { Text(device.displayName) },
                        onClick = {
                            onDeviceSelected(device)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
