package grouzerliu.mirrorgame.model

import kotlin.random.Random

enum class MappingType { CLICK, JOYSTICK }

data class KeyMapping(
    val id: String = Random.nextInt().let { if (it < 0) -it else it }.toString(16),
    val type: MappingType = MappingType.CLICK,
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val radius: Float = 0.15f,
    val keyName: String = "",
    val keyUp: String = "",
    val keyDown: String = "",
    val keyLeft: String = "",
    val keyRight: String = "",
) {
    /** Human-readable display of bound keys for overlay dots. */
    val displayKeys: String
        get() = when (type) {
            MappingType.CLICK -> keyName
            MappingType.JOYSTICK -> {
                val parts = listOf(keyUp, keyDown, keyLeft, keyRight).filter { it.isNotEmpty() }
                if (parts.isEmpty()) "" else parts.joinToString("/")
            }
        }
}
