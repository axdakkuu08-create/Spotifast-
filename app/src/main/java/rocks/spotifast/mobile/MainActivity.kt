package rocks.spotifast.mobile

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

data class LocalTrack(val id: Long, val title: String, val artist: String, val uri: Uri)

class MainActivity : ComponentActivity() {
    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player = ExoPlayer.Builder(this).build()
        setContent {
            SpotifastTheme {
                SpotifastScreen(
                    tracksProvider = { loadTracks() },
                    onPlay = { track ->
                        val p = player ?: return@SpotifastScreen
                        p.setMediaItem(MediaItem.fromUri(track.uri))
                        p.prepare()
                        p.play()
                    },
                    onToggle = {
                        val p = player ?: return@SpotifastScreen
                        if (p.isPlaying) p.pause() else p.play()
                    },
                    isPlaying = { player?.isPlaying == true }
                )
            }
        }
    }

    private fun loadTracks(): List<LocalTrack> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val tracks = mutableListOf<LocalTrack>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        contentResolver.query(
            collection, projection, selection, null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val title = cursor.getString(titleCol) ?: "Unknown title"
                val artist = cursor.getString(artistCol) ?: "Unknown artist"
                tracks += LocalTrack(
                    id, title, artist,
                    ContentUris.withAppendedId(collection, id)
                )
            }
        }
        return tracks
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }
}

@Composable
private fun SpotifastTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = Color(0xFF1ED760),
        background = Color(0xFF101010),
        surface = Color(0xFF1B1B1B),
        onPrimary = Color.Black
    )
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
private fun SpotifastScreen(
    tracksProvider: () -> List<LocalTrack>,
    onPlay: (LocalTrack) -> Unit,
    onToggle: () -> Unit,
    isPlaying: () -> Boolean
) {
    var tracks by remember { mutableStateOf(emptyList<LocalTrack>()) }
    var selected by remember { mutableStateOf<LocalTrack?>(null) }
    var permissionGranted by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }

    val permission = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
        if (granted) tracks = tracksProvider()
    }

    LaunchedEffect(Unit) {
        permissionGranted = ContextCompat.checkSelfPermission(
            androidx.compose.ui.platform.LocalContext.current,
            permission
        ) == PackageManager.PERMISSION_GRANTED
        if (permissionGranted) tracks = tracksProvider()
    }

    DisposableEffect(Unit) {
        onDispose { }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.background(Color(0xFF1ED760), RoundedCornerShape(14.dp)).padding(10.dp)
                ) {
                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.Black)
                }
                Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                    Text("SPOTIFAST", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text("Your music. Your device.", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { if (permissionGranted) tracks = tracksProvider() else permissionLauncher.launch(permission) }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh music library")
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("Your library", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("${tracks.size} local tracks", color = Color.LightGray)
            Spacer(Modifier.height(14.dp))

            if (!permissionGranted) {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF202020))) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Find music on your phone", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text("Allow audio access to show songs stored on this device.")
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { permissionLauncher.launch(permission) }) { Text("Allow access") }
                    }
                }
            } else if (tracks.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color(0xFF1ED760))
                        Spacer(Modifier.height(8.dp))
                        Text("No local tracks found", fontWeight = FontWeight.Bold)
                        Text("Add audio files to your device, then tap refresh.", color = Color.LightGray)
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(tracks, key = { it.id }) { track ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                selected = track
                                onPlay(track)
                                playing = true
                            }.padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.background(Color(0xFF252525), RoundedCornerShape(8.dp)).padding(12.dp)
                            ) { Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color(0xFF1ED760)) }
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                            }
                            if (selected?.id == track.id && playing) {
                                Icon(Icons.Default.Pause, contentDescription = "Playing", tint = Color(0xFF1ED760))
                            }
                        }
                    }
                }
            }

            selected?.let { track ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF252525))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("NOW PLAYING", color = Color(0xFF1ED760), style = MaterialTheme.typography.labelSmall)
                            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                            Text(track.artist, color = Color.LightGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = {
                            onToggle()
                            playing = !playing
                        }) {
                            Icon(
                                if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (playing) "Pause" else "Play",
                                tint = Color(0xFF1ED760)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
