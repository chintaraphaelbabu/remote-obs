package com.raphael.remoteobs

import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class ObsGateway(
    private val onLog: (LogLevel, String) -> Unit,
    private val onConnected: () -> Unit,
    private val onDisconnected: (String?) -> Unit,
    private val onError: (String) -> Unit,
    private val onSceneList: (List<String>, String, String) -> Unit,
    private val onTransitions: (List<String>, String, Int) -> Unit,
    private val onPreviewSceneChanged: (String) -> Unit,
    private val onProgramSceneChanged: (String) -> Unit,
    private val onProgramTakeSuccess: () -> Unit
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val pendingRequests = ConcurrentHashMap<String, PendingRequest>()
    private var socket: WebSocket? = null
    private var currentHost: String = ""
    private var currentPort: Int = 4455
    private var currentPassword: String = ""

    fun connect(host: String, port: Int, password: String) {
        currentHost = host
        currentPort = port
        currentPassword = password

        disconnect()
        val request = Request.Builder()
            .url("ws://$host:$port")
            .build()

        onLog(LogLevel.Info, "Connecting to $host:$port")
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                onLog(LogLevel.Info, "Socket opened")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                onLog(LogLevel.Warning, "Socket closing: $reason")
                onDisconnected(reason.ifBlank { null })
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                onLog(LogLevel.Warning, "Socket closed: $reason")
                onDisconnected(reason.ifBlank { null })
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val message = when {
                    response != null -> "OBS WebSocket rejected connection (HTTP ${response.code})"
                    t is java.net.ConnectException -> "Cannot reach OBS at $currentHost:$currentPort. Check the IP, firewall, and OBS WebSocket server."
                    t is java.net.SocketTimeoutException -> "Connection to OBS timed out at $currentHost:$currentPort. Check that both devices are on the same network."
                    t is java.net.UnknownHostException -> "OBS host '$currentHost' could not be found. Check the IP address."
                    else -> "OBS WebSocket failed: ${t.message ?: t.javaClass.simpleName}"
                }
                onLog(LogLevel.Error, message)
                onError(message)
                onDisconnected(message)
            }
        })
    }

    fun disconnect() {
        pendingRequests.clear()
        socket?.close(1000, "Disconnect")
        socket = null
    }

    fun refreshSceneData() {
        requestSceneList()
        requestTransitionList()
    }

    fun requestSceneList() {
        sendRequest("GetSceneList", JSONObject()) { responseData, success, comment ->
            if (!success) {
                onError(comment ?: "Failed to fetch scene list")
                return@sendRequest
            }

            val scenes = mutableListOf<String>()
            val scenesArray = responseData.optJSONArray("scenes") ?: JSONArray()
            for (index in 0 until scenesArray.length()) {
                val sceneName = scenesArray.optJSONObject(index)?.optString("sceneName").orEmpty()
                if (sceneName.isNotBlank()) {
                    scenes += sceneName
                }
            }

            val previewScene = responseData.optString("currentPreviewSceneName")
                .ifBlank { responseData.optString("previewSceneName") }
            val programScene = responseData.optString("currentProgramSceneName")
                .ifBlank { responseData.optString("programSceneName") }

            onSceneList(scenes, previewScene, programScene)
        }
    }

    fun requestTransitionList() {
        sendRequest("GetSceneTransitionList", JSONObject()) { responseData, success, comment ->
            if (!success) {
                onError(comment ?: "Failed to fetch transition list")
                return@sendRequest
            }

            val transitions = mutableListOf<String>()
            val transitionsArray = responseData.optJSONArray("transitions") ?: JSONArray()
            for (index in 0 until transitionsArray.length()) {
                val transitionName = transitionsArray.optJSONObject(index)?.optString("transitionName").orEmpty()
                if (transitionName.isNotBlank()) {
                    transitions += transitionName
                }
            }

            val currentTransition = responseData.optString("currentTransitionName")
                .ifBlank { responseData.optString("currentSceneTransitionName", "Cut") }
            val currentDuration = when {
                responseData.has("currentTransitionDuration") -> responseData.optInt("currentTransitionDuration", 300)
                responseData.has("currentSceneTransitionDuration") -> responseData.optInt("currentSceneTransitionDuration", 300)
                else -> 300
            }

            onTransitions(transitions, currentTransition, currentDuration)
        }
    }

    fun setCurrentPreviewScene(sceneName: String) {
        sendRequest("SetCurrentPreviewScene", JSONObject().put("sceneName", sceneName)) { _, success, comment ->
            if (!success) {
                onError(comment ?: "Preview scene change failed")
            }
        }
    }

    fun setCurrentProgramScene(sceneName: String) {
        sendRequest("SetCurrentProgramScene", JSONObject().put("sceneName", sceneName)) { _, success, comment ->
            if (success) {
            } else {
                onError(comment ?: "Program scene change failed")
            }
        }
    }

    fun setCurrentTransition(transitionName: String) {
        sendRequest("SetCurrentSceneTransition", JSONObject().put("transitionName", transitionName)) { _, success, comment ->
            if (!success) {
                onError(comment ?: "Transition change failed")
            }
        }
    }

    fun setCurrentTransitionDuration(durationMs: Int) {
        sendRequest("SetCurrentSceneTransitionDuration", JSONObject().put("transitionDuration", durationMs)) { _, success, comment ->
            if (!success) {
                onError(comment ?: "Transition duration change failed")
            }
        }
    }

    fun testConnection() {
        requestSceneList()
    }

    fun getSourceScreenshot(sourceName: String, onResult: (String?) -> Unit) {
        if (socket == null || sourceName.isBlank()) {
            onResult(null)
            return
        }
        val params = JSONObject()
            .put("sourceName", sourceName)
            .put("imageFormat", "jpg")
            .put("imageWidth", 1280)
            .put("imageHeight", 720)
            .put("imageCompressionQuality", 60)

        sendRequest("GetSourceScreenshot", params) { data, success, _ ->
            if (success) {
                val imgData = data.optString("imageData")
                if (imgData.isNotBlank()) {
                    val base64 = if (imgData.contains(",")) imgData.substringAfter(",") else imgData
                    onResult(base64)
                } else {
                    onResult(null)
                }
            } else {
                onResult(null)
            }
        }
    }

    private fun handleMessage(raw: String) {
        val message = try {
            JSONObject(raw)
        } catch (_: Exception) {
            onError("OBS sent an invalid WebSocket message")
            return
        }
        when (message.optInt("op")) {
            0 -> handleHello(message.optJSONObject("d") ?: JSONObject())
            2 -> handleIdentified()
            5 -> handleEvent(message.optJSONObject("d") ?: JSONObject())
            7 -> handleRequestResponse(message.optJSONObject("d") ?: JSONObject())
        }
    }

    private fun handleHello(data: JSONObject) {
        val identifyData = JSONObject()
            .put("rpcVersion", 1)
            .put("eventSubscriptions", 5)

        val authentication = data.optJSONObject("authentication")
        if (authentication != null) {
            val challenge = authentication.optString("challenge")
            val salt = authentication.optString("salt")
            identifyData.put("authentication", buildAuthToken(currentPassword, salt, challenge))
        }

        socket?.send(
            JSONObject()
                .put("op", 1)
                .put("d", identifyData)
                .toString()
        )
        onLog(LogLevel.Info, "Hello received; identifying")
    }

    private fun handleIdentified() {
        onLog(LogLevel.Info, "OBS identified")
        onConnected()
        refreshSceneData()
    }

    private fun handleEvent(data: JSONObject) {
        when (data.optString("eventType")) {
            "CurrentPreviewSceneChanged" -> {
                val eventData = data.optJSONObject("eventData") ?: JSONObject()
                val sceneName = eventData.optString("sceneName")
                if (sceneName.isNotBlank()) {
                    onPreviewSceneChanged(sceneName)
                }
            }
            "CurrentProgramSceneChanged" -> {
                val eventData = data.optJSONObject("eventData") ?: JSONObject()
                val sceneName = eventData.optString("sceneName")
                if (sceneName.isNotBlank()) {
                    onProgramSceneChanged(sceneName)
                }
            }
            "SceneCreated",
            "SceneRemoved",
            "SceneNameChanged",
            "SceneListChanged" -> refreshSceneData()
        }
    }

    private fun handleRequestResponse(data: JSONObject) {
        val requestId = data.optString("requestId")
        val requestType = data.optString("requestType")
        val requestStatus = data.optJSONObject("requestStatus") ?: JSONObject()
        val responseData = data.optJSONObject("responseData") ?: JSONObject()
        val success = requestStatus.optBoolean("result", true)
        val comment = requestStatus.optString("comment").ifBlank { null }
        pendingRequests.remove(requestId)?.callback?.invoke(responseData, success, comment)
        if (!success && comment != null) {
            onError(comment)
        }
        if (success) {
            onLog(LogLevel.Info, "$requestType completed")
            if (requestType == "SetCurrentProgramScene") {
                onProgramTakeSuccess()
            }
        }
    }

    private fun sendRequest(
        requestType: String,
        requestData: JSONObject,
        callback: ((JSONObject, Boolean, String?) -> Unit)? = null
    ) {
        val requestId = UUID.randomUUID().toString()
        if (callback != null) {
            pendingRequests[requestId] = PendingRequest(requestType, callback)
        }

        val payload = JSONObject()
            .put("op", 6)
            .put(
                "d",
                JSONObject()
                    .put("requestType", requestType)
                    .put("requestId", requestId)
                    .put("requestData", requestData)
            )

        val sent = socket?.send(payload.toString()) == true
        if (!sent) {
            pendingRequests.remove(requestId)
            onError("Unable to send $requestType")
        }
    }

    private fun buildAuthToken(password: String, salt: String, challenge: String): String {
        val secret = sha256Base64(password + salt)
        return sha256Base64(secret + challenge)
    }

    private fun sha256Base64(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8))
        return Base64.getEncoder().encodeToString(digest)
    }

    private data class PendingRequest(
        val requestType: String,
        val callback: (JSONObject, Boolean, String?) -> Unit
    )
}
