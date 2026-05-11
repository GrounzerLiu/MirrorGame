package grouzerliu.mirrorgame.model

import kotlin.random.Random

data class LaunchProject(
    val id: String = Random.nextInt().let { if (it < 0) -it else it }.toString(16),
    val name: String = "",
    val packageName: String = "",
    val useSecondaryDisplay: Boolean = false,
    val displayWidth: Int = 1920,
    val displayHeight: Int = 1080,
    val displayDpi: Int = 320,
    val bitrate: Int = 8000000,
    val useMaxResolution: Boolean = true,
    val maxFps: Int = 60,
    val keyMappings: List<KeyMapping> = emptyList(),
) {
    val displaySummary: String
        get() = if (name.isNotEmpty()) name else packageName
}
