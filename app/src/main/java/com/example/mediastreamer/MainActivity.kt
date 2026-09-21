package com.example.mediastreamer

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.net.URLEncoder
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Apply Dark Scheme across app to fix login input text color
            MaterialTheme(colorScheme = darkColorScheme()) {
                AppNavigator()
            }
        }
    }
}

@Composable
fun AppNavigator() {
    val context = LocalContext.current
    val sharedPref = remember { context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE) }

    var ipAddress by remember { mutableStateOf(sharedPref.getString("SAVED_IP", "") ?: "") }
    var authToken by remember { mutableStateOf("") }
    
    // State for video playback & playlist navigation
    var playlist by remember { mutableStateOf<List<String>>(emptyList()) }
    var currentVideoIndex by remember { mutableStateOf(-1) }

    if (currentVideoIndex in playlist.indices) {
        VLCPlayerScreen(
            playlist = playlist,
            initialIndex = currentVideoIndex,
            onClose = { currentVideoIndex = -1 }
        )
    } else if (authToken.isEmpty()) {
        LoginScreen(
            initialIp = ipAddress,
            onLoginSuccess = { ip, token ->
                ipAddress = ip
                authToken = token
                sharedPref.edit().putString("SAVED_IP", ip).apply()
            }
        )
    } else {
        FileExplorerScreen(
            ipAddress = ipAddress,
            token = authToken,
            onPlayVideo = { videoUrls, selectedIndex ->
                playlist = videoUrls
                currentVideoIndex = selectedIndex
            },
            onLogout = { authToken = "" }
        )
    }
}

@Composable
fun LoginScreen(initialIp: String, onLoginSuccess: (String, String) -> Unit) {
    var ip by remember { mutableStateOf(initialIp) }
    var username by remember { mutableStateOf("admin") }
    var password by remember { mutableStateOf("raks@7") }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val textColor = Color.White
    val labelColor = Color.LightGray

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Connect to ASGI Media Server",
            style = MaterialTheme.typography.headlineSmall,
            color = textColor
        )
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = ip,
            onValueChange = { ip = it },
            label = { Text("Server Address (e.g. 192.168.1.10:8000)", color = labelColor) },
            singleLine = true,
            textStyle = TextStyle(color = textColor, fontSize = 16.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = textColor,
                unfocusedTextColor = textColor,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Gray
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username", color = labelColor) },
            singleLine = true,
            textStyle = TextStyle(color = textColor, fontSize = 16.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = textColor,
                unfocusedTextColor = textColor,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Gray
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password", color = labelColor) },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            textStyle = TextStyle(color = textColor, fontSize = 16.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = textColor,
                unfocusedTextColor = textColor,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Gray
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (ip.isBlank()) {
                    Toast.makeText(context, "Please enter IP Address", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                isLoading = true
                scope.launch {
                    try {
                        val api = ApiService.create(ip)
                        val response = api.login(LoginRequest(username, password))
                        if (response.isSuccessful && response.body()?.token != null) {
                            onLoginSuccess(ip, response.body()!!.token)
                        } else {
                            Toast.makeText(context, "Invalid credentials", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Connection failed: ${e.message}", Toast.LENGTH_LONG).show()
                    } finally {
                        isLoading = false
                    }
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (isLoading) "Connecting..." else "Login",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
fun FileExplorerScreen(
    ipAddress: String,
    token: String,
    onPlayVideo: (List<String>, Int) -> Unit,
    onLogout: () -> Unit
) {
    val pathHistory = remember { mutableStateListOf("/") }
    val currentPath = pathHistory.last()
    var items by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val api = remember(ipAddress) { ApiService.create(ipAddress) }

    fun loadPath(path: String) {
        isLoading = true
        scope.launch {
            try {
                val response = api.browseDirectory(path, "Bearer $token")
                if (response.isSuccessful) {
                    items = response.body() ?: emptyList()
                } else {
                    Toast.makeText(context, "Failed to load directory", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(currentPath) {
        loadPath(currentPath)
    }

    BackHandler(enabled = pathHistory.size > 1) {
        pathHistory.removeAt(pathHistory.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Path: $currentPath", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Button(onClick = onLogout) { Text("Logout") }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            LazyColumn {
                items(items) { item ->
                    ListItem(
                        headlineContent = { Text(item.name) },
                        leadingContent = { Text(if (item.is_dir) "📁" else "🎬") },
                        modifier = Modifier.clickable {
                            if (item.is_dir) {
                                val nextPath = if (currentPath.endsWith("/")) "$currentPath${item.name}/" else "$currentPath/${item.name}/"
                                pathHistory.add(nextPath)
                            } else {
                                // Extract video files only for playlist creation
                                val videoItems = items.filter { !it.is_dir }
                                val formattedBaseUrl = if (ipAddress.startsWith("http://") || ipAddress.startsWith("https://")) {
                                    ipAddress.removeSuffix("/")
                                } else {
                                    "http://${ipAddress.removeSuffix("/")}"
                                }

                                val videoUrls = videoItems.map { video ->
                                    val filePath = if (currentPath.endsWith("/")) "$currentPath${video.name}" else "$currentPath/${video.name}"
                                    val encodedPath = URLEncoder.encode(filePath, "UTF-8")
                                    "$formattedBaseUrl/api/media?path=$encodedPath"
                                }

                                val selectedIndex = videoItems.indexOf(item).coerceAtLeast(0)
                                onPlayVideo(videoUrls, selectedIndex)
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun VLCPlayerScreen(
    playlist: List<String>,
    initialIndex: Int,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    var currentIndex by remember { mutableStateOf(initialIndex) }
    val currentVideoUrl = playlist.getOrNull(currentIndex) ?: ""

    val libVLC = remember {
        LibVLC(
            context,
            arrayListOf(
                "--no-drop-late-frames",
                "--no-skip-frames",
                "--rtsp-tcp",
                "--http-reconnect",
                "--network-caching=1500"
            )
        )
    }
    val mediaPlayer = remember { MediaPlayer(libVLC) }

    var isPlaying by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableStateOf(0L) }
    var totalDuration by remember { mutableStateOf(1L) }
    var gestureOverlayText by remember { mutableStateOf<String?>(null) }

    // Reload video automatically when currentIndex changes
    LaunchedEffect(currentIndex) {
        if (currentVideoUrl.isNotEmpty()) {
            mediaPlayer.stop()
            val media = Media(libVLC, Uri.parse(currentVideoUrl))
            media.setHWDecoderEnabled(true, false)
            mediaPlayer.media = media
            media.release()
            mediaPlayer.play()
            isPlaying = true
        }
    }

    // Auto-hide controls timer
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(4000)
            showControls = false
        }
    }

    // Progress updates
    LaunchedEffect(Unit) {
        while (true) {
            if (mediaPlayer.isPlaying) {
                currentPosition = mediaPlayer.time
                totalDuration = if (mediaPlayer.length > 0) mediaPlayer.length else 1L
            }
            delay(500)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer.stop()
            mediaPlayer.release()
            libVLC.release()
        }
    }

    BackHandler {
        onClose()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Video View
        AndroidView(
            factory = { ctx ->
                VLCVideoLayout(ctx).apply {
                    mediaPlayer.attachViews(this, null, false, false)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Touch Gesture Capture Layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { showControls = !showControls }
                    )
                }
                .pointerInput(Unit) {
                    var totalDragX = 0f
                    var totalDragY = 0f

                    detectDragGestures(
                        onDragStart = {
                            totalDragX = 0f
                            totalDragY = 0f
                        },
                        onDragEnd = { gestureOverlayText = null },
                        onDragCancel = { gestureOverlayText = null },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            totalDragX += dragAmount.x
                            totalDragY += dragAmount.y

                            // Horizontal Drag -> Seek
                            if (abs(totalDragX) > abs(totalDragY) && abs(totalDragX) > 20f) {
                                val seekDelta = (totalDragX / 5).toLong() * 1000L
                                val targetTime = (mediaPlayer.time + seekDelta).coerceIn(0L, totalDuration)
                                mediaPlayer.time = targetTime
                                currentPosition = targetTime

                                val seconds = seekDelta / 1000
                                gestureOverlayText = if (seconds >= 0) "Seek +${seconds}s" else "Seek ${seconds}s"
                            } 
                            // Vertical Drag Right Side -> Volume
                            else if (abs(totalDragY) > abs(totalDragX) && change.position.x > size.width / 2) {
                                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

                                if (dragAmount.y < -10f) {
                                    val newVol = (currentVol + 1).coerceAtMost(maxVol)
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                                    gestureOverlayText = "Volume: ${(newVol * 100) / maxVol}%"
                                } else if (dragAmount.y > 10f) {
                                    val newVol = (currentVol - 1).coerceAtLeast(0)
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                                    gestureOverlayText = "Volume: ${(newVol * 100) / maxVol}%"
                                }
                            }
                        }
                    )
                }
        )

        // Gesture Overlay Display
        gestureOverlayText?.let { text ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.7f), shape = MaterialTheme.shapes.medium)
                    .padding(16.dp)
            ) {
                Text(text, color = Color.White, style = MaterialTheme.typography.titleLarge)
            }
        }

        // Visible Control Overlay
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                // Top Exit Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .align(Alignment.TopStart),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Text("✕", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    }
                }

                // Center Navigation & Play Controls
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous Video
                    Button(
                        onClick = {
                            if (currentIndex > 0) {
                                currentIndex--
                                showControls = true
                            }
                        },
                        enabled = currentIndex > 0
                    ) {
                        Text("⏮ Prev")
                    }

                    // Seek -10s
                    Button(onClick = {
                        val target = (mediaPlayer.time - 10000L).coerceAtLeast(0L)
                        mediaPlayer.time = target
                        currentPosition = target
                        showControls = true
                    }) {
                        Text("-10s")
                    }

                    // Play / Pause
                    Button(onClick = {
                        if (isPlaying) {
                            mediaPlayer.pause()
                            isPlaying = false
                        } else {
                            mediaPlayer.play()
                            isPlaying = true
                        }
                        showControls = true
                    }) {
                        Text(if (isPlaying) "Pause" else "Play")
                    }

                    // Seek +10s
                    Button(onClick = {
                        val target = (mediaPlayer.time + 10000L).coerceAtMost(totalDuration)
                        mediaPlayer.time = target
                        currentPosition = target
                        showControls = true
                    }) {
                        Text("+10s")
                    }

                    // Next Video
                    Button(
                        onClick = {
                            if (currentIndex < playlist.size - 1) {
                                currentIndex++
                                showControls = true
                            }
                        },
                        enabled = currentIndex < playlist.size - 1
                    ) {
                        Text("Next ⏭")
                    }
                }

                // Bottom Seekbar & Time Displays
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .align(Alignment.BottomCenter)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatMillis(currentPosition), color = Color.White)
                        Text(formatMillis(totalDuration), color = Color.White)
                    }

                    Slider(
                        value = currentPosition.toFloat(),
                        onValueChange = { newValue ->
                            currentPosition = newValue.toLong()
                            mediaPlayer.time = newValue.toLong()
                            showControls = true
                        },
                        valueRange = 0f..totalDuration.toFloat(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

fun formatMillis(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
