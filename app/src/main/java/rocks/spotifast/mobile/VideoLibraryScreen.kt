package rocks.spotifast.mobile

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

private data class LocalVideo(val id: Long, val name: String, val durationMs: Long, val uri: Uri)

private val VideoGreen = Color(0xFFB7FF3C)
private val VideoPurple = Color(0xFF9B7BFF)
private val VideoBackground = Color(0xFF090B10)
private val VideoPanel = Color(0xFF151923)
private val VideoMuted = Color(0xFF9AA3B5)

@Composable
fun VideoLibraryScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val player = remember(context) { ExoPlayer.Builder(context).build() }
    var videos by remember { mutableStateOf(emptyList<LocalVideo>()) }
    var selectedVideo by remember { mutableStateOf<LocalVideo?>(null) }
    var query by remember { mutableStateOf("") }
    var permissionGranted by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var draggingProgress by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableFloatStateOf(0f) }
    var isPlaying by remember { mutableStateOf(false) }
    val permission = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    fun loadVideos(): List<LocalVideo> {
        val result = mutableListOf<LocalVideo>()
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION
        )
        context.contentResolver.query(
            collection, projection, null, null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: "Untitled video"
                val duration = cursor.getLong(durationCol)
                result += LocalVideo(id, name, duration, ContentUris.withAppendedId(collection, id))
            }
        }
        return result
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
        if (granted) videos = loadVideos()
    }

    LaunchedEffect(Unit) {
        permissionGranted = ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
        if (permissionGranted) videos = loadVideos()
    }

    LaunchedEffect(player, selectedVideo) {
        while (true) {
            if (!draggingProgress) {
                positionMs = player.currentPosition.coerceAtLeast(0L)
                durationMs = player.duration.takeIf { it > 0L } ?: selectedVideo?.durationMs ?: 0L
                scrubValue = positionMs.toFloat()
            }
            isPlaying = player.isPlaying
            delay(350)
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    val filteredVideos = videos.filter { it.name.contains(query, ignoreCase = true) }

    Surface(modifier = Modifier.fillMaxSize(), color = VideoBackground) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    player.pause()
                    onBack()
                }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back to music", tint = VideoGreen)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "SPOTIFAST VIDEO",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = 0.6.sp
                    )
                    Text(
                        "LOCAL  •  PRIVATE  •  OFFLINE",
                        style = MaterialTheme.typography.labelSmall,
                        color = VideoMuted,
                        letterSpacing = 1.sp
                    )
                }
                Icon(
                    Icons.Default.VideoLibrary,
                    contentDescription = null,
                    tint = VideoPurple,
                    modifier = Modifier.size(27.dp)
                )
            }

            Spacer(Modifier.height(14.dp))

            selectedVideo?.let { video ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = VideoPanel)
                ) {
                    Column(Modifier.padding(10.dp)) {
                        AndroidView(
                            modifier = Modifier.fillMaxWidth().height(220.dp),
                            factory = { viewContext ->
                                PlayerView(viewContext).apply {
                                    this.player = player
                                    useController = false
                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                                }
                            },
                            update = { view -> view.player = player }
                        )

                        Spacer(Modifier.height(10.dp))
                        Text(
                            video.name,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        Text(
                            "ON THIS DEVICE",
                            color = VideoMuted,
                            style = MaterialTheme.typography.labelSmall,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(start = 4.dp, top = 3.dp)
                        )

                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = scrubValue.coerceIn(0f, durationMs.coerceAtLeast(1L).toFloat()),
                            onValueChange = {
                                draggingProgress = true
                                scrubValue = it
                            },
                            onValueChangeFinished = {
                                player.seekTo(scrubValue.toLong().coerceIn(0L, durationMs.coerceAtLeast(0L)))
                                positionMs = scrubValue.toLong()
                                draggingProgress = false
                            },
                            valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = VideoGreen,
                                activeTrackColor = VideoGreen,
                                inactiveTrackColor = Color(0xFF414653)
                            )
                        )
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(formatVideoDuration(positionMs), color = VideoMuted, style = MaterialTheme.typography.labelSmall)
                            Text(formatVideoDuration(durationMs), color = VideoMuted, style = MaterialTheme.typography.labelSmall)
                        }

                        Row(
                            Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = {
                                player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L))
                            }) {
                                Text("−10", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            IconButton(
                                onClick = {
                                    if (player.isPlaying) player.pause() else player.play()
                                    isPlaying = player.isPlaying
                                },
                                modifier = Modifier.size(54.dp).background(VideoGreen, CircleShape)
                            ) {
                                Icon(
                                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause video" else "Play video",
                                    tint = Color(0xFF11150A),
                                    modifier = Modifier.size(31.dp)
                                )
                            }
                            IconButton(onClick = {
                                val end = durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE
                                player.seekTo((player.currentPosition + 10_000L).coerceAtMost(end))
                            }) {
                                Text("+10", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search local videos", color = VideoMuted) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = VideoGreen) },
                shape = RoundedCornerShape(15.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VideoGreen,
                    unfocusedBorderColor = Color(0xFF303747),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = VideoGreen
                )
            )

            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("YOUR VIDEOS", color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                Spacer(Modifier.weight(1f))
                Text("${filteredVideos.size} FOUND", color = VideoMuted, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(8.dp))

            if (!permissionGranted) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = VideoPanel),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Find videos on your phone", color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Spotifast only lists local videos you allow it to access. Playback stays on your device and works offline.",
                            color = VideoMuted
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { permissionLauncher.launch(permission) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = VideoGreen,
                                contentColor = Color(0xFF11150A)
                            )
                        ) { Text("Allow video access", fontWeight = FontWeight.Bold) }
                    }
                }
            } else if (filteredVideos.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.VideoLibrary,
                            contentDescription = null,
                            tint = VideoPurple,
                            modifier = Modifier.size(42.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            if (videos.isEmpty()) "No local videos found" else "No matching videos",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Try another search or add a video to your device.",
                            color = VideoMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filteredVideos, key = { it.id }) { video ->
                        val selected = selectedVideo?.id == video.id
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable {
                                selectedVideo = video
                                player.setMediaItem(MediaItem.fromUri(video.uri))
                                player.prepare()
                                player.playWhenReady = true
                            },
                            shape = RoundedCornerShape(15.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected) Color(0xFF202A22) else VideoPanel
                            )
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier.size(48.dp).background(
                                        if (selected) VideoGreen.copy(alpha = 0.14f) else Color(0xFF252B39),
                                        RoundedCornerShape(12.dp)
                                    ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        if (selected && isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = VideoGreen,
                                        modifier = Modifier.size(29.dp)
                                    )
                                }
                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(
                                        video.name,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        formatVideoDuration(video.durationMs) + "  •  LOCAL FILE",
                                        color = VideoMuted,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatVideoDuration(ms: Long): String {
    val seconds = ms.coerceAtLeast(0L) / 1000L
    val hours = seconds / 3600L
    val minutes = (seconds % 3600L) / 60L
    val remaining = seconds % 60L
    return if (hours > 0) {
        "${hours}:${minutes.toString().padStart(2, '0')}:${remaining.toString().padStart(2, '0')}"
    } else {
        "${minutes}:${remaining.toString().padStart(2, '0')}"
    }
}
