package com.raphael.remoteobs

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        hideSystemBars()
        setContent {
            MaterialTheme(
                colorScheme = remoteObsColorScheme,
                typography = androidx.compose.material3.Typography()
            ) {
                val viewModel: RemoteObsViewModel = viewModel()
                RemoteObsApp(viewModel)
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).run {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

private val remoteObsColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF7DD3C7),
    onPrimary = Color(0xFF062A27),
    secondary = Color(0xFFFFC857),
    background = Color(0xFF090B0D),
    surface = Color(0xFF14181C),
    surfaceVariant = Color(0xFF20262C),
    onSurface = Color(0xFFE8EDF0),
    onSurfaceVariant = Color(0xFFA7B0B7),
    error = Color(0xFFFF6B6B)
)

@Composable
private fun WhepPlayer(url: String, modifier: Modifier = Modifier) {
    if (url.isBlank()) return
    
    val html = """
        <!DOCTYPE html>
        <html>
        <head>
            <style>
                body { margin: 0; background: black; overflow: hidden; }
                video { width: 100vw; height: 100vh; object-fit: contain; }
            </style>
        </head>
        <body>
            <video id="video" autoplay playsinline muted></video>
            <script>
                async function start() {
                    const video = document.getElementById('video');
                    const pc = new RTCPeerConnection();
                    
                    pc.addTransceiver('video', { direction: 'recvonly' });
                    pc.ontrack = (e) => video.srcObject = e.streams[0];
                    
                    const offer = await pc.createOffer();
                    await pc.setLocalDescription(offer);
                    
                    const res = await fetch('$url', {
                        method: 'POST',
                        body: offer.sdp,
                        headers: { 'Content-Type': 'application/sdp' }
                    });
                    
                    if (res.ok) {
                        const answer = await res.text();
                        await pc.setRemoteDescription({ type: 'answer', sdp: answer });
                    }
                }
                start();
            </script>
        </body>
        </html>
    """.trimIndent()

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.apply {
                    javaScriptEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    domStorageEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                }
                webViewClient = WebViewClient()
                loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
        },
        modifier = modifier,
        update = { }
    )
}

@Composable
private fun RemoteObsApp(viewModel: RemoteObsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current
    val hapticFeedback = LocalHapticFeedback.current
    val context = LocalContext.current
    val activity = context as? Activity

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.Snackbar -> snackbarHostState.showSnackbar(event.message)
                UiEvent.HapticSuccess -> hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }

    DisposableEffect(state.settings.keepScreenAwake, activity) {
        if (state.settings.keepScreenAwake) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    Scaffold(
        snackbarHost = {
            androidx.compose.material3.SnackbarHost(hostState = snackbarHostState)
        },
        containerColor = Color(0xFF111214)
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val drawerWidth = if (maxWidth < 700.dp) maxWidth * 0.88f else maxWidth * 0.70f
            val columns = if (maxWidth < 700.dp) 2 else 4
            val scale = if (state.settings.largeControls) 1.15f else 1f

            RightEdgeDrawer(
                open = state.menuOpen,
                drawerWidth = drawerWidth,
                onOpenChange = { open ->
                    if (open) {
                        viewModel.toggleMenuOpen()
                    } else {
                        viewModel.closeMenu()
                    }
                },
                mainContent = {
                    MainControlSurface(
                        state = state,
                        sceneColumns = columns,
                        scale = scale,
                        onSceneClick = viewModel::onSceneClicked
                    )
                },
                drawerContent = {
                    if (state.rearrangeMode) {
                        RearrangeScenesScreen(
                            state = state,
                            onBack = { viewModel.closeRearrangeMode() },
                            onTogglePin = viewModel::toggleScenePinned,
                            onMoveUp = viewModel::moveSceneUp,
                            onMoveDown = viewModel::moveSceneDown
                        )
                    } else {
                        MenuScreen(
                            state = state,
                            onClose = { viewModel.closeMenu() },
                            onHostChange = viewModel::setHost,
                            onPortChange = viewModel::setPort,
                            onPasswordChange = viewModel::setPassword,
                            onConnect = { viewModel.connect() },
                            onDisconnect = { viewModel.disconnect() },
                            onRefreshScenes = { viewModel.refreshScenes() },
                            onTestConnection = { viewModel.testConnection() },
                            onAutoConnectChange = viewModel::setAutoConnect,
                            onKeepScreenAwakeChange = viewModel::setKeepScreenAwake,
                            onHapticsChange = viewModel::setHapticsEnabled,
                            onLargeControlsChange = viewModel::setLargeControls,
                            onWhepUrlChange = viewModel::setWhepUrl,
                            onTransitionChange = viewModel::setSelectedTransition,
                            onTransitionDurationChange = viewModel::setTransitionDuration,
                            onOperatorLockChange = viewModel::setOperatorLockEnabled,
                            onPinChange = viewModel::setOperatorPin,
                            onUnlock = viewModel::unlockOperator,
                            onLock = viewModel::lockOperator,
                            onOpenRearrange = viewModel::openRearrangeMode,
                            onClearConnectionTest = viewModel::dismissConnectionTestResult,
                            clipboardManager = clipboardManager,
                            scale = scale
                        )
                    }
                }
            )

            state.errorBanner?.let { errorMsg ->
                ErrorBanner(
                    message = errorMsg,
                    clipboardManager = clipboardManager,
                    onDismiss = viewModel::copyErrorHandled
                )
            }

            if (state.settings.operatorLockEnabled && !state.operatorUnlocked) {
                OperatorLockOverlay(
                    onUnlock = viewModel::unlockOperator,
                    onLock = viewModel::lockOperator
                )
            }
        }
    }
}

@Composable
private fun MainControlSurface(
    state: UiState,
    sceneColumns: Int,
    scale: Float,
    onSceneClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090B0D))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MainStatusBar(state = state)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.35f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SourcePanel(
                title = "Preview",
                sceneName = state.previewScene,
                imageData = state.previewImage,
                whepUrl = null,
                borderColor = Color(0xFF00D95C),
                modifier = Modifier.weight(1f)
            )
            SourcePanel(
                title = "Program",
                sceneName = state.programScene,
                imageData = state.programImage,
                whepUrl = state.settings.whepUrl.ifBlank { null },
                borderColor = Color(0xFFFF3C41),
                modifier = Modifier.weight(1f)
            )
        }

        SceneGrid(
            state = state,
            columns = sceneColumns,
            scale = scale,
            onSceneClick = onSceneClick,
            modifier = Modifier.weight(0.95f)
        )
    }
}

@Composable
private fun MainStatusBar(state: UiState) {
    val statusColor = when (state.connectionState) {
        ConnectionState.Connected -> Color(0xFF65D6A5)
        ConnectionState.Connecting -> Color(0xFFFFC857)
        ConnectionState.Error -> Color(0xFFFF6B6B)
        ConnectionState.Disconnected -> Color(0xFF8D969E)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = "REMOTE OBS",
                color = Color(0xFFE8EDF0),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.8.sp
            )
            Text(
                text = if (state.scenes.isEmpty()) "CONTROL SURFACE" else "${state.scenes.size} SCENES ONLINE",
                color = Color(0xFF7F8A92),
                fontSize = 9.sp,
                letterSpacing = 1.1.sp
            )
        }
        Row(
            modifier = Modifier
                .border(1.dp, statusColor.copy(alpha = 0.55f), RoundedCornerShape(3.dp))
                .background(statusColor.copy(alpha = 0.09f), RoundedCornerShape(3.dp))
                .padding(horizontal = 11.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(statusColor, RoundedCornerShape(50))
            )
            Text(
                text = state.connectionLabel.uppercase(),
                color = statusColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
        }
    }
}

@Composable
private fun SceneGrid(
    state: UiState,
    columns: Int,
    scale: Float,
    onSceneClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val tileHeight = if (scale > 1f) 110.dp else 95.dp
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.fillMaxWidth(),
        userScrollEnabled = false,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(state.scenes, key = { it.name }) { scene ->
            SceneTile(
                scene = scene,
                previewScene = state.previewScene,
                programScene = state.programScene,
                pendingTakeScene = state.pendingTakeScene,
                height = tileHeight,
                onClick = { onSceneClick(scene.name) }
            )
        }
    }
}

@Composable
private fun SceneTile(
    scene: SceneEntry,
    previewScene: String,
    programScene: String,
    pendingTakeScene: String,
    height: Dp,
    onClick: () -> Unit
) {
    val borderColor = when {
        scene.name == programScene -> Color(0xFFFF3C41)
        scene.name == previewScene -> Color(0xFF00D95C)
        scene.pinned -> Color(0xFF8FD2FF)
        else -> Color(0xFF4B555D)
    }
    Box(
        modifier = Modifier
            .height(height)
            .fillMaxWidth()
            .border(if (scene.name == pendingTakeScene) 3.dp else 1.dp, borderColor, RoundedCornerShape(4.dp))
            .background(Color(0xFF12161A), RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        if (scene.name == pendingTakeScene && scene.name != programScene) {
            Text(
                text = "TAKE",
                color = Color(0xFFFFC857),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.1.sp,
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
        Text(
            text = scene.name,
            color = Color(0xFFE0E3E7),
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            modifier = Modifier.align(Alignment.BottomCenter),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SourcePanel(
    title: String,
    sceneName: String,
    imageData: String?,
    whepUrl: String?,
    borderColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .border(2.dp, borderColor.copy(alpha = 0.72f), RoundedCornerShape(6.dp))
            .background(Color(0xFF050607), RoundedCornerShape(6.dp))
    ) {
        if (!whepUrl.isNullOrBlank()) {
            WhepPlayer(url = whepUrl, modifier = Modifier.fillMaxSize())
        } else if (!imageData.isNullOrBlank()) {
            var bitmap by remember { mutableStateOf<Bitmap?>(null) }
            LaunchedEffect(imageData) {
                bitmap = withContext(Dispatchers.Default) {
                    try {
                        val bytes = Base64.decode(imageData, Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    } catch (e: Exception) { null }
                }
            }
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xCC050607))
                    )
                )
                .padding(10.dp)
        ) {
            Text(
                text = title.uppercase(),
                color = borderColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.4.sp,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
            if (sceneName.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .background(Color(0xCC050607), RoundedCornerShape(3.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = sceneName,
                        color = Color(0xFFE8EDF0),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuScreen(
    state: UiState,
    onClose: () -> Unit,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRefreshScenes: () -> Unit,
    onTestConnection: () -> Unit,
    onAutoConnectChange: (Boolean) -> Unit,
    onKeepScreenAwakeChange: (Boolean) -> Unit,
    onHapticsChange: (Boolean) -> Unit,
    onLargeControlsChange: (Boolean) -> Unit,
    onWhepUrlChange: (String) -> Unit,
    onTransitionChange: (String) -> Unit,
    onTransitionDurationChange: (String) -> Unit,
    onOperatorLockChange: (Boolean) -> Unit,
    onPinChange: (String) -> Unit,
    onUnlock: (String) -> Unit,
    onLock: () -> Unit,
    onOpenRearrange: () -> Unit,
    onClearConnectionTest: () -> Unit,
    clipboardManager: ClipboardManager,
    scale: Float
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF15171B))
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Menu", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = onClose) { Text("Close") }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF171C20)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Connection", color = Color.White, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = state.settings.host,
                    onValueChange = onHostChange,
                    label = { Text("OBS Host") },
                    singleLine = true,
                    colors = darkTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.settings.port.toString(),
                    onValueChange = onPortChange,
                    label = { Text("Port") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = darkTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.settings.password,
                    onValueChange = onPasswordChange,
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    colors = darkTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = onConnect, modifier = Modifier.weight(1f)) { Text("Connect") }
                        Button(onClick = onDisconnect, modifier = Modifier.weight(1f)) { Text("Disconnect") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = onRefreshScenes, modifier = Modifier.weight(1f)) { Text("Refresh scenes") }
                        Button(onClick = onTestConnection, modifier = Modifier.weight(1f)) { Text("Test connection") }
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF171C20)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Behavior", color = Color.White, fontWeight = FontWeight.SemiBold)
                SettingToggleRow("Auto-connect", state.settings.autoConnect, onAutoConnectChange)
                SettingToggleRow("Keep screen awake", state.settings.keepScreenAwake, onKeepScreenAwakeChange)
                SettingToggleRow("Haptic feedback on successful take", state.settings.hapticsEnabled, onHapticsChange)
                SettingToggleRow("Large controls", state.settings.largeControls, onLargeControlsChange)

                OutlinedTextField(
                    value = state.settings.whepUrl,
                    onValueChange = onWhepUrlChange,
                    label = { Text("WHEP URL (WebRTC)") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("http://ip:8889/whep/program") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                SettingToggleRow("Operator lock", state.settings.operatorLockEnabled, onOperatorLockChange)
                OutlinedTextField(
                    value = state.settings.operatorPin,
                    onValueChange = onPinChange,
                    label = { Text("Operator PIN") },
                    singleLine = true,
                    colors = darkTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onUnlock(state.settings.operatorPin) }) { Text("Unlock") }
                    Button(onClick = onLock) { Text("Lock") }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF171C20)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Transitions", color = Color.White, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.availableTransitions.forEach { transition ->
                        FilterChip(
                            selected = transition == state.settings.selectedTransition,
                            onClick = { onTransitionChange(transition) },
                            label = { Text(transition) }
                        )
                    }
                }
                OutlinedTextField(
                    value = state.settings.transitionDurationMs.toString(),
                    onValueChange = onTransitionDurationChange,
                    label = { Text("Transition duration (ms)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = darkTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF171C20)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Scenes", color = Color.White, fontWeight = FontWeight.SemiBold)
                Button(onClick = onOpenRearrange) { Text("Rearrange scenes") }
                Text(
                    text = "Scene list updates in real time from OBS.",
                    color = Color(0xFFB7BCC3),
                    fontSize = 12.sp
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF171C20)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Diagnostics", color = Color.White, fontWeight = FontWeight.SemiBold)
                if (state.connectionTestResult != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(state.connectionTestResult, color = Color(0xFF8FD2FF), modifier = Modifier.weight(1f))
                        TextButton(onClick = onClearConnectionTest) { Text("Clear") }
                    }
                }
                Text(
                    text = "Local Wi-Fi only. No cloud services.",
                    color = Color(0xFFB7BCC3),
                    fontSize = 12.sp
                )
                Text(
                    text = "Logs",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                LazyColumn(
                    modifier = Modifier.height(160.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(state.logs.take(12)) { log ->
                        Text(
                            text = "[${log.timestampLabel}] ${log.message}",
                            color = when (log.level) {
                                LogLevel.Info -> Color(0xFFD7DBE0)
                                LogLevel.Warning -> Color(0xFFF5C542)
                                LogLevel.Error -> Color(0xFFFF5A67)
                                else -> Color(0xFFD7DBE0)
                            },
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RearrangeScenesScreen(
    state: UiState,
    onBack: () -> Unit,
    onTogglePin: (String) -> Unit,
    onMoveUp: (String) -> Unit,
    onMoveDown: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF15171B))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Rearrange scenes", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = onBack) { Text("Back") }
        }
        Text(
            text = "Pin scenes to keep them at the top, then use Up/Down to set your custom order.",
            color = Color(0xFFB7BCC3),
            fontSize = 12.sp
        )
        Divider(color = Color(0xFF2C3137))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            items(state.scenes, key = { it.name }) { scene ->
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1E23))) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = scene.name,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = { onTogglePin(scene.name) }) {
                            Text(if (scene.pinned) "Unpin" else "Pin")
                        }
                        Button(onClick = { onMoveUp(scene.name) }) { Text("Up") }
                        Button(onClick = { onMoveDown(scene.name) }) { Text("Down") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun darkTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    disabledTextColor = Color(0xFFB7BCC3),
    focusedBorderColor = Color(0xFF8B9098),
    unfocusedBorderColor = Color(0xFF8B9098),
    cursorColor = Color(0xFF8FD2FF),
    focusedLabelColor = Color(0xFFB7BCC3),
    unfocusedLabelColor = Color(0xFF8B9098),
    focusedPlaceholderColor = Color(0xFF6F7680),
    unfocusedPlaceholderColor = Color(0xFF6F7680)
)

@Composable
private fun ErrorBanner(
    message: String,
    clipboardManager: ClipboardManager,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF39191D)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = message,
                    color = Color(0xFFFFCDD2),
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    clipboardManager.setText(AnnotatedString(message))
                }) { Text("Copy") }
                TextButton(onClick = onDismiss) { Text("Dismiss") }
            }
        }
    }
}

@Composable
private fun OperatorLockOverlay(
    onUnlock: (String) -> Unit,
    onLock: () -> Unit
) {
    var inputPin by remember { mutableStateOf("") }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xAA000000)),
        contentAlignment = Alignment.Center
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1E23))) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Operator lock enabled", color = Color.White, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = inputPin,
                    onValueChange = { inputPin = it },
                    label = { Text("PIN") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onUnlock(inputPin) }) {
                        Text("Unlock")
                    }
                    Button(onClick = onLock) { Text("Keep locked") }
                }
            }
        }
    }
}

@Composable
private fun RightEdgeDrawer(
    open: Boolean,
    drawerWidth: Dp,
    onOpenChange: (Boolean) -> Unit,
    mainContent: @Composable BoxScope.() -> Unit,
    drawerContent: @Composable ColumnScope.() -> Unit
) {
    val progress by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = tween(220),
        label = "drawerProgress"
    )
    val density = LocalDensity.current
    val drawerWidthPx = with(density) { drawerWidth.toPx() }

    Box(modifier = Modifier.fillMaxSize()) {
        mainContent()

        if (progress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable { onOpenChange(false) }
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(18.dp)
                .pointerInput(open) {
                    detectHorizontalDragGestures(
                        onDragEnd = { onOpenChange(true) },
                        onDragCancel = { if (!open) onOpenChange(false) }
                    ) { _, dragAmount ->
                        if (dragAmount < -8f) {
                            onOpenChange(true)
                        } else if (dragAmount > 8f) {
                            onOpenChange(false)
                        }
                    }
                }
        )

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(drawerWidth)
                .graphicsLayer {
                    translationX = drawerWidthPx * (1f - progress)
                }
                .background(Color(0xFF15171B))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                drawerContent()
            }
        }
    }
}
