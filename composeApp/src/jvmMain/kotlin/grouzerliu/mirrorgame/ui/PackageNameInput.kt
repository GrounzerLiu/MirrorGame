package grouzerliu.mirrorgame.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import grouzerliu.mirrorgame.adb.AdbService
import grouzerliu.mirrorgame.adb.InstalledApp

@Composable
fun PackageNameInput(
    value: String,
    onValueChange: (String) -> Unit,
    serial: String?,
    adbService: AdbService,
    modifier: Modifier = Modifier,
) {
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var showList by remember { mutableStateOf(false) }

    LaunchedEffect(serial) {
        if (serial != null) apps = adbService.listPackages(serial)
    }

    val filtered = remember(apps, value) {
        if (value.isBlank()) apps.take(50)
        else apps.filter { it.packageName.contains(value, ignoreCase = true) }.take(50)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                showList = it.isNotEmpty() || apps.isNotEmpty()
            },
            label = { Text("包名") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (showList && filtered.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 150.dp)) {
                filtered.forEach { app ->
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onValueChange(app.packageName); showList = false }
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}
