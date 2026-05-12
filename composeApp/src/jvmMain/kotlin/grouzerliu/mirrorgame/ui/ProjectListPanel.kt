package grouzerliu.mirrorgame.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import grouzerliu.mirrorgame.model.LaunchProject

@Composable
fun ProjectListPanel(
    projects: List<LaunchProject>,
    onAdd: () -> Unit,
    onEdit: (LaunchProject) -> Unit,
    onDelete: (String) -> Unit,
    onLaunch: (LaunchProject) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (projects.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无启动项目\n点击右下角「+」创建一个", fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(projects, key = { it.id }) { project ->
                    ProjectCard(
                        project = project,
                        onEdit = { onEdit(project) },
                        onDelete = { onDelete(project.id) },
                        onLaunch = { onLaunch(project) },
                    )
                }
            }
        }

        // FAB
        FloatingActionButton(
            onClick = onAdd,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            Text("+", fontSize = 24.sp)
        }
    }
}

@Composable
private fun ProjectCard(
    project: LaunchProject,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onLaunch: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(project.displaySummary, style = MaterialTheme.typography.titleSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(project.packageName, fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledTonalButton(onClick = onLaunch,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                        Text("启动", fontSize = 13.sp)
                    }
                    OutlinedButton(onClick = onEdit,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                        Text("编辑", fontSize = 13.sp)
                    }
                    TextButton(onClick = onDelete,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                        Text("删除", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (project.useSecondaryDisplay) {
                Spacer(Modifier.height(4.dp))
                Text("辅助显示器: ${project.displayWidth}x${project.displayHeight} ${project.displayDpi}dpi",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
