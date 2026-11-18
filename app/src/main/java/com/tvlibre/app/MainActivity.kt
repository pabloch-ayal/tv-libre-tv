package com.tvlibre.app

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val BgDeep = Color(0xFF0A0E1A)
private val CardDark = Color(0xFF131B2E)
private val CardFocus = Color(0xFF1E293B)
private val AccentCyan = Color(0xFF06B6D4)
private val AccentPurple = Color(0xFF7C3AED)
private val AccentOrange = Color(0xFFFF5722)

data class TrackOption(
    val label: String,
    val group: Tracks.Group?,
    val trackIndex: Int,
    val isOffOption: Boolean = false
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            TvLibreAndroidTvApp()
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun TvLibreAndroidTvApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("tv_libre_tv_prefs", Context.MODE_PRIVATE) }

    var activeSection by remember { mutableStateOf("TV") }
    var searchQuery by remember { mutableStateOf("") }
    var showSearchBar by remember { mutableStateOf(false) }
    var showSourceDialog by remember { mutableStateOf(false) }
    var showTrackDialog by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Cargando catálogo...") }

    var channels by remember { mutableStateOf<List<TvChannel>>(emptyList()) }
    var movies by remember { mutableStateOf<List<VodItem>>(emptyList()) }
    var series by remember { mutableStateOf<List<VodItem>>(emptyList()) }
    var matches by remember { mutableStateOf<List<MatchEvent>>(emptyList()) }
    var favoriteUrls by remember {
        mutableStateOf(prefs.getStringSet("fav_urls", emptySet()) ?: emptySet())
    }

    var currentStreamUrl by remember { mutableStateOf("") }
    var currentStreamTitle by remember { mutableStateOf("Selecciona un canal o evento") }
    var selectedVodDetail by remember { mutableStateOf<VodItem?>(null) }
    var detailedPlot by remember { mutableStateOf("") }

    var savedSources by remember {
        mutableStateOf(
            prefs.getString("sources_json", "[]")?.let { raw ->
                val arr = JSONArray(raw)
                List(arr.length()) { arr.getString(it) }
            } ?: emptyList()
        )
    }

    var clockText by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            clockText = fmt.format(Date())
            delay(15000)
        }
    }

    val exoPlayer = remember {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("VLC/3.0.18 LibVLC/3.0.18")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
        val renderers = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        ExoPlayer.Builder(context, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .build().apply {
                playWhenReady = true
            }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    fun playStream(url: String, title: String, fullScreenNow: Boolean = false) {
        if (url.isBlank()) return
        currentStreamUrl = url
        currentStreamTitle = title
        if (fullScreenNow) isFullscreen = true
        exoPlayer.setMediaItem(MediaItem.fromUri(url))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    fun toggleFavorite(url: String) {
        val updated = favoriteUrls.toMutableSet()
        if (url in updated) updated.remove(url) else updated.add(url)
        favoriteUrls = updated
        prefs.edit().putStringSet("fav_urls", updated).apply()
    }

    suspend fun reloadAllData(sourcesList: List<String> = savedSources) {
        statusText = "Sincronizando..."
        withContext(Dispatchers.IO) {
            val allCh = mutableListOf<TvChannel>()
            val allMov = mutableListOf<VodItem>()
            val allSer = mutableListOf<VodItem>()
            val allMat = mutableListOf<MatchEvent>()

            sourcesList.forEachIndexed { i, src ->
                try {
                    val parsed = parseUniversalSource(src, "Mi Lista ${i + 1}")
                    allCh.addAll(parsed.channels)
                    allMov.addAll(parsed.movies)
                    allSer.addAll(parsed.series)
                    allMat.addAll(parsed.todayMatches)
                } catch (_: Exception) {}
            }

            try {
                val baseJson = fetchUrlSmart("https://raw.githubusercontent.com/pabloch-ayal/tv-libre-apk/main/catalogo.json")
                val arr = JSONArray(baseJson)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val t = o.getString("title")
                    val c = o.optString("category", "General")
                    val u = o.getString("streamUrl")
                    val type = o.optString("type", "TV")
                    if (type == "VOD") {
                        allMov.add(VodItem("base_$i", t, c, u, "", "Película gratuita verificada."))
                    } else {
                        val ch = TvChannel("base_$i", t, c, u)
                        allCh.add(ch)
                        parseMatchFromChannel(ch)?.let { allMat.add(it) }
                    }
                }
            } catch (_: Exception) {}

            val cleanCh = allCh.distinctBy { it.streamUrl }
            val cleanMov = allMov.distinctBy { it.streamUrl }
            val cleanSer = allSer.distinctBy { it.title }
            val cleanMat = allMat.distinctBy { it.streamUrl }

            withContext(Dispatchers.Main) {
                channels = cleanCh
                movies = cleanMov
                series = cleanSer
                matches = cleanMat
                statusText = "${cleanCh.size} canales • ${cleanMat.size} partidos • ${cleanMov.size} cine"
                if (currentStreamUrl.isEmpty() && cleanCh.isNotEmpty()) {
                    playStream(cleanCh.first().streamUrl, cleanCh.first().title, false)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        reloadAllData()
    }

    BackHandler(enabled = showTrackDialog || isFullscreen || selectedVodDetail != null || showSearchBar) {
        when {
            showTrackDialog -> showTrackDialog = false
            isFullscreen -> isFullscreen = false
            selectedVodDetail != null -> selectedVodDetail = null
            showSearchBar -> {
                showSearchBar = false
                searchQuery = ""
            }
        }
    }

    // MODO PANTALLA COMPLETA SIN CUADRO AMARILLO Y CON CONTROLES DE SUBTÍTULOS / AUDIO / CALIDAD
    if (isFullscreen) {
        var showOsd by remember { mutableStateOf(true) }
        var isPaused by remember { mutableStateOf(false) }

        LaunchedEffect(showOsd, currentStreamUrl, showTrackDialog) {
            if (showOsd && !showTrackDialog) {
                delay(3500)
                showOsd = false
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusable()
                .clickable {
                    if (!showOsd) {
                        showOsd = true
                    } else {
                        if (exoPlayer.isPlaying) {
                            exoPlayer.pause()
                            isPaused = true
                        } else {
                            exoPlayer.play()
                            isPaused = false
                        }
                    }
                }
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        isFocusable = false
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (showOsd) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))))
                        .padding(horizontal = 28.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(
                            if (isPaused) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(34.dp)
                        )
                        Column {
                            Text(currentStreamTitle, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text("OK: Pausar/Reanudar  •  Atrás: Salir", color = Color.LightGray, fontSize = 12.sp)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TvFocusButton("Subtítulos / Audio / Calidad", AccentPurple) {
                            showOsd = true
                            showTrackDialog = true
                        }
                        TvFocusButton("Salir (Atrás)", CardFocus) { isFullscreen = false }
                    }
                }
            }
        }

        // Diálogo selector de Subtítulos, Audio y Resolución
        if (showTrackDialog) {
            val tracks = exoPlayer.currentTracks
            val subtitleOptions = remember(tracks) {
                val list = mutableListOf(TrackOption("Desactivar subtítulos", null, -1, isOffOption = true))
                for (group in tracks.groups) {
                    if (group.type == C.TRACK_TYPE_TEXT) {
                        for (i in 0 until group.length) {
                            val fmt = group.getTrackFormat(i)
                            val lang = fmt.language ?: "Subtítulo ${i + 1}"
                            val label = fmt.label ?: lang.uppercase()
                            list.add(TrackOption(label, group, i))
                        }
                    }
                }
                list
            }
            val audioOptions = remember(tracks) {
                val list = mutableListOf<TrackOption>()
                for (group in tracks.groups) {
                    if (group.type == C.TRACK_TYPE_AUDIO) {
                        for (i in 0 until group.length) {
                            val fmt = group.getTrackFormat(i)
                            val lang = fmt.language ?: "Pista ${i + 1}"
                            val label = fmt.label ?: lang.uppercase()
                            list.add(TrackOption(label, group, i))
                        }
                    }
                }
                list
            }
            val videoOptions = remember(tracks) {
                val list = mutableListOf(TrackOption("Automática (Mejor disponible)", null, -1, isOffOption = true))
                for (group in tracks.groups) {
                    if (group.type == C.TRACK_TYPE_VIDEO) {
                        for (i in 0 until group.length) {
                            val fmt = group.getTrackFormat(i)
                            if (fmt.height > 0) {
                                list.add(TrackOption("${fmt.height}p (${fmt.width}x${fmt.height})", group, i))
                            }
                        }
                    }
                }
                list
            }

            Dialog(onDismissRequest = { showTrackDialog = false }) {
                Column(
                    modifier = Modifier
                        .width(560.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(CardDark)
                        .border(2.dp, AccentCyan, RoundedCornerShape(16.dp))
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Opciones de Reproducción", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)

                    Text("Subtítulos:", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(subtitleOptions) { opt ->
                            TvFocusButton(opt.label, CardFocus) {
                                val builder = exoPlayer.trackSelectionParameters.buildUpon()
                                if (opt.isOffOption) {
                                    builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                } else if (opt.group != null) {
                                    builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                    builder.setOverrideForType(TrackSelectionOverride(opt.group.mediaTrackGroup, opt.trackIndex))
                                }
                                exoPlayer.trackSelectionParameters = builder.build()
                                showTrackDialog = false
                            }
                        }
                    }

                    if (audioOptions.isNotEmpty()) {
                        Text("Idioma / Audio:", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(audioOptions) { opt ->
                                TvFocusButton(opt.label, CardFocus) {
                                    if (opt.group != null) {
                                        val builder = exoPlayer.trackSelectionParameters.buildUpon()
                                        builder.setOverrideForType(TrackSelectionOverride(opt.group.mediaTrackGroup, opt.trackIndex))
                                        exoPlayer.trackSelectionParameters = builder.build()
                                    }
                                    showTrackDialog = false
                                }
                            }
                        }
                    }

                    Text("Resolución / Calidad:", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(videoOptions) { opt ->
                            TvFocusButton(opt.label, CardFocus) {
                                val builder = exoPlayer.trackSelectionParameters.buildUpon()
                                if (opt.isOffOption) {
                                    builder.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                                } else if (opt.group != null) {
                                    builder.setOverrideForType(TrackSelectionOverride(opt.group.mediaTrackGroup, opt.trackIndex))
                                }
                                exoPlayer.trackSelectionParameters = builder.build()
                                showTrackDialog = false
                            }
                        }
                    }

                    TvFocusButton("Cerrar", AccentOrange) { showTrackDialog = false }
                }
            }
        }
        return
    }

    // INTERFAZ PRINCIPAL CON BANNER DEGRADADO AZUL -> MORADO -> FUCSIA
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0F172A), BgDeep)))
            .padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF1E3A8A), Color(0xFF7C3AED), Color(0xFFEC4899))
                    )
                )
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LiveTv, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("TV LIBRE", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, maxLines = 1, softWrap = false)
                    Spacer(Modifier.width(12.dp))
                    Text(statusText, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 1, softWrap = false)
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvBannerIcon(Icons.Default.Search, "Buscar") { showSearchBar = !showSearchBar }
                    TvBannerIcon(Icons.Default.History, "Favoritos") { activeSection = "FAVORITOS" }
                    TvBannerIcon(Icons.Default.PersonAdd, "Agregar Lista") { showSourceDialog = true }
                    TvBannerIcon(Icons.Default.Refresh, "Actualizar") { scope.launch { reloadAllData() } }
                    Text(
                        clockText,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            val tabs = listOf(
                "TV" to "TV",
                "PARTIDOS" to "P. HOY (${matches.size})",
                "PELICULAS" to "PELÍCULAS",
                "SERIES" to "SERIES",
                "FAVORITOS" to "FAVORITOS",
                "ADD_M3U" to "+ M3U",
                "ADD_XTREAM" to "+ XTREAM"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                tabs.forEach { (key, label) ->
                    val isActionPill = key == "ADD_M3U" || key == "ADD_XTREAM"
                    val selected = activeSection == key
                    var focused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                when {
                                    focused -> Color.White
                                    selected -> Color.White.copy(alpha = 0.32f)
                                    isActionPill -> Color.Black.copy(alpha = 0.28f)
                                    else -> Color.White.copy(alpha = 0.14f)
                                }
                            )
                            .border(
                                width = if (focused || selected) 1.5.dp else if (isActionPill) 1.dp else 0.dp,
                                color = if (isActionPill && !focused) Color.White.copy(alpha = 0.55f) else Color.White,
                                shape = RoundedCornerShape(50)
                            )
                            .onFocusChanged { focused = it.isFocused }
                            .focusable()
                            .clickable {
                                if (isActionPill) showSourceDialog = true
                                else activeSection = key
                            }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            label,
                            color = if (focused) Color(0xFF1E3A8A) else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        if (showSearchBar) {
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Buscar canal, partido, película o serie...", color = Color.Gray) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = AccentCyan
                )
            )
        }

        Spacer(Modifier.height(12.dp))

        when (activeSection) {
            "TV" -> {
                val filteredCh = channels.filter {
                    searchQuery.isBlank() || it.title.contains(searchQuery, true) || it.category.contains(searchQuery, true)
                }
                Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Column(
                        modifier = Modifier
                            .weight(0.36f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(14.dp))
                            .background(CardDark)
                            .padding(12.dp)
                    ) {
                        Text("Lista de canales (${filteredCh.size})", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            itemsIndexed(filteredCh) { idx, ch ->
                                var focused by remember { mutableStateOf(false) }
                                val isPlaying = ch.streamUrl == currentStreamUrl
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (focused) AccentCyan.copy(alpha = 0.25f) else if (isPlaying) CardFocus else Color(0xFF0E1525))
                                        .border(if (focused) 2.dp else 0.dp, Color.White, RoundedCornerShape(10.dp))
                                        .onFocusChanged { focused = it.isFocused }
                                        .focusable()
                                        .clickable {
                                            if (isPlaying) isFullscreen = true
                                            else playStream(ch.streamUrl, ch.title, false)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${idx + 1}", color = AccentCyan, fontWeight = FontWeight.Bold, modifier = Modifier.width(34.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(ch.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(ch.category, color = Color.Gray, fontSize = 11.sp, maxLines = 1)
                                    }
                                    if (ch.streamUrl in favoriteUrls) {
                                        Icon(Icons.Default.Favorite, null, tint = AccentOrange, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(0.64f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Black)
                    ) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            AndroidView(
                                factory = { ctx ->
                                    PlayerView(ctx).apply {
                                        player = exoPlayer
                                        useController = false
                                        isFocusable = false
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CardDark)
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                currentStreamTitle,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (currentStreamUrl.isNotEmpty()) {
                                    TvFocusButton(
                                        if (currentStreamUrl in favoriteUrls) "Quitar Fav" else "+ Favorito",
                                        CardFocus
                                    ) { toggleFavorite(currentStreamUrl) }
                                }
                                TvFocusButton("Pantalla Completa", AccentOrange) { isFullscreen = true }
                            }
                        }
                    }
                }
            }

            "PARTIDOS" -> {
                var selectedLeague by remember { mutableStateOf("Todos") }
                val leagues = remember(matches) { listOf("Todos") + matches.map { it.league }.distinct() }
                val filteredMatches = matches.filter {
                    (selectedLeague == "Todos" || it.league == selectedLeague) &&
                    (searchQuery.isBlank() || it.title.contains(searchQuery, true))
                }

                Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Column(
                        modifier = Modifier
                            .weight(0.26f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(14.dp))
                            .background(CardDark)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Partidos de Hoy", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        leagues.forEach { lg ->
                            var focused by remember { mutableStateOf(false) }
                            val active = selectedLeague == lg
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (focused) AccentCyan.copy(alpha = 0.25f) else if (active) AccentPurple else Color(0xFF0E1525))
                                    .border(if (focused) 2.dp else 0.dp, Color.White, RoundedCornerShape(10.dp))
                                    .onFocusChanged { focused = it.isFocused }
                                    .focusable()
                                    .clickable { selectedLeague = lg }
                                    .padding(12.dp)
                            ) {
                                Text(lg, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(0.74f).fillMaxHeight()) {
                        if (filteredMatches.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(CardDark),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No se detectaron partidos 'vs' en las listas actuales.\nAgrega tu lista IPTV en '+ M3U' o '+ XTREAM'.",
                                    color = Color.LightGray,
                                    fontSize = 15.sp
                                )
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(filteredMatches) { m ->
                                    var focused by remember { mutableStateOf(false) }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (focused) CardFocus else CardDark)
                                            .border(if (focused) 2.dp else 1.dp, if (focused) AccentCyan else Color(0xFF1E293B), RoundedCornerShape(12.dp))
                                            .onFocusChanged { focused = it.isFocused }
                                            .focusable()
                                            .clickable { playStream(m.streamUrl, m.title, true) }
                                            .padding(horizontal = 18.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(0.24f)) {
                                            Text(m.league, color = AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Text("Toca OK para ver", color = Color.Gray, fontSize = 11.sp)
                                        }
                                        Text(m.teamA, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(0.28f))
                                        Column(modifier = Modifier.weight(0.20f), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(m.timeLabel, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                                            Text("EN DIRECTO", color = AccentOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Text(m.teamB, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(0.28f))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "PELICULAS", "SERIES" -> {
                val baseVod = if (activeSection == "PELICULAS") movies else series
                var selectedGenre by remember { mutableStateOf("Todo") }
                val genres = remember(baseVod) { listOf("Todo") + baseVod.map { it.category }.distinct().take(12) }
                val filteredVod = baseVod.filter {
                    (selectedGenre == "Todo" || it.category == selectedGenre) &&
                    (searchQuery.isBlank() || it.title.contains(searchQuery, true))
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(genres) { g ->
                            var focused by remember { mutableStateOf(false) }
                            val active = selectedGenre == g
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (focused) AccentCyan.copy(alpha = 0.25f) else if (active) AccentPurple else CardDark)
                                    .border(if (focused) 2.dp else 0.dp, Color.White, RoundedCornerShape(10.dp))
                                    .onFocusChanged { focused = it.isFocused }
                                    .focusable()
                                    .clickable { selectedGenre = g }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(g, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    if (filteredVod.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(CardDark),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No hay títulos aquí todavía.\nConecta tu lista en '+ M3U' o '+ XTREAM'.", color = Color.LightGray)
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(5),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(filteredVod) { item ->
                                var focused by remember { mutableStateOf(false) }
                                Column(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (focused) CardFocus else CardDark)
                                        .border(if (focused) 2.dp else 1.dp, if (focused) AccentCyan else Color(0xFF1E293B), RoundedCornerShape(12.dp))
                                        .onFocusChanged { focused = it.isFocused }
                                        .focusable()
                                        .clickable {
                                            selectedVodDetail = item
                                            detailedPlot = item.plot
                                            scope.launch(Dispatchers.IO) {
                                                val full = fetchVodPlotXtream(item)
                                                withContext(Dispatchers.Main) { detailedPlot = full }
                                            }
                                        }
                                        .padding(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(145.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Brush.verticalGradient(listOf(AccentPurple.copy(alpha = 0.45f), Color(0xFF0E1525)))),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            if (item.isSeries) Icons.Default.VideoLibrary else Icons.Default.Movie,
                                            contentDescription = null,
                                            tint = AccentCyan,
                                            modifier = Modifier.size(44.dp)
                                        )
                                        if (item.rating.isNotEmpty()) {
                                            Text(
                                                "★ ${item.rating}",
                                                color = Color.Yellow,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Text(item.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(item.category, color = AccentCyan, fontSize = 11.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }

            "FAVORITOS" -> {
                val favChannels = channels.filter { it.streamUrl in favoriteUrls }
                Column(modifier = Modifier.fillMaxSize()) {
                    Text("Mis Canales Favoritos (${favChannels.size})", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(12.dp))
                    if (favChannels.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(CardDark),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Aún no tienes favoritos. En la pestaña TV pulsa '+ Favorito'.", color = Color.LightGray)
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(favChannels) { ch ->
                                var focused by remember { mutableStateOf(false) }
                                Column(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (focused) AccentCyan.copy(alpha = 0.25f) else CardDark)
                                        .border(if (focused) 2.dp else 0.dp, Color.White, RoundedCornerShape(12.dp))
                                        .onFocusChanged { focused = it.isFocused }
                                        .focusable()
                                        .clickable { playStream(ch.streamUrl, ch.title, true) }
                                        .padding(14.dp)
                                ) {
                                    Text(ch.title, color = Color.White, fontWeight = FontWeight.Bold)
                                    Text(ch.category, color = AccentCyan, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedVodDetail?.let { vod ->
        Dialog(onDismissRequest = { selectedVodDetail = null }) {
            Column(
                modifier = Modifier
                    .width(560.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardDark)
                    .border(2.dp, AccentCyan, RoundedCornerShape(16.dp))
                    .padding(24.dp)
            ) {
                Text(vod.title, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                Spacer(Modifier.height(4.dp))
                Text("${vod.category} ${if (vod.year.isNotEmpty()) "• ${vod.year}" else ""}", color = AccentCyan, fontSize = 13.sp)
                Spacer(Modifier.height(14.dp))
                Text("Sinopsis:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Text(detailedPlot, color = Color.LightGray, fontSize = 14.sp, maxLines = 8, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (vod.streamUrl.isNotEmpty()) {
                        TvFocusButton("▶ Reproducir Ahora", AccentOrange) {
                            val u = vod.streamUrl
                            val t = vod.title
                            selectedVodDetail = null
                            playStream(u, t, true)
                        }
                    }
                    TvFocusButton("Cerrar", CardFocus) { selectedVodDetail = null }
                }
            }
        }
    }

    if (showSourceDialog) {
        var inputUrl by remember { mutableStateOf("") }
        var xHost by remember { mutableStateOf("") }
        var xUser by remember { mutableStateOf("") }
        var xPass by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { showSourceDialog = false }) {
            Column(
                modifier = Modifier
                    .width(560.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardDark)
                    .border(2.dp, AccentPurple, RoundedCornerShape(16.dp))
                    .padding(22.dp)
            ) {
                Text("Agregar Lista M3U o Cuenta Xtream Codes", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(10.dp))
                Text("Opción 1: Enlace M3U / M3U8 (HTTP o HTTPS)", color = AccentCyan, fontSize = 13.sp)
                OutlinedTextField(
                    value = inputUrl,
                    onValueChange = { inputUrl = it },
                    placeholder = { Text("http://... o https://.../lista.m3u", color = Color.Gray) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
                Spacer(Modifier.height(12.dp))
                Text("Opción 2: Servidor Xtream Codes (Películas con Sinopsis y Partidos)", color = AccentCyan, fontSize = 13.sp)
                OutlinedTextField(
                    value = xHost,
                    onValueChange = { xHost = it },
                    placeholder = { Text("Servidor (ej. http://iptv.servidor.com:8080)", color = Color.Gray) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = xUser,
                        onValueChange = { xUser = it },
                        placeholder = { Text("Usuario", color = Color.Gray) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                    OutlinedTextField(
                        value = xPass,
                        onValueChange = { xPass = it },
                        placeholder = { Text("Contraseña", color = Color.Gray) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvFocusButton("Guardar y Cargar", AccentCyan) {
                        val nextList = savedSources.toMutableList()
                        if (inputUrl.isNotBlank()) nextList.add(inputUrl.trim())
                        if (xHost.isNotBlank() && xUser.isNotBlank() && xPass.isNotBlank()) {
                            nextList.add("${xHost.trim()}|${xUser.trim()}|${xPass.trim()}")
                        }
                        savedSources = nextList
                        val arr = JSONArray()
                        nextList.forEach { arr.put(it) }
                        prefs.edit().putString("sources_json", arr.toString()).apply()
                        showSourceDialog = false
                        scope.launch { reloadAllData(nextList) }
                    }
                    TvFocusButton("Borrar listas", AccentOrange) {
                        savedSources = emptyList()
                        prefs.edit().putString("sources_json", "[]").apply()
                        showSourceDialog = false
                        scope.launch { reloadAllData(emptyList()) }
                    }
                    TvFocusButton("Cerrar", CardFocus) { showSourceDialog = false }
                }
            }
        }
    }
}

@Composable
fun TvFocusButton(text: String, baseColor: Color, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (focused) baseColor.copy(alpha = 0.95f) else baseColor.copy(alpha = 0.72f))
            .border(if (focused) 2.dp else 1.dp, if (focused) AccentCyan else Color.White.copy(alpha = 0.22f), RoundedCornerShape(50))
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, softWrap = false)
    }
}

@Composable
fun TvBannerIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.18f))
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = desc, tint = if (focused) Color(0xFF1E3A8A) else Color.White, modifier = Modifier.size(19.dp))
    }
}
