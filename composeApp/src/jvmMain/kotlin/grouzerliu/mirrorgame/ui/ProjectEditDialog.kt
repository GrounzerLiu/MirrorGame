package grouzerliu.mirrorgame.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import grouzerliu.mirrorgame.adb.AdbService
import grouzerliu.mirrorgame.model.LaunchProject

@Composable
fun ProjectEditDialog(
    project: LaunchProject,
    serial: String?,
    adbService: AdbService,
    onSave: (LaunchProject) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(project.name) }
    var pkg by remember { mutableStateOf(project.packageName) }
    var useSecondary by remember { mutableStateOf(project.useSecondaryDisplay) }
    var dispW by remember { mutableStateOf(project.displayWidth.toString()) }
    var dispH by remember { mutableStateOf(project.displayHeight.toString()) }
    var dpi by remember { mutableStateOf(project.displayDpi.toString()) }
    var bitrate by remember { mutableStateOf(project.bitrate.toString()) }
    var useMaxRes by remember { mutableStateOf(project.useMaxResolution) }
    var maxFps by remember { mutableStateOf(project.maxFps.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (project.id.isEmpty()) "添加启动项" else "编辑启动项") },
        text = {
            Column(
                modifier = Modifier.width(360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                PackageNameInput(
                    value = pkg,
                    onValueChange = { pkg = it },
                    serial = serial,
                    adbService = adbService,
                )

                HorizontalDivider()
                Text("模拟辅助显示器", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = useSecondary, onCheckedChange = { useSecondary = it })
                    Spacer(Modifier.width(8.dp))
                    Text(if (useSecondary) "已启用" else "已禁用")
                }
                if (useSecondary) {
                    OutlinedTextField(value = dpi, onValueChange = { dpi = it },
                        label = { Text("DPI") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                    Text("分辨率", style = MaterialTheme.typography.titleSmall)
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = useMaxRes, onClick = { useMaxRes = true })
                            Spacer(Modifier.width(4.dp))
                            Text("最大化", Modifier.align(Alignment.CenterVertically))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = !useMaxRes, onClick = { useMaxRes = false })
                            Spacer(Modifier.width(4.dp))
                            Text("自定义", Modifier.align(Alignment.CenterVertically))
                        }
                    }
                    if (!useMaxRes) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = dispW, onValueChange = { dispW = it },
                                label = { Text("宽度") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = dispH, onValueChange = { dispH = it },
                                label = { Text("高度") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                    }
                }

                HorizontalDivider()
                Text("视频", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(value = bitrate, onValueChange = { bitrate = it },
                    label = { Text("比特率 (bps)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = maxFps, onValueChange = { maxFps = it },
                    label = { Text("刷新率 (fps)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(project.copy(
                    name = name, packageName = pkg,
                    useSecondaryDisplay = useSecondary,
                    displayWidth = dispW.toIntOrNull() ?: 1920,
                    displayHeight = dispH.toIntOrNull() ?: 1080,
                    displayDpi = dpi.toIntOrNull() ?: 320,
                    bitrate = bitrate.toIntOrNull() ?: 8000000,
                    useMaxResolution = useMaxRes,
                    maxFps = maxFps.toIntOrNull() ?: 60,
                ))
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
