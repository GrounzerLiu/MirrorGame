package grouzerliu.mirrorgame.model

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

object ProjectRepository {

    private val gson = Gson()

    private val file: File by lazy {
        val dir = configDir()
        dir.mkdirs()
        File(dir, "projects.json")
    }

    private fun configDir(): File {
        val os = System.getProperty("os.name").lowercase()
        val home = System.getProperty("user.home")
        return when {
            os.contains("win") -> {
                val appdata = System.getenv("APPDATA") ?: "$home\\AppData\\Roaming"
                File(appdata, "MirrorGame")
            }
            os.contains("mac") -> {
                File(home, "Library/Application Support/MirrorGame")
            }
            else -> {
                val xdg = System.getenv("XDG_CONFIG_HOME") ?: "$home/.config"
                File(xdg, "MirrorGame")
            }
        }
    }

    fun load(): List<LaunchProject> {
        if (!file.exists()) return emptyList()
        return try {
            val type = object : TypeToken<List<LaunchProject>>() {}.type
            gson.fromJson(file.readText(), type) ?: emptyList()
        } catch (e: Exception) {
            System.err.println("[repo] load error: ${e.message}")
            emptyList()
        }
    }

    fun save(projects: List<LaunchProject>) {
        try {
            file.writeText(gson.toJson(projects))
        } catch (e: Exception) {
            System.err.println("[repo] save error: ${e.message}")
        }
    }
}
