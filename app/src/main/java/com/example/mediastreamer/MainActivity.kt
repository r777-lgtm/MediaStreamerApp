package com.example.mediastreamer

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.SurfaceView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
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
    var currentVideoUrl by remember { mutableStateOf<String?>(null) }

    if (currentVideoUrl != null) {
        VLCPlayerScreen(videoUrl = currentVideoUrl!!) {
            currentVideoUrl = null
        }
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
            onPlayVideo = { streamUrl -> currentVideoUrl = streamUrl },
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

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Connect to ASGI Media Server", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = ip,
            onValueChange = { ip = it },
            label = { Text("Server Address (e.g. 192.168.1.10:8000)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

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
            Text(if (isLoading) "Connecting..." else "Login")
        }
    }
}

@Composable
fun FileExplorerScreen(
    ipAddress: String,
    token: String,
    onPlayVideo: (String) -> Unit,
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
                                val filePath = if (currentPath.endsWith("/")) "$currentPath${item.name}" else "$currentPath/${item.name}"
                                val baseUrl = if (ipAddress.startsWith("http")) ipAddress else "http://$ipAddress"
                                val streamUrl = "$baseUrl/api/media?path=$filePath"
                                onPlayVideo(streamUrl)
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
fun VLCPlayerScreen(videoUrl: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val libVLC = remember { LibVLC(context, arrayListOf("--no-drop-late-frames", "--no-skip-frames", "--rtsp-tcp")) }
    val mediaPlayer = remember { MediaPlayer(libVLC) }

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

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                VLCVideoLayout(ctx).apply {
                    mediaPlayer.attachViews(this, null, false, false)
                    val media = Media(libVLC, Uri.parse(videoUrl))
                    mediaPlayer.media = media
                    media.release()
                    mediaPlayer.play()
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
