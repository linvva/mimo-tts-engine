package io.github.linvva.mimottsengine.data

enum class UiStyle(val storageKey: String, val title: String) {
    Classic("classic", "经典"),
    LiquidGlass("liquid_glass", "液态玻璃");

    companion object {
        fun fromStorage(value: String?): UiStyle = entries.firstOrNull { it.storageKey == value } ?: Classic
    }
}
