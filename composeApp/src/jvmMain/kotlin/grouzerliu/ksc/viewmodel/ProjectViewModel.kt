package grouzerliu.mirrorgame.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import grouzerliu.mirrorgame.model.KeyMapping
import grouzerliu.mirrorgame.model.LaunchProject
import grouzerliu.mirrorgame.model.MappingType
import grouzerliu.mirrorgame.model.ProjectRepository

data class ProjectUiState(
    val projects: List<LaunchProject> = emptyList(),
    val editingProject: LaunchProject? = null,
    val showEditor: Boolean = false,
    val editingMappingsProject: LaunchProject? = null,
    val showMappingsEditor: Boolean = false,
)

class ProjectViewModel {
    var state by mutableStateOf(ProjectUiState(projects = ProjectRepository.load()))
        private set

    fun addProject() {
        state = state.copy(
            showEditor = true,
            editingProject = LaunchProject(),
        )
    }

    fun editProject(project: LaunchProject) {
        state = state.copy(
            showEditor = true,
            editingProject = project,
        )
    }

    fun deleteProject(id: String) {
        val list = state.projects.filter { it.id != id }
        state = state.copy(projects = list)
        ProjectRepository.save(list)
    }

    fun saveProject(project: LaunchProject) {
        val list = state.projects.toMutableList()
        val idx = list.indexOfFirst { it.id == project.id }
        if (idx >= 0) {
            list[idx] = project
        } else {
            list.add(project)
        }
        state = state.copy(
            projects = list,
            showEditor = false,
            editingProject = null,
        )
        ProjectRepository.save(list)
    }

    fun saveProjectMappings(projectId: String, mappings: List<KeyMapping>) {
        val list = state.projects.toMutableList()
        val idx = list.indexOfFirst { it.id == projectId }
        if (idx >= 0) {
            list[idx] = list[idx].copy(keyMappings = mappings)
            state = state.copy(projects = list)
            ProjectRepository.save(list)
        }
    }

    fun cancelEdit() {
        state = state.copy(
            showEditor = false,
            editingProject = null,
        )
    }

    // === Key mapping editor ===

    fun editMappings(project: LaunchProject) {
        state = state.copy(
            showMappingsEditor = true,
            editingMappingsProject = project,
        )
    }

    fun closeMappingsEditor() {
        val editorProject = state.editingMappingsProject ?: return
        // Save changes to the project list
        val list = state.projects.toMutableList()
        val idx = list.indexOfFirst { it.id == editorProject.id }
        if (idx >= 0) {
            list[idx] = editorProject
            state = state.copy(projects = list, showMappingsEditor = false, editingMappingsProject = null)
            ProjectRepository.save(list)
        } else {
            state = state.copy(showMappingsEditor = false, editingMappingsProject = null)
        }
    }

    fun addMapping(type: MappingType) {
        val project = state.editingMappingsProject ?: return
        val mapping = KeyMapping(type = type)
        project.keyMappings + mapping // update list
        saveEditorProject(project.copy(keyMappings = project.keyMappings + mapping))
    }

    fun removeMapping(id: String) {
        val project = state.editingMappingsProject ?: return
        saveEditorProject(project.copy(keyMappings = project.keyMappings.filter { it.id != id }))
    }

    fun updateMapping(id: String, keyName: String) {
        val project = state.editingMappingsProject ?: return
        saveEditorProject(project.copy(
            keyMappings = project.keyMappings.map { if (it.id == id) it.copy(keyName = keyName) else it }
        ))
    }

    fun updateJoystickMapping(id: String, keyUp: String, keyDown: String, keyLeft: String, keyRight: String, radius: Float) {
        val project = state.editingMappingsProject ?: return
        saveEditorProject(project.copy(
            keyMappings = project.keyMappings.map {
                if (it.id == id) it.copy(keyUp = keyUp, keyDown = keyDown, keyLeft = keyLeft, keyRight = keyRight, radius = radius) else it
            }
        ))
    }

    private fun saveEditorProject(project: LaunchProject) {
        state = state.copy(editingMappingsProject = project)
    }
}
