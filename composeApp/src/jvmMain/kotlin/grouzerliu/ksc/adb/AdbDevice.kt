package grouzerliu.mirrorgame.adb

data class AdbDevice(
    val serial: String,
    val status: String,
    val model: String = "",
) {
    val displayName: String
        get() = if (model.isNotEmpty()) "$model ($serial)" else serial

    val isOnline: Boolean get() = status == "device"
}
