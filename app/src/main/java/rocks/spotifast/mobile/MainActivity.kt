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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay

data class LocalTrack(val id: Long, val title: String, val artist: String, val album: String, val uri: Uri)

private val NeonGreen = Color(0xFFB7FF3C)
private val NeonPurple = Color(0xFF9B7BFF)
private val AppBackground = Color(0xFF090B10)
private val PanelBackground = Color(0xFF151923)
private val MutedText = Color(0xFF9AA3B5)

private enum class LibraryTab(val label: String) { Songs("Songs"), Albums("Albums"), Artists("Artists"), Playlists("Playlists") }

class MainActivity : ComponentActivity() {
    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player = ExoPlayer.Builder(this).build()
        setContent {
            SpotifastTheme {
                SpotifastScreen(
                    tracksProvider = { loadTracks() },
                    player = player!!,
                    onPlay = { track ->
                        val p = player ?: return@SpotifastScreen
                        val queue = loadTracks()
                        val startIndex = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
                        p.setMediaItems(queue.map { MediaItem.Builder().setMediaId(it.id.toString()).setUri(it.uri).build() }, startIndex, 0L)
                        p.prepare()
                        p.play()
                    },
                    onToggle = {
                        val p = player ?: return@SpotifastScreen
                        if (p.isPlaying) p.pause() else p.play()
                    }
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
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        contentResolver.query(
            collection, projection, selection, null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val title = cursor.getString(titleCol) ?: "Unknown title"
                val artist = cursor.getString(artistCol) ?: "Unknown artist"
                val album = cursor.getString(albumCol)?.takeUnless { it == "<unknown>" } ?: "Unknown album"
                tracks += LocalTrack(id, title, artist, album, ContentUris.withAppendedId(collection, id))
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
        primary = NeonGreen,
        onPrimary = Color(0xFF101407),
        secondary = NeonPurple,
        background = AppBackground,
        surface = PanelBackground,
        onBackground = Color(0xFFF4F6FC),
        onSurface = Color(0xFFF4F6FC)
    )
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
private fun SpotifastScreen(
    tracksProvider: () -> List<LocalTrack>,
    player: ExoPlayer,
    onPlay: (LocalTrack) -> Unit,
    onToggle: () -> Unit
) {
    var tracks by remember { mutableStateOf(emptyList<LocalTrack>()) }
    var selected by remember { mutableStateOf<LocalTrack?>(null) }
    var permissionGranted by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var favorites by remember { mutableStateOf(setOf<Long>()) }
    var showFavorites by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(LibraryTab.Songs) }
    var expandedPlayer by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var shuffleEnabled by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableIntStateOf(0) }
    val context = androidx.compose.ui.platform.LocalContext.current

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
        permissionGranted = ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
        if (permissionGranted) tracks = tracksProvider()
    }

    val matchingTracks = tracks.filter { track ->
        val matchesSearch = track.title.contains(searchQuery, ignoreCase = true) ||
            track.artist.contains(searchQuery, ignoreCase = true) ||
            track.album.contains(searchQuery, ignoreCase = true)
        matchesSearch && (!showFavorites || favorites.contains(track.id))
    }
    LaunchedEffect(playing, selected?.id) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.coerceAtLeast(0L)
            delay(500)
        }
    }

    val visibleTracks = when (selectedTab) {
        LibraryTab.Songs -> matchingTracks
        LibraryTab.Albums -> matchingTracks.distinctBy { it.album }
        LibraryTab.Artists -> matchingTracks.distinctBy { it.artist }
        LibraryTab.Playlists -> matchingTracks
    }

    Surface(modifier = Modifier.fillMaxSize(), color = AppBackground) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            Brush.linearGradient(listOf(NeonGreen, Color(0xFF53D6B4))),
                            RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Headphones, contentDescription = null, tint = Color(0xFF11150A), modifier = Modifier.size(27.dp))
                }
                Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                    Text("SPOTIFAST", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
                    Text("YOUR SOUND. YOUR RULES.", color = MutedText, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                }
                IconButton(onClick = {
                    if (permissionGranted) tracks = tracksProvider() else permissionLauncher.launch(permission)
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh library", tint = NeonGreen)
                }
            }

            Spacer(Modifier.height(22.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = PanelBackground)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(listOf(Color(0xFF202B24), Color(0xFF171827), PanelBackground)))
                        .padding(18.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.background(NeonGreen.copy(alpha = 0.13f), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("PLAYER MODE", color = NeonGreen, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            }
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Default.GraphicEq, contentDescription = null, tint = NeonPurple, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.height(18.dp))
                        Text(
                            selected?.title ?: "Your next favorite track.",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            selected?.artist ?: "Pick a song from your offline library",
                            color = MutedText,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(18.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf(12, 22, 32, 18, 38, 24, 14, 30, 20, 36, 16, 26).forEachIndexed { index, barHeight ->
                                    Box(
                                        Modifier
                                            .width(4.dp)
                                            .height((if (playing) barHeight else (barHeight / 2).coerceAtLeast(7)).dp)
                                            .background(
                                                if (index % 3 == 0) NeonPurple else NeonGreen,
                                                CircleShape
                                            )
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(if (playing) "PLAYING" else "READY", color = MutedText, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                            }
                            IconButton(
                                onClick = {
                                    if (selected != null) {
                                        onToggle()
                                        playing = !playing
                                    }
                                },
                                enabled = selected != null,
                                modifier = Modifier
                                    .size(54.dp)
                                    .background(NeonGreen, CircleShape)
                            ) {
                                Icon(
                                    if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (playing) "Pause" else "Play",
                                    tint = Color(0xFF11150A),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Your library", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("${tracks.size} LOCAL TRACKS", color = MutedText, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                }
            }
            ScrollableTabRow(selectedTabIndex = selectedTab.ordinal, containerColor = AppBackground, contentColor = NeonGreen, edgePadding = 0.dp) {
                LibraryTab.values().forEach { tab ->
                    Tab(selected = selectedTab == tab, onClick = {
                        selectedTab = tab
                        showFavorites = tab == LibraryTab.Playlists
                    }, text = { Text(tab.label) })
                }
            }
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search songs or artists", color = MutedText) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = NeonGreen) },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonGreen,
                    unfocusedBorderColor = Color(0xFF303747),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = NeonGreen
                )
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !showFavorites,
                    onClick = { showFavorites = false },
                    label = { Text("All tracks") }
                )
                FilterChip(
                    selected = showFavorites,
                    onClick = { showFavorites = true },
                    label = { Text("Favorites  •  ${favorites.size}") }
                )
            }
            Spacer(Modifier.height(8.dp))

            if (!permissionGranted) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = PanelBackground)
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Unlock your local library", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text("Allow audio access to find music already stored on this device. Spotifast can play these files offline.", color = MutedText)
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = { permissionLauncher.launch(permission) },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color(0xFF11150A))
                        ) { Text("Allow music access", fontWeight = FontWeight.Bold) }
                    }
                }
            } else if (visibleTracks.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(34.dp))
                        Spacer(Modifier.height(10.dp))
                        Text(
                            if (showFavorites && tracks.isNotEmpty()) "No favorites yet" else "No matching tracks",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            if (showFavorites && tracks.isNotEmpty()) "Tap the heart beside a song to save it here." else "Add audio files to your phone or try another search.",
                            color = MutedText,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(visibleTracks, key = { it.id }) { track ->
                        val isSelected = selected?.id == track.id
                        val isFavorite = favorites.contains(track.id)
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable {
                                selected = track
                                onPlay(track)
                                playing = true
                            },
                            shape = RoundedCornerShape(17.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) Color(0xFF202A22) else PanelBackground
                            )
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .background(
                                            if (isSelected) NeonGreen.copy(alpha = 0.16f) else Color(0xFF252B39),
                                            RoundedCornerShape(13.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        if (isSelected && playing) Icons.Default.GraphicEq else Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = if (isSelected) NeonGreen else NeonPurple
                                    )
                                }
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text(
                                        track.title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        track.artist,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MutedText,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                IconButton(onClick = {
                                    favorites = if (isFavorite) favorites - track.id else favorites + track.id
                                }) {
                                    Icon(
                                        if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = if (isFavorite) "Remove favorite" else "Add favorite",
                                        tint = if (isFavorite) NeonGreen else MutedText
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }

            if (selected != null) {
                Column(
                    Modifier.fillMaxWidth().background(Color(0xFF11151D)).clickable { expandedPlayer = true }
                ) {
                    Box(Modifier.fillMaxWidth().height(2.dp).background(Color(0xFF303746))) {
                        Box(Modifier.fillMaxWidth(if (duration > 0L) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f).height(2.dp).background(NeonGreen))
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Album, contentDescription = null, tint = NeonPurple, modifier = Modifier.size(34.dp))
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Text(selected!!.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                            Text(selected!!.artist, color = MutedText, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                        IconButton(onClick = { onToggle(); playing = !playing }) {
                            Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play or pause", tint = NeonGreen)
                        }
                        IconButton(onClick = { if (player.hasNextMediaItem()) player.seekToNextMediaItem() }) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White)
                        }
                    }
                }
            }
        }
    }

    if (expandedPlayer) {
        ModalBottomSheet(onDismissRequest = { expandedPlayer = false }, containerColor = PanelBackground) {
            selected?.let { track ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(20.dp))
                    Box(Modifier.size(220.dp).background(Brush.linearGradient(listOf(NeonPurple, NeonGreen.copy(alpha = .55f), PanelBackground)), RoundedCornerShape(28.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Album, contentDescription = null, tint = Color.White, modifier = Modifier.size(94.dp))
                    }
                    Spacer(Modifier.height(22.dp))
                    Text(track.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artist + " • " + track.album, color = MutedText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(18.dp))
                    Slider(value = if (duration > 0L) position.toFloat().coerceIn(0f, duration.toFloat()) else 0f, onValueChange = { position = it.toLong() }, onValueChangeFinished = { player.seekTo(position) }, valueRange = 0f..duration.coerceAtLeast(1L).toFloat(), colors = SliderDefaults.colors(thumbColor = NeonGreen, activeTrackColor = NeonGreen))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatTime(position), color = MutedText, style = MaterialTheme.typography.labelSmall)
                        Text(formatTime(duration), color = MutedText, style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { shuffleEnabled = !shuffleEnabled; player.shuffleModeEnabled = shuffleEnabled }) { Icon(Icons.Default.Shuffle, "Shuffle", tint = if (shuffleEnabled) NeonGreen else MutedText) }
                        IconButton(onClick = { if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem() }) { Icon(Icons.Default.SkipPrevious, "Previous", modifier = Modifier.size(34.dp)) }
                        IconButton(onClick = { onToggle(); playing = !playing }, modifier = Modifier.size(64.dp).background(NeonGreen, CircleShape)) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play or pause", tint = Color(0xFF11150A), modifier = Modifier.size(38.dp)) }
                        IconButton(onClick = { if (player.hasNextMediaItem()) player.seekToNextMediaItem() }) { Icon(Icons.Default.SkipNext, "Next", modifier = Modifier.size(34.dp)) }
                        IconButton(onClick = { repeatMode = (repeatMode + 1) % 3; player.repeatMode = when (repeatMode) { 1 -> Player.REPEAT_MODE_ONE; 2 -> Player.REPEAT_MODE_ALL; else -> Player.REPEAT_MODE_OFF } }) { Icon(Icons.Default.Repeat, "Repeat", tint = if (repeatMode == 0) MutedText else NeonGreen) }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { expandedPlayer = false }) { Icon(Icons.Default.QueueMusic, null); Spacer(Modifier.width(8.dp)); Text("Queue is the device library order") }
                }
            }
        }
    }
}