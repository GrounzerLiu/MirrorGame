package grouzerliu.mirrorgame.adb

data class InstalledApp(
    val packageName: String,
) {
    val displayName: String get() = packageName
}
