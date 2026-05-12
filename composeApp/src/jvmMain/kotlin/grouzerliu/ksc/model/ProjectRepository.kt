package grouzerliu.mirrorgame.model

import com.google.gson.Gson
import java.io.File

object ProjectRepository {

    private val gson = Gson()

    private val dir: File by lazy {
        val os = System.getProperty("os.name").lowercase()
        val home = System.getProperty("user.home")
        val d = when {
            os.contains("win") -> File(System.getenv("APPDATA") ?: "$home\\AppData\\Roaming", "MirrorGame")
            os.contains("mac") -> File(home, "Library/Application Support/MirrorGame")
            else -> File(System.getenv("XDG_CONFIG_HOME") ?: "$home/.config", "MirrorGame")
        }
        d.mkdirs()
        d
    }

    /** Old combined projects.json — migrate once on first load. */
    private val legacyFile: File by lazy { File(dir, "projects.json") }

    private var migrated = false

    private fun migrateIfNeeded() {
        if (migrated || !legacyFile.exists()) { migrated = true; return }
        migrated = true
        try {
            val list: List<LaunchProject> = gson.fromJson(legacyFile.readText(),
                object : com.google.gson.reflect.TypeToken<List<LaunchProject>>() {}.type) ?: return
            list.forEach { saveFile(it) }
            legacyFile.delete()
            println("[repo] migrated ${list.size} projects")
        } catch (_: Exception) {}
    }

    private fun fileName(p: LaunchProject): String {
        val pkg = p.packageName.ifBlank { "unnamed" }
        // Sanitize: replace file-path-unfriendly chars
        val safe = pkg.replace(Regex("""[/\\:*?"<>|]"""), "_")
        return "${safe}_${p.id}.json"
    }

    private fun saveFile(p: LaunchProject) {
        try { File(dir, fileName(p)).writeText(gson.toJson(p)) }
        catch (e: Exception) { System.err.println("[repo] save ${fileName(p)}: ${e.message}") }
    }

    private fun deleteFile(p: LaunchProject) {
        runCatching { File(dir, fileName(p)).delete() }
    }

    fun load(): List<LaunchProject> {
        migrateIfNeeded()
        val result = mutableListOf<LaunchProject>()
        dir.listFiles { f -> f.name.endsWith(".json") && f.name != "prefs.txt" }?.forEach { f ->
            try {
                val p: LaunchProject = gson.fromJson(f.readText(), LaunchProject::class.java)
                if (p != null) result.add(p)
            } catch (_: Exception) {}
        }
        return result
    }

    fun save(projects: List<LaunchProject>) {
        migrateIfNeeded()
        val savedNames = mutableSetOf<String>()
        projects.forEach { p ->
            saveFile(p)
            savedNames.add(fileName(p))
        }
        // Remove files for deleted projects
        dir.listFiles { f -> f.name.endsWith(".json") && f.name != "prefs.txt" }?.forEach { f ->
            if (f.name !in savedNames) f.delete()
        }
    }
}
