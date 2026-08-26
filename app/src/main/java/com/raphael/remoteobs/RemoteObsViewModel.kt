package com.raphael.remoteobs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RemoteObsViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsStore = SettingsStore(application)
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 16)
    val events = _events.asSharedFlow()

    private var gateway: ObsGateway? = null
    private var reconnectJob: Job? = null
    private var screenshotJob: Job? = null
    private var previewScreenshotInFlight = false
    private var programScreenshotInFlight = false
    private var manualDisconnect = false
    private var nsd: NsdDiscovery? = null

    init {
        val settings = settingsStore.load()
        _state.value = UiState(
            settings = settings,
            operatorUnlocked = !settings.operatorLockEnabled
        )

        nsd = NsdDiscovery(getApplication()) { host, _ ->
            val s = _state.value.settings
            if (s.host.isBlank() || s.host == "192.168.1.100") {
                updateSettings { it.copy(host = host) }
            }
            if (_state.value.connectionState == ConnectionState.Disconnected && s.autoConnect) {
                connect(manual = false)
            }
        }
        nsd?.start()

        if (settings.autoConnect && settings.host.isNotBlank()) {
            connect(manual = false)
        }
    }

    override fun onCleared() {
        nsd?.stop()
        super.onCleared()
    }

    fun connect(manual: Boolean = true) {
        manualDisconnect = false
        reconnectJob?.cancel()
        reconnectJob = null

        val settings = _state.value.settings
        if (settings.host.isBlank()) {
            setError("Host is required")
            return
        }

        updateState {
            it.copy(
                settings = settings,
                connectionState = ConnectionState.Connecting,
                connectionLabel = if (manual) "Connecting..." else "Auto-connecting...",
                errorBanner = null,
                reconnecting = false,
                reconnectAttempt = 0
            )
        }
        addLog(LogLevel.Info, "Connecting to ${settings.host}:${settings.port}")

        gateway()?.connect(settings.host, settings.port, settings.password)
    }

    fun disconnect() {
        manualDisconnect = true
        reconnectJob?.cancel()
        reconnectJob = null
        screenshotJob?.cancel()
        screenshotJob = null
        previewScreenshotInFlight = false
        programScreenshotInFlight = false
        gateway()?.disconnect()
        updateState {
            it.copy(
                connectionState = ConnectionState.Disconnected,
                connectionLabel = "Disconnected",
                reconnecting = false,
                reconnectAttempt = 0,
                previewImage = null,
                programImage = null
            )
        }
        addLog(LogLevel.Warning, "Disconnected")
    }

    fun toggleMenuOpen() = updateState { it.copy(menuOpen = !it.menuOpen) }

    fun closeMenu() = updateState { it.copy(menuOpen = false, rearrangeMode = false) }

    fun openRearrangeMode() = updateState { it.copy(rearrangeMode = true, menuOpen = true) }

    fun closeRearrangeMode() = updateState { it.copy(rearrangeMode = false) }

    fun setHost(value: String) = updateSettings { it.copy(host = value.trim()) }

    fun setPort(value: String) {
        updateSettings { it.copy(port = value.toIntOrNull() ?: it.port) }
    }

    fun setPassword(value: String) = updateSettings { it.copy(password = value) }

    fun setAutoConnect(enabled: Boolean) = updateSettings { it.copy(autoConnect = enabled) }

    fun setKeepScreenAwake(enabled: Boolean) = updateSettings { it.copy(keepScreenAwake = enabled) }

    fun setHapticsEnabled(enabled: Boolean) = updateSettings { it.copy(hapticsEnabled = enabled) }

    fun setLargeControls(enabled: Boolean) = updateSettings { it.copy(largeControls = enabled) }

    fun setWhepUrl(value: String) = updateSettings { it.copy(whepUrl = value.trim()) }

    fun setSelectedTransition(name: String) {
        updateSettings { it.copy(selectedTransition = name) }
        gateway()?.setCurrentTransition(name)
    }

    fun setTransitionDuration(value: String) {
        val duration = value.toIntOrNull() ?: _state.value.settings.transitionDurationMs
        updateSettings { it.copy(transitionDurationMs = duration) }
        gateway()?.setCurrentTransitionDuration(duration)
    }

    fun setOperatorLockEnabled(enabled: Boolean) {
        updateState {
            val updatedSettings = it.settings.copy(operatorLockEnabled = enabled)
            it.copy(
                settings = updatedSettings,
                operatorUnlocked = !enabled || updatedSettings.operatorPin.isBlank()
            )
        }
        settingsStore.save(_state.value.settings)
    }

    fun setOperatorPin(value: String) = updateSettings { it.copy(operatorPin = value) }

    fun unlockOperator(pin: String) {
        val settings = _state.value.settings
        if (!settings.operatorLockEnabled) {
            updateState { it.copy(operatorUnlocked = true) }
            return
        }
        if (settings.operatorPin.isBlank() || settings.operatorPin == pin) {
            updateState { it.copy(operatorUnlocked = true) }
            addLog(LogLevel.Info, "Operator unlocked")
        } else {
            setError("Invalid PIN")
        }
    }

    fun lockOperator() = updateState { it.copy(operatorUnlocked = false) }

    fun refreshScenes() {
        gateway()?.refreshSceneData()
        addLog(LogLevel.Info, "Refreshing scene data")
    }

    fun testConnection() {
        if (_state.value.connectionState != ConnectionState.Connected) {
            setError("Not connected")
            return
        }
        gateway()?.testConnection()
        updateState { it.copy(connectionTestResult = "Connection test requested") }
        addLog(LogLevel.Info, "Connection test requested")
    }

    fun copyErrorHandled() {
        updateState { it.copy(errorBanner = null) }
    }

    fun dismissConnectionTestResult() {
        updateState { it.copy(connectionTestResult = null) }
    }

    fun onSceneClicked(sceneName: String) {
        val state = _state.value
        if (state.settings.operatorLockEnabled && !state.operatorUnlocked) {
            setError("Operator lock is enabled")
            return
        }
        if (state.connectionState != ConnectionState.Connected) {
            setError("Not connected")
            return
        }

        if (state.pendingTakeScene == sceneName) {
            addLog(LogLevel.Info, "Taking $sceneName to Program")
            gateway()?.setCurrentProgramScene(sceneName)
        } else {
            addLog(LogLevel.Info, "Setting Preview to $sceneName")
            updateState { it.copy(pendingTakeScene = sceneName) }
            gateway()?.setCurrentPreviewScene(sceneName)
        }
    }

    fun moveSceneUp(sceneName: String) {
        val order = currentSceneOrder().toMutableList()
        val index = order.indexOf(sceneName)
        if (index > 0) {
            order.removeAt(index)
            order.add(index - 1, sceneName)
            updateSceneArrangement(order)
        }
    }

    fun moveSceneDown(sceneName: String) {
        val order = currentSceneOrder().toMutableList()
        val index = order.indexOf(sceneName)
        if (index in 0 until order.lastIndex) {
            order.removeAt(index)
            order.add(index + 1, sceneName)
            updateSceneArrangement(order)
        }
    }

    fun toggleScenePinned(sceneName: String) {
        val pinned = _state.value.settings.pinnedScenes.toMutableSet()
        if (!pinned.add(sceneName)) {
            pinned.remove(sceneName)
        }
        updateState {
            val updatedSettings = it.settings.copy(pinnedScenes = pinned)
            it.copy(
                settings = updatedSettings,
                scenes = mergeSceneOrdering(
                    it.scenes.map { scene -> scene.name },
                    updatedSettings.sceneOrder,
                    updatedSettings.pinnedScenes
                )
            )
        }
        settingsStore.save(_state.value.settings)
    }

    fun saveAndApplyCurrentSettings() {
        settingsStore.save(_state.value.settings)
    }

    private fun gateway(): ObsGateway {
        val current = gateway
        if (current != null) return current

        gateway = ObsGateway(
            onLog = { level, message -> addLog(level, message) },
            onConnected = {
                updateState {
                    it.copy(
                        connectionState = ConnectionState.Connected,
                        connectionLabel = "Connected",
                        reconnecting = false,
                        reconnectAttempt = 0,
                        errorBanner = null
                    )
                }
                addLog(LogLevel.Info, "Connected")
                manualDisconnect = false
                startScreenshotPolling()
            },
            onDisconnected = { reason -> handleDisconnect(reason) },
            onError = { message -> handleError(message) },
            onSceneList = { scenes, preview, program ->
                handleSceneList(scenes, preview, program)
            },
            onTransitions = { transitions, selectedTransition, duration ->
                handleTransitionList(transitions, selectedTransition, duration)
            },
            onPreviewSceneChanged = { scene ->
                updateState { it.copy(previewScene = scene, pendingTakeScene = scene) }
            },
            onProgramSceneChanged = { scene ->
                updateState { it.copy(programScene = scene) }
            },
            onProgramTakeSuccess = {
                updateState { it.copy(pendingTakeScene = "") }
                if (_state.value.settings.hapticsEnabled) {
                    _events.tryEmit(UiEvent.HapticSuccess)
                }
                addLog(LogLevel.Info, "Program take succeeded")
            }
        )
        return gateway!!
    }

    private fun handleDisconnect(reason: String?) {
        screenshotJob?.cancel()
        screenshotJob = null
        previewScreenshotInFlight = false
        programScreenshotInFlight = false
        if (manualDisconnect) {
            updateState {
                it.copy(
                    connectionState = ConnectionState.Disconnected,
                    connectionLabel = "Disconnected",
                    reconnecting = false,
                    reconnectAttempt = 0
                )
            }
            return
        }

        val message = reason?.ifBlank { null } ?: "Disconnected"
        updateState {
            it.copy(
                connectionState = ConnectionState.Disconnected,
                connectionLabel = message,
                reconnecting = true
            )
        }
        addLog(LogLevel.Warning, message)

        if (_state.value.settings.autoConnect) {
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true) return
        reconnectJob = viewModelScope.launch {
            val settings = _state.value.settings
            for (attempt in 1..5) {
                if (_state.value.connectionState == ConnectionState.Connected) {
                    break
                }
                updateState {
                    it.copy(
                        connectionState = ConnectionState.Connecting,
                        connectionLabel = "Reconnecting ($attempt/5)...",
                        reconnecting = true,
                        reconnectAttempt = attempt
                    )
                }
                _events.emit(UiEvent.Snackbar("Reconnecting ($attempt/5)..."))
                gateway()?.connect(settings.host, settings.port, settings.password)
                delay(1500)
                if (_state.value.connectionState == ConnectionState.Connected) {
                    break
                }
            }
            if (_state.value.connectionState != ConnectionState.Connected) {
                updateState {
                    it.copy(
                        connectionState = ConnectionState.Disconnected,
                        connectionLabel = "Disconnected",
                        reconnecting = false,
                        reconnectAttempt = 0
                    )
                }
                _events.emit(UiEvent.Snackbar("Reconnect attempts exhausted"))
            }
        }
    }

    private fun handleError(message: String) {
        setError(message)
        addLog(LogLevel.Error, message)
    }

    private fun setError(message: String) {
        updateState {
            it.copy(
                connectionState = ConnectionState.Error,
                connectionLabel = "Error: $message",
                errorBanner = message
            )
        }
    }

    private fun handleSceneList(scenes: List<String>, previewScene: String, programScene: String) {
        val current = _state.value
        val settings = if (current.settings.sceneOrder.isEmpty() && scenes.isNotEmpty()) {
            val seeded = current.settings.copy(sceneOrder = scenes)
            settingsStore.save(seeded)
            updateState { it.copy(settings = seeded) }
            seeded
        } else {
            current.settings
        }
        val merged = mergeSceneOrdering(scenes, settings.sceneOrder, settings.pinnedScenes)
        updateState {
            it.copy(
                scenes = merged,
                previewScene = previewScene.ifBlank { it.previewScene },
                programScene = programScene.ifBlank { it.programScene },
                pendingTakeScene = previewScene.ifBlank { it.pendingTakeScene }
            )
        }
        addLog(LogLevel.Info, "Scene list updated (${merged.size} scenes)")
    }

    private fun currentSceneOrder(): List<String> {
        return _state.value.scenes.map { it.name }
    }

    private fun handleTransitionList(transitions: List<String>, selectedTransition: String, duration: Int) {
        val currentSettings = _state.value.settings
        val mergedTransitions = if (transitions.isNotEmpty()) transitions else _state.value.availableTransitions
        updateState {
            it.copy(
                availableTransitions = mergedTransitions,
                settings = currentSettings.copy(
                    selectedTransition = selectedTransition.ifBlank { currentSettings.selectedTransition },
                    transitionDurationMs = if (duration > 0) duration else currentSettings.transitionDurationMs
                )
            )
        }
        settingsStore.save(_state.value.settings)
    }

    private fun mergeSceneOrdering(
        rawScenes: List<String>,
        savedOrder: List<String>,
        pinnedScenes: Set<String>
    ): List<SceneEntry> {
        val validScenes = rawScenes.distinct()
        val orderedNames = mutableListOf<String>()
        for (name in savedOrder) {
            if (name in validScenes && name !in orderedNames) {
                orderedNames += name
            }
        }
        for (name in validScenes) {
            if (name !in orderedNames) {
                orderedNames += name
            }
        }
        return orderedNames.map { name -> SceneEntry(name = name, pinned = name in pinnedScenes) }
            .sortedWith(compareByDescending<SceneEntry> { it.pinned }.thenBy { orderedNames.indexOf(it.name) })
    }

    private fun updateSettings(transform: (AppSettings) -> AppSettings) {
        updateState {
            val updated = transform(it.settings)
            it.copy(settings = updated)
        }
        settingsStore.save(_state.value.settings)
    }

    private fun updateSceneArrangement(order: List<String>) {
        updateState {
            val updatedSettings = it.settings.copy(sceneOrder = order)
            it.copy(
                settings = updatedSettings,
                scenes = mergeSceneOrdering(
                    it.scenes.map { scene -> scene.name },
                    updatedSettings.sceneOrder,
                    updatedSettings.pinnedScenes
                )
            )
        }
        settingsStore.save(_state.value.settings)
    }

    private fun updateState(transform: (UiState) -> UiState) {
        _state.update(transform)
    }

    private fun addLog(level: LogLevel, message: String) {
        val entry = LogEntry(timeFormat.format(Date()), level, message)
        updateState {
            val updated = (listOf(entry) + it.logs).take(120)
            it.copy(logs = updated)
        }
    }

    private fun startScreenshotPolling() {
        screenshotJob?.cancel()
        screenshotJob = viewModelScope.launch {
            var tick = 0
            while (true) {
                if (_state.value.connectionState == ConnectionState.Connected) {
                    val s = _state.value.settings
                    val preview = _state.value.previewScene
                    val program = _state.value.programScene
                    val programWhep = s.whepUrl.isNotBlank()

                    if (tick % 2 == 0 && preview.isNotBlank() && !previewScreenshotInFlight) {
                        previewScreenshotInFlight = true
                        launch {
                            gateway?.getSourceScreenshot(preview) { img ->
                                if (img != null) {
                                    updateState { it.copy(previewImage = img) }
                                }
                                previewScreenshotInFlight = false
                            } ?: run { previewScreenshotInFlight = false }
                        }
                    }
                    if (tick % 2 == 1 && program.isNotBlank() && !programScreenshotInFlight && !programWhep) {
                        programScreenshotInFlight = true
                        launch {
                            gateway?.getSourceScreenshot(program) { img ->
                                if (img != null) {
                                    updateState { it.copy(programImage = img) }
                                }
                                programScreenshotInFlight = false
                            } ?: run { programScreenshotInFlight = false }
                        }
                    }
                    tick++
                }
                delay(50)
            }
        }
    }
}
