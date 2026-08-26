package com.raphael.remoteobs

import androidx.compose.runtime.Immutable

enum class ConnectionState {
    Disconnected,
    Connecting,
    Connected,
    Error
}

enum class LogLevel {
    Info,
    Warning,
    Error
}

@Immutable
data class LogEntry(
    val timestampLabel: String,
    val level: LogLevel,
    val message: String
)

@Immutable
data class SceneEntry(
    val name: String,
    val pinned: Boolean = false
)

@Immutable
data class AppSettings(
    val host: String = "192.168.1.100",
    val port: Int = 4455,
    val password: String = "",
    val autoConnect: Boolean = true,
    val keepScreenAwake: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val largeControls: Boolean = false,
    val operatorLockEnabled: Boolean = false,
    val operatorPin: String = "",
    val selectedTransition: String = "Cut",
    val transitionDurationMs: Int = 300,
    val sceneOrder: List<String> = emptyList(),
    val pinnedScenes: Set<String> = emptySet(),
    val whepUrl: String = ""
)

@Immutable
data class UiState(
    val settings: AppSettings = AppSettings(),
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val connectionLabel: String = "Disconnected",
    val previewScene: String = "",
    val programScene: String = "",
    val pendingTakeScene: String = "",
    val scenes: List<SceneEntry> = emptyList(),
    val availableTransitions: List<String> = listOf("Cut", "Fade"),
    val menuOpen: Boolean = false,
    val rearrangeMode: Boolean = false,
    val operatorUnlocked: Boolean = true,
    val reconnectAttempt: Int = 0,
    val reconnecting: Boolean = false,
    val errorBanner: String? = null,
    val connectionTestResult: String? = null,
    val logs: List<LogEntry> = emptyList(),
    val previewImage: String? = null,
    val programImage: String? = null
)

sealed class UiEvent {
    data class Snackbar(val message: String) : UiEvent()
    object HapticSuccess : UiEvent()
}
