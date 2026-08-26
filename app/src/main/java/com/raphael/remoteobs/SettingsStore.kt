package com.raphael.remoteobs

import android.content.Context
import org.json.JSONArray

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("remote_obs_settings", Context.MODE_PRIVATE)

    fun load(): AppSettings {
        return AppSettings(
            host = prefs.getString("host", "192.168.1.100") ?: "192.168.1.100",
            port = prefs.getInt("port", 4455),
            password = prefs.getString("password", "") ?: "",
            autoConnect = prefs.getBoolean("autoConnect", true),
            keepScreenAwake = prefs.getBoolean("keepScreenAwake", true),
            hapticsEnabled = prefs.getBoolean("hapticsEnabled", true),
            largeControls = prefs.getBoolean("largeControls", false),
            operatorLockEnabled = prefs.getBoolean("operatorLockEnabled", false),
            operatorPin = prefs.getString("operatorPin", "") ?: "",
            selectedTransition = prefs.getString("selectedTransition", "Cut") ?: "Cut",
            transitionDurationMs = prefs.getInt("transitionDurationMs", 300),
            sceneDisplayCount = prefs.getInt("sceneDisplayCount", 10).coerceIn(0, 20),
            previewHeightPercent = prefs.getInt("previewHeightPercent", 55).coerceIn(25, 70),
            sceneColumns = prefs.getInt("sceneColumns", 4).coerceIn(2, 6),
            sceneOrder = readStringList("sceneOrder"),
            pinnedScenes = readStringList("pinnedScenes").toSet(),
            whepUrl = prefs.getString("whepUrl", "") ?: ""
        )
    }

    fun save(settings: AppSettings) {
        prefs.edit()
            .putString("host", settings.host)
            .putInt("port", settings.port)
            .putString("password", settings.password)
            .putBoolean("autoConnect", settings.autoConnect)
            .putBoolean("keepScreenAwake", settings.keepScreenAwake)
            .putBoolean("hapticsEnabled", settings.hapticsEnabled)
            .putBoolean("largeControls", settings.largeControls)
            .putBoolean("operatorLockEnabled", settings.operatorLockEnabled)
            .putString("operatorPin", settings.operatorPin)
            .putString("selectedTransition", settings.selectedTransition)
            .putInt("transitionDurationMs", settings.transitionDurationMs)
            .putInt("sceneDisplayCount", settings.sceneDisplayCount.coerceIn(0, 20))
            .putInt("previewHeightPercent", settings.previewHeightPercent.coerceIn(25, 70))
            .putInt("sceneColumns", settings.sceneColumns.coerceIn(2, 6))
            .putString("sceneOrder", JSONArray(settings.sceneOrder).toString())
            .putString("pinnedScenes", JSONArray(settings.pinnedScenes.toList()).toString())
            .putString("whepUrl", settings.whepUrl)
            .apply()
    }

    private fun readStringList(key: String): List<String> {
        val raw = prefs.getString(key, "[]") ?: "[]"
        val array = JSONArray(raw)
        val result = mutableListOf<String>()
        for (index in 0 until array.length()) {
            val value = array.optString(index)
            if (value.isNotBlank()) {
                result += value
            }
        }
        return result
    }
}
