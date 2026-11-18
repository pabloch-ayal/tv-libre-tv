package com.tvlibre.app

import android.content.Context
import android.os.Build
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
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.media3.common.Player
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
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Paleta Pizarra Índigo Profundo + Banner Degradado
private val BgIndigoDeep = Color(0xFF090D18)
private val PanelSlate = Color(0xFF141B33)
private val CardFocus = Color(0xFF1E293B)
private val AccentCyan = Color(0xFF06B6D4)
private val AccentPurple = Color(0xFF7C3AED)
private val AccentPink = Color(0xFFEC4899)
private val AccentOrange = Color(0xFFFF5722)

data class TrackOption(
    val label: String,
    val group: Tracks.Group?,
    val trackIndex: Int,
    val isOffOption: Boolean = false
)

fun isAdultContent(title: String, category: String): Boolean {
    val combined = "$title $category".lowercase()
    val keywords = listOf("xxx", "+18", "18+", "adult", "adulto", "porn", "erotic", "erotico", "playboy", "venus", "hustler", "sex", "penthouse", "brazzers", "private")
    return keywords.any { it in combined }
}

fun isSportsChannel(title: String, category: String): Boolean {
    val combined = "$title $category".lowercase()
    val keywords = listOf("deporte", "sport", "espn", "fox sport", "tnt sport", "tyc", "directv", "dsports", "gol", "liga", "futbol", "fútbol", "bein", "dazn", "nba", "ufc", "f1", "formula 1", "claro sport", "movistar deporte", "l1 max", "liga 1")
    return keywords.any { it in combined }
}


fun resolveChannelLogo(title: String, rawLogo: String): String {
    if (rawLogo.startsWith("http")) return rawLogo
    val t = title.lowercase()
    return when {
        "america" in t || "américa" in t -> "https://upload.wikimedia.org/wikipedia/commons/thumb/3/34/Am%C3%A9rica_Televisi%C3%B3n_logo_2022.svg/240px-Am%C3%A9rica_Televisi%C3%B3n_logo_2022.svg.png"
        "atv" in t -> "https://upload.wikimedia.org/wikipedia/commons/thumb/4/4e/ATV_Per%C3%BA_logo_2014.svg/240px-ATV_Per%C3%BA_logo_2014.svg.png"
        "latina" in t -> "https://upload.wikimedia.org/wikipedia/commons/thumb/8/87/Latina_Televisi%C3%B3n_logo.svg/240px-Latina_Televisi%C3%B3n_logo.svg.png"
        "panamericana" in t -> "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a2/Panamericana_Televisi%C3%B3n_logo.svg/240px-Panamericana_Televisi%C3%B3n_logo.svg.png"
        "willax" in t -> "https://upload.wikimedia.org/wikipedia/commons/thumb/1/1d/Willax_Televisi%C3%B3n_logo.svg/240px-Willax_Televisi%C3%B3n_logo.svg.png"
        "tv peru" in t || "tv perú" in t -> "https://upload.wikimedia.org/wikipedia/commons/thumb/2/23/TV_Per%C3%BA_logo_2019.svg/240px-TV_Per%C3%BA_logo_2019.svg.png"
        "golperu" in t || "gol peru" in t -> "https://upload.wikimedia.org/wikipedia/commons/thumb/5/53/GolPer%C3%BA_logo.svg/240px-GolPer%C3%BA_logo.svg.png"
        "l1 max" in t || "liga 1" in t -> "https://upload.wikimedia.org/wikipedia/commons/thumb/4/45/L1_MAX_logo.png/240px-L1_MAX_logo.png"
        "espn" in t -> "https://upload.wikimedia.org/wikipedia/commons/thumb/2/2f/ESPN_wordmark.svg/240px-ESPN_wordmark.svg.png"
        "fox sport" in t -> "https://upload.wikimedia.org/wikipedia/commons/thumb/0/0c/FOX_Sports_logo.svg/240px-FOX_Sports_logo.svg.png"
        "dsports" in t || "directv" in t -> "https://upload.wikimedia.org/wikipedia/commons/thumb/9/9e/DSports_logo.svg/240px-DSports_logo.svg.png"
        "tnt sport" in t -> "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d3/TNT_Sports_2021_logo.svg/240px-TNT_Sports_2021_logo.svg.png"
        "tyc" in t -> "https://upload.wikimedia.org/wikipedia/commons/thumb/9/91/TyC_Sports_logo.svg/240px-TyC_Sports_logo.svg.png"
        else -> ""
    }
}

val BadgePalette = listOf(
    Color(0xFFF59E0B), // Ámbar / Naranja (como AT)
    Color(0xFF10B981), // Esmeralda / Verde (como AS)
    Color(0xFF3B82F6), // Azul brillante
    Color(0xFF8B5CF6), // Violeta
    Color(0xFFEC4899), // Rosa / Magenta
    Color(0xFF06B6D4), // Cian
    Color(0xFFEF4444)  // Coral / Rojo
)

@Composable
fun ChannelLogoBadge(title: String, logoUrl: String) {
    val cleanName = remember(title) { title.replace(Regex("\\([^)]*\\)|\\[[^\\]]*\\]"), "").trim() }
    val initials = remember(cleanName) {
        val words = cleanName.split(" ").filter { it.isNotEmpty() }
        when {
            words.size >= 2 -> (words[0].take(1) + words[1].take(1)).uppercase()
            words.size == 1 -> words[0].take(2).uppercase()
            else -> "TV"
        }
    }
    val badgeColor = remember(cleanName) {
        val idx = kotlin.math.abs(cleanName.hashCode()) % BadgePalette.size
        BadgePalette[idx]
    }
    var logoLoaded by remember(logoUrl) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (logoLoaded) Color.White.copy(alpha = 0.94f) else badgeColor),
        contentAlignment = Alignment.Center
    ) {
        if (!logoLoaded) {
            Text(initials, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, maxLines = 1)
        }
        if (logoUrl.startsWith("http")) {
            AsyncImage(
                model = logoUrl,
                contentDescription = title,
                contentScale = ContentScale.Fit,
                onSuccess = { logoLoaded = true },
                onError = { logoLoaded = false },
                modifier = Modifier.fillMaxSize().padding(if (logoLoaded) 3.dp else 0.dp)
            )
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.decorView.defaultFocusHighlightEnabled = false
        }
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
    var showPinSetupDialog by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }
    var isCurrentVod by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Cargando catálogo...") }

    // Control Parental (PIN de 4 dígitos)
    var parentalPin by remember { mutableStateOf(prefs.getString("parental_pin", "") ?: "") }
    var pendingLockedAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    var channels by remember { mutableStateOf<List<TvChannel>>(emptyList()) }
    var movies by remember { mutableStateOf<List<VodItem>>(emptyList()) }
    var series by remember { mutableStateOf<List<VodItem>>(emptyList()) }
    var matches by remember { mutableStateOf<List<MatchEvent>>(emptyList()) }
    var favoriteUrls by remember {
        mutableStateOf(prefs.getStringSet("fav_urls", emptySet()) ?: emptySet())
    }
    var historyList by remember {
        mutableStateOf(
            prefs.getString("history_json", "[]")?.let { raw ->
                val arr = JSONArray(raw)
                List(arr.length()) {
                    val o = arr.getJSONObject(it)
                    TvChannel("hist_$it", o.getString("title"), o.optString("category", "Película / Serie"), o.getString("url"))
                }.filter { it.category != "Canal TV" && it.category != "Visto recientemente" }
            } ?: emptyList()
        )
    }

    var currentStreamUrl by remember { mutableStateOf("") }
    var currentStreamTitle by remember { mutableStateOf("Selecciona un canal o evento") }
    var streamOfflineMessage by remember { mutableStateOf<String?>(null) }
    var triedUrlsForCurrent by remember { mutableStateOf(setOf<String>()) }
    var hiddenChannelUrls by remember {
        mutableStateOf(prefs.getStringSet("hidden_urls", emptySet()) ?: emptySet())
    }
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

    val httpDataSourceFactory = remember {
        DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10000)
            .setReadTimeoutMs(10000)
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

    fun executeProtected(title: String, category: String, action: () -> Unit) {
        if (parentalPin.isNotEmpty() && isAdultContent(title, category)) {
            pendingLockedAction = action
        } else {
            action()
        }
    }

    fun playStream(
        url: String,
        title: String,
        openFullscreen: Boolean = false,
        isVod: Boolean = false,
        userAgent: String = "",
        referer: String = "",
        resetTried: Boolean = true
    ) {
        if (url.isBlank()) return
        currentStreamUrl = url
        currentStreamTitle = title
        streamOfflineMessage = null
        if (resetTried) triedUrlsForCurrent = setOf(url)

        val headers = mutableMapOf<String, String>()
        if (referer.isNotBlank()) headers["Referer"] = referer
        val effectiveUa = userAgent.ifBlank { "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36" }
        httpDataSourceFactory.setUserAgent(effectiveUa)
        httpDataSourceFactory.setDefaultRequestProperties(headers)
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
                    val savedLastUrl = prefs.getString("last_live_url", "") ?: ""
                    val savedLastCh = cleanCh.firstOrNull { it.streamUrl == savedLastUrl && !isAdultContent(it.title, it.category) }
                    val targetCh = savedLastCh ?: cleanCh.firstOrNull { !isAdultContent(it.title, it.category) }
                    if (targetCh != null) {
                        playStream(targetCh.streamUrl, targetCh.title, false, false, targetCh.userAgent, targetCh.referer)
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        reloadAllData()
    }

    BackHandler(enabled = showTrackDialog || pendingLockedAction != null || showPinSetupDialog || isFullscreen || selectedVodDetail != null || showSearchBar) {
        when {
            showTrackDialog -> showTrackDialog = false
            pendingLockedAction != null -> pendingLockedAction = null
            showPinSetupDialog -> showPinSetupDialog = false
            isFullscreen -> isFullscreen = false
            selectedVodDetail != null -> selectedVodDetail = null
            showSearchBar -> {
                showSearchBar = false
                searchQuery = ""
            }
        }
    }

    // 1. MODO PANTALLA COMPLETA: TV EN VIVO 100% LIMPIA vs PELÍCULA/SERIE CON BARRA DE PROGRESO Y CONTROLES VOD
    if (isFullscreen) {
        var showOsd by remember { mutableStateOf(true) }
        var isPaused by remember { mutableStateOf(false) }
        var currentPosMs by remember { mutableLongStateOf(0L) }
        var totalDurMs by remember { mutableLongStateOf(0L) }
        val playPauseFocus = remember { FocusRequester() }

        LaunchedEffect(isFullscreen, isCurrentVod) {
            while (isFullscreen) {
                currentPosMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                totalDurMs = exoPlayer.duration.coerceAtLeast(0L)
                isPaused = !exoPlayer.isPlaying
                delay(500)
            }
        }

        LaunchedEffect(showOsd, currentStreamUrl, showTrackDialog, isPaused) {
            if (showOsd && !showTrackDialog && !isPaused) {
                delay(if (isCurrentVod) 4500 else 2500)
                showOsd = false
            }
        }

        LaunchedEffect(showOsd, isCurrentVod) {
            if (showOsd && isCurrentVod && !showTrackDialog) {
                try {
                    delay(120)
                    playPauseFocus.requestFocus()
                } catch (_: Exception) {}
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        isFocusable = false
                        isFocusableInTouchMode = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            defaultFocusHighlightEnabled = false
                        }
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (!isCurrentVod || !showOsd) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .focusable()
                        .clickable {
                            if (!isCurrentVod) {
                                showOsd = !showOsd
                            } else {
                                showOsd = true
                            }
                        }
                )
            }

            if (showOsd) {
                if (!isCurrentVod) {
                    // TV EN VIVO: Totalmente limpia, cero cápsulas ni botones flotantes (sale solo con botón Atrás del control)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))))
                            .padding(horizontal = 28.dp, vertical = 18.dp)
                    ) {
                        Column {
                            Text(currentStreamTitle, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text("EN VIVO • Presiona Atrás en tu control remoto para salir", color = AccentCyan, fontSize = 12.sp)
                        }
                    }
                } else {
                    // PELÍCULAS Y SERIES: Barra de tiempo + Retroceder (-15s) + Pausar/Reanudar + Adelantar (+15s) + Subtítulos/Audio
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f))))
                            .padding(horizontal = 32.dp, vertical = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(currentStreamTitle, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1)

                        val progress = if (totalDurMs > 0L) (currentPosMs.toFloat() / totalDurMs.toFloat()).coerceIn(0f, 1f) else 0f
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(formatDurationMs(currentPosMs), color = AccentCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(6.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.25f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(progress)
                                        .fillMaxHeight()
                                        .background(Brush.horizontalGradient(listOf(AccentCyan, AccentPurple)))
                                )
                            }
                            Text(formatDurationMs(totalDurMs), color = Color.LightGray, fontSize = 13.sp)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                TvFocusButton("⏪ -15s", CardFocus) {
                                    showOsd = true
                                    exoPlayer.seekTo((exoPlayer.currentPosition - 15000L).coerceAtLeast(0L))
                                }
                                Box(modifier = Modifier.focusRequester(playPauseFocus)) {
                                    TvFocusButton(if (isPaused) "▶ Reanudar" else "⏸ Pausar", AccentCyan) {
                                        showOsd = true
                                        if (exoPlayer.isPlaying) {
                                            exoPlayer.pause()
                                            isPaused = true
                                        } else {
                                            exoPlayer.play()
                                            isPaused = false
                                        }
                                    }
                                }
                                TvFocusButton("⏩ +15s", CardFocus) {
                                    showOsd = true
                                    val maxPos = if (totalDurMs > 0L) totalDurMs else Long.MAX_VALUE
                                    exoPlayer.seekTo((exoPlayer.currentPosition + 15000L).coerceAtMost(maxPos))
                                }
                            }

                            TvFocusButton("💬 Subtítulos / Audio / Calidad", AccentPurple) {
                                showOsd = true
                                showTrackDialog = true
                            }
                        }
                    }
                }
            }
        }

        // Menú funcional de Subtítulos, Audio (Español Latino / Original) y Resolución
        if (showTrackDialog) {
            val tracks = exoPlayer.currentTracks
            val firstOptionFocus = remember { FocusRequester() }

            LaunchedEffect(Unit) {
                try {
                    delay(120)
                    firstOptionFocus.requestFocus()
                } catch (_: Exception) {}
            }

            val subtitleOptions = remember(tracks) {
                val list = mutableListOf(TrackOption("Desactivar subtítulos", null, -1, isOffOption = true))
                var idx = 1
                for (group in tracks.groups) {
                    if (group.type == C.TRACK_TYPE_TEXT) {
                        for (i in 0 until group.length) {
                            val fmt = group.getTrackFormat(i)
                            val rawLang = (fmt.language ?: "").lowercase()
                            val friendly = when {
                                "es" in rawLang || "spa" in rawLang -> "Español"
                                "en" in rawLang || "eng" in rawLang -> "Inglés"
                                rawLang.isNotEmpty() -> rawLang.uppercase()
                                else -> "Subtítulo $idx"
                            }
                            val label = if (!fmt.label.isNullOrBlank()) "${fmt.label} ($friendly)" else friendly
                            list.add(TrackOption(label, group, i))
                            idx++
                        }
                    }
                }
                list
            }

            val audioOptions = remember(tracks) {
                val list = mutableListOf<TrackOption>()
                var idx = 1
                for (group in tracks.groups) {
                    if (group.type == C.TRACK_TYPE_AUDIO) {
                        for (i in 0 until group.length) {
                            val fmt = group.getTrackFormat(i)
                            val rawLang = (fmt.language ?: "").lowercase()
                            val friendly = when {
                                "es" in rawLang || "spa" in rawLang || "lat" in rawLang -> "Español Latino"
                                "en" in rawLang || "eng" in rawLang -> "Inglés / Original"
                                rawLang.isNotEmpty() -> rawLang.uppercase()
                                else -> "Audio $idx"
                            }
                            val label = if (!fmt.label.isNullOrBlank()) "${fmt.label} ($friendly)" else friendly
                            list.add(TrackOption(label, group, i))
                            idx++
                        }
                    }
                }
                list
            }

            val videoOptions = remember(tracks) {
                val list = mutableListOf(TrackOption("Automática (HD / FHD)", null, -1, isOffOption = true))
                for (group in tracks.groups) {
                    if (group.type == C.TRACK_TYPE_VIDEO) {
                        for (i in 0 until group.length) {
                            val fmt = group.getTrackFormat(i)
                            if (fmt.height > 0) {
                                list.add(TrackOption("${fmt.height}p", group, i))
                            }
                        }
                    }
                }
                list
            }

            Dialog(onDismissRequest = { showTrackDialog = false }) {
                Column(
                    modifier = Modifier
                        .width(600.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(PanelSlate)
                        .border(2.dp, AccentCyan, RoundedCornerShape(16.dp))
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Subtítulos, Audio y Calidad", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)

                    Text("Subtítulos:", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(subtitleOptions) { index, opt ->
                            Box(modifier = if (index == 0) Modifier.focusRequester(firstOptionFocus) else Modifier) {
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
                    }

                    Text("Audio / Idioma:", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    if (audioOptions.isEmpty()) {
                        Text("Pista única integrada en este video", color = Color.Gray, fontSize = 12.sp)
                    } else {
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

                    Text("Resolución:", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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

                    TvFocusButton("Cerrar (Atrás)", AccentOrange) { showTrackDialog = false }
                }
            }
        }
        return
    }


    LaunchedEffect(currentStreamUrl) {
        if (currentStreamUrl.isBlank()) return@LaunchedEffect
        kotlinx.coroutines.delay(7000L)
        val state = exoPlayer.playbackState
        if (state != Player.STATE_READY || !exoPlayer.isPlaying) {
            val baseName = currentStreamTitle.replace(Regex("\\([^)]*\\)|\\[[^\\]]*\\]"), "").trim().lowercase()
            val alt = channels.firstOrNull { ch ->
                ch.streamUrl !in triedUrlsForCurrent &&
                ch.streamUrl !in hiddenChannelUrls &&
                ch.title.replace(Regex("\\([^)]*\\)|\\[[^\\]]*\\]"), "").trim().lowercase() == baseName
            }
            if (alt != null) {
                triedUrlsForCurrent = triedUrlsForCurrent + alt.streamUrl
                playStream(alt.streamUrl, alt.title, isFullscreen, false, alt.userAgent, alt.referer, resetTried = false)
            } else {
                streamOfflineMessage = "Señal no disponible o bloqueada por región/servidor"
            }
        } else {
            streamOfflineMessage = null
        }
    }

    // 2. INTERFAZ PRINCIPAL: BANNER DE BORDE A BORDE + LIENZO CONTINUO SIN CAJAS
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgIndigoDeep)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(Color(0xFF1E3A8A), AccentPurple, AccentPink)))
                .padding(horizontal = 28.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LiveTv, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("TV LIBRE", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp, maxLines = 1, softWrap = false)
                    Spacer(Modifier.width(12.dp))
                    Text("• César IA", color = Color.White.copy(alpha = 0.78f), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, softWrap = false)
                    Spacer(Modifier.width(12.dp))
                    Text(statusText, color = Color.White.copy(alpha = 0.88f), fontSize = 12.sp, maxLines = 1, softWrap = false)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvBannerIcon(Icons.Default.Search, "Buscar") { showSearchBar = !showSearchBar }
                    TvBannerIcon(Icons.Default.History, "Continuar viendo") { activeSection = "HISTORIAL" }
                    TvBannerIcon(if (parentalPin.isNotEmpty()) Icons.Default.Lock else Icons.Default.LockOpen, "Control Parental PIN") { showPinSetupDialog = true }
                    TvBannerIcon(Icons.Default.Person, "Perfil y Listas") { showSourceDialog = true }
                    TvBannerIcon(Icons.Default.Refresh, "Actualizar") { scope.launch { reloadAllData() } }
                    Text(clockText, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 6.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            val tabs = listOf(
                "TV" to "TV",
                "PARTIDOS" to "PARTIDOS DE HOY (${matches.size})",
                "DEPORTES" to "DEPORTES",
                "PELICULAS" to "PELÍCULAS",
                "SERIES" to "SERIES",
                "FAVORITOS" to "FAVORITOS",
                "ADD_SOURCE" to "+ M3U / XTREAM"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                tabs.forEach { (key, label) ->
                    val isActionPill = key == "ADD_SOURCE"
                    val selected = activeSection == key
                    var focused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(when {
                                focused -> Color.White
                                selected -> Color.White.copy(alpha = 0.34f)
                                isActionPill -> Color.Black.copy(alpha = 0.28f)
                                else -> Color.White.copy(alpha = 0.16f)
                            })
                            .border(if (focused) 1.5.dp else 0.dp, Color.White, RoundedCornerShape(50))
                            .onFocusChanged { focused = it.isFocused }
                            .focusable()
                            .clickable { if (isActionPill) showSourceDialog = true else activeSection = key }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(label, color = if (focused) Color(0xFF1E3A8A) else Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
        Column(modifier = Modifier.fillMaxSize().background(BgIndigoDeep)) {
            if (showSearchBar) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Buscar canal, partido, deporte, película o serie...", color = Color.Gray) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = AccentCyan)
                )
            }
            when (activeSection) {
                "TV", "DEPORTES" -> {
                    val baseList = if (activeSection == "DEPORTES") channels.filter { isSportsChannel(it.title, it.category) } else channels
                    val filteredCh = baseList.filter {
                        it.streamUrl !in hiddenChannelUrls &&
                        (searchQuery.isBlank() || it.title.contains(searchQuery, true) || it.category.contains(searchQuery, true))
                    }
                    val sectionLabel = if (activeSection == "DEPORTES") "Canales de Deportes (${filteredCh.size})" else "Lista de canales (${filteredCh.size})"
                    Row(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.weight(0.36f).fillMaxHeight().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 10.dp)) {
                            Text(sectionLabel, color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 8.dp))
                            Spacer(Modifier.height(8.dp))
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                itemsIndexed(filteredCh) { idx, ch ->
                                    var focused by remember { mutableStateOf(false) }
                                    val isPlaying = ch.streamUrl == currentStreamUrl
                                    val locked = parentalPin.isNotEmpty() && isAdultContent(ch.title, ch.category)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(when {
                                                focused -> Brush.horizontalGradient(listOf(AccentPurple.copy(alpha = 0.72f), AccentCyan.copy(alpha = 0.50f)))
                                                isPlaying -> Brush.horizontalGradient(listOf(PanelSlate, PanelSlate))
                                                else -> Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                                            })
                                            .border(if (focused) 1.5.dp else 0.dp, Color.White, RoundedCornerShape(10.dp))
                                            .onFocusChanged { focused = it.isFocused }
                                            .focusable()
                                            .clickable {
                                                executeProtected(ch.title, ch.category) {
                                                    if (isPlaying) isFullscreen = true else playStream(ch.streamUrl, ch.title, false, false, ch.userAgent, ch.referer)
                                                }
                                            }
                                            .padding(horizontal = 10.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("${idx + 1}", color = AccentCyan, fontWeight = FontWeight.Bold, modifier = Modifier.width(30.dp))
                                        ChannelLogoBadge(ch.title, ch.logoUrl)
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(if (locked) "🔒 ${ch.title}" else ch.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(ch.category, color = Color.LightGray.copy(alpha = 0.65f), fontSize = 11.sp, maxLines = 1)
                                        }
                                        if (ch.streamUrl in favoriteUrls) Icon(Icons.Default.Favorite, null, tint = AccentPink, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                        Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.08f)))
                        Column(modifier = Modifier.weight(0.64f).fillMaxHeight()) {
                            Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Color.Black)) {
                                AndroidView(
                                    factory = { ctx -> PlayerView(ctx).apply { player = exoPlayer; useController = false; isFocusable = false; isFocusableInTouchMode = false } },
                                    modifier = Modifier.fillMaxSize()
                                )
                                streamOfflineMessage?.let { msg ->
                                    Column(
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(Color(0xE6090D18))
                                            .padding(horizontal = 24.dp, vertical = 18.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(msg, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        TvFocusButton("Ocultar canal caído", AccentPink) {
                                            val updated = hiddenChannelUrls + currentStreamUrl
                                            hiddenChannelUrls = updated
                                            prefs.edit().putStringSet("hidden_urls", updated).apply()
                                            streamOfflineMessage = null
                                            val nextCh = channels.firstOrNull { it.streamUrl !in updated && !isAdultContent(it.title, it.category) }
                                            if (nextCh != null) playStream(nextCh.streamUrl, nextCh.title, false, false, nextCh.userAgent, nextCh.referer)
                                        }
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().background(BgIndigoDeep).padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(currentStreamTitle, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    if (currentStreamUrl.isNotEmpty()) TvFocusButton(if (currentStreamUrl in favoriteUrls) "Quitar Fav" else "+ Favorito", PanelSlate) { toggleFavorite(currentStreamUrl) }
                                    TvFocusButton("Pantalla Completa", AccentOrange) { isFullscreen = true }
                                }
                            }
                        }
                    }
                }
                "PARTIDOS" -> {
                    var selectedLeague by remember { mutableStateOf("Todos") }
                    val leagues = remember(matches) { listOf("Todos") + matches.map { it.league }.distinct() }
                    val filteredMatches = matches.filter { (selectedLeague == "Todos" || it.league == selectedLeague) && (searchQuery.isBlank() || it.title.contains(searchQuery, true)) }
                    Row(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        Column(modifier = Modifier.weight(0.26f).fillMaxHeight().clip(RoundedCornerShape(14.dp)).background(PanelSlate).border(1.dp, AccentPurple.copy(alpha = 0.25f), RoundedCornerShape(14.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Partidos de Hoy", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            leagues.forEach { lg ->
                                var focused by remember { mutableStateOf(false) }
                                val active = selectedLeague == lg
                                Box(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                        .background(if (focused) AccentCyan.copy(alpha = 0.25f) else if (active) AccentPurple else Color(0xFF0E1428))
                                        .border(if (focused) 1.5.dp else 0.dp, Color.White, RoundedCornerShape(10.dp))
                                        .onFocusChanged { focused = it.isFocused }.focusable().clickable { selectedLeague = lg }.padding(12.dp)
                                ) { Text(lg, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                            }
                        }
                        Column(modifier = Modifier.weight(0.74f).fillMaxHeight()) {
                            if (filteredMatches.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(PanelSlate), contentAlignment = Alignment.Center) {
                                    Text("No se detectaron partidos 'vs' en las listas actuales.\nAgrega tu lista IPTV en '+ M3U / XTREAM'.", color = Color.LightGray, fontSize = 15.sp)
                                }
                            } else {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    items(filteredMatches) { m ->
                                        var focused by remember { mutableStateOf(false) }
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                                .background(if (focused) CardFocus else PanelSlate)
                                                .border(if (focused) 2.dp else 1.dp, if (focused) AccentCyan else AccentPurple.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                                                .onFocusChanged { focused = it.isFocused }.focusable().clickable { playStream(m.streamUrl, m.title, true, false) }
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
                    val filteredVod = baseVod.filter { (selectedGenre == "Todo" || it.category == selectedGenre) && (searchQuery.isBlank() || it.title.contains(searchQuery, true)) }
                    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 14.dp)) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(genres) { g ->
                                var focused by remember { mutableStateOf(false) }
                                val active = selectedGenre == g
                                Box(
                                    modifier = Modifier.clip(RoundedCornerShape(10.dp))
                                        .background(if (focused) AccentCyan.copy(alpha = 0.25f) else if (active) AccentPurple else PanelSlate)
                                        .border(if (focused) 1.5.dp else 0.dp, Color.White, RoundedCornerShape(10.dp))
                                        .onFocusChanged { focused = it.isFocused }.focusable().clickable { selectedGenre = g }.padding(horizontal = 14.dp, vertical = 8.dp)
                                ) { Text(g, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        if (filteredVod.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(PanelSlate), contentAlignment = Alignment.Center) {
                                Text("No hay títulos aquí todavía.\nConecta tu lista en '+ M3U / XTREAM'.", color = Color.LightGray)
                            }
                        } else {
                            LazyVerticalGrid(columns = GridCells.Fixed(5), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(filteredVod) { item ->
                                    var focused by remember { mutableStateOf(false) }
                                    val locked = parentalPin.isNotEmpty() && isAdultContent(item.title, item.category)
                                    Column(
                                        modifier = Modifier.clip(RoundedCornerShape(12.dp))
                                            .background(if (focused) CardFocus else PanelSlate)
                                            .border(if (focused) 2.dp else 1.dp, if (focused) AccentCyan else AccentPurple.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                                            .onFocusChanged { focused = it.isFocused }.focusable()
                                            .clickable {
                                                executeProtected(item.title, item.category) {
                                                    selectedVodDetail = item
                                                    detailedPlot = item.plot
                                                    scope.launch(Dispatchers.IO) { val full = fetchVodPlotXtream(item); withContext(Dispatchers.Main) { detailedPlot = full } }
                                                }
                                            }.padding(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().height(145.dp).clip(RoundedCornerShape(8.dp))
                                                .background(Brush.verticalGradient(listOf(AccentPurple.copy(alpha = 0.45f), Color(0xFF0E1428)))),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(if (locked) Icons.Default.Lock else if (item.isSeries) Icons.Default.VideoLibrary else Icons.Default.Movie, null, tint = if (locked) AccentPink else AccentCyan, modifier = Modifier.size(44.dp))
                                            if (!locked && item.posterUrl.startsWith("http")) {
                                                AsyncImage(model = item.posterUrl, contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                            }
                                            if (item.rating.isNotEmpty()) Text("★ ${item.rating}", color = Color.Yellow, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Text(if (locked) "🔒 ${item.title}" else item.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text(item.category, color = AccentCyan, fontSize = 11.sp, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
                "HISTORIAL" -> {
                    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 14.dp)) {
                        Text("Continuar viendo Películas y Series (${historyList.size})", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(Modifier.height(12.dp))
                        if (historyList.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(PanelSlate), contentAlignment = Alignment.Center) {
                                Text("Aquí aparecerán únicamente las películas y series que hayas visto recientemente.", color = Color.LightGray)
                            }
                        } else {
                            LazyVerticalGrid(columns = GridCells.Fixed(4), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(historyList) { item ->
                                    var focused by remember { mutableStateOf(false) }
                                    Column(
                                        modifier = Modifier.clip(RoundedCornerShape(12.dp))
                                            .background(if (focused) AccentCyan.copy(alpha = 0.25f) else PanelSlate)
                                            .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else AccentPurple.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                                            .onFocusChanged { focused = it.isFocused }.focusable()
                                            .clickable { executeProtected(item.title, item.category) { playStream(item.streamUrl, item.title, true, true) } }
                                            .padding(14.dp)
                                    ) {
                                        Text(item.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(item.category, color = AccentCyan, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                "FAVORITOS" -> {
                    val favChannels = channels.filter { it.streamUrl in favoriteUrls }
                    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 14.dp)) {
                        Text("Mis Canales Favoritos (${favChannels.size})", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(Modifier.height(12.dp))
                        if (favChannels.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(PanelSlate), contentAlignment = Alignment.Center) {
                                Text("Aún no tienes favoritos. En la pestaña TV pulsa '+ Favorito'.", color = Color.LightGray)
                            }
                        } else {
                            LazyVerticalGrid(columns = GridCells.Fixed(4), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(favChannels) { ch ->
                                    var focused by remember { mutableStateOf(false) }
                                    Column(
                                        modifier = Modifier.clip(RoundedCornerShape(12.dp))
                                            .background(if (focused) AccentCyan.copy(alpha = 0.25f) else PanelSlate)
                                            .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else AccentPurple.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                                            .onFocusChanged { focused = it.isFocused }.focusable()
                                            .clickable { executeProtected(ch.title, ch.category) { playStream(ch.streamUrl, ch.title, true, false) } }
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
    }
    selectedVodDetail?.let { vod ->
        Dialog(onDismissRequest = { selectedVodDetail = null }) {
            Column(modifier = Modifier.width(560.dp).clip(RoundedCornerShape(16.dp)).background(PanelSlate).border(2.dp, AccentCyan, RoundedCornerShape(16.dp)).padding(24.dp)) {
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
                            val u = vod.streamUrl; val t = vod.title; selectedVodDetail = null; playStream(u, t, true, true)
                        }
                    }
                    TvFocusButton("Cerrar", CardFocus) { selectedVodDetail = null }
                }
            }
        }
    }

    if (showPinSetupDialog) {
        var newPin by remember { mutableStateOf("") }
        Dialog(onDismissRequest = { showPinSetupDialog = false }) {
            Column(modifier = Modifier.width(460.dp).clip(RoundedCornerShape(16.dp)).background(PanelSlate).border(2.dp, AccentPink, RoundedCornerShape(16.dp)).padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Control Parental (PIN +18)", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
                Text(if (parentalPin.isEmpty()) "Protege canales y películas para adultos (+18 / XXX) con una clave de 4 dígitos." else "El bloqueo parental está ACTIVO. Ingresa una nueva clave de 4 dígitos o desactívalo.", color = Color.LightGray, fontSize = 13.sp)
                OutlinedTextField(value = newPin, onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) newPin = it }, placeholder = { Text("Ej. 1234", color = Color.Gray) }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvFocusButton("Guardar PIN", AccentCyan) { if (newPin.length == 4) { parentalPin = newPin; prefs.edit().putString("parental_pin", newPin).apply(); showPinSetupDialog = false } }
                    if (parentalPin.isNotEmpty()) TvFocusButton("Desactivar PIN", AccentOrange) { parentalPin = ""; prefs.edit().remove("parental_pin").apply(); showPinSetupDialog = false }
                    TvFocusButton("Cerrar", CardFocus) { showPinSetupDialog = false }
                }
            }
        }
    }

    pendingLockedAction?.let { unlockAction ->
        var enteredPin by remember { mutableStateOf("") }
        var pinError by remember { mutableStateOf(false) }
        Dialog(onDismissRequest = { pendingLockedAction = null }) {
            Column(modifier = Modifier.width(420.dp).clip(RoundedCornerShape(16.dp)).background(PanelSlate).border(2.dp, AccentPink, RoundedCornerShape(16.dp)).padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("🔒 Contenido Restringido (+18)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Ingresa tu PIN de 4 dígitos para desbloquear:", color = Color.LightGray, fontSize = 13.sp)
                OutlinedTextField(
                    value = enteredPin,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) { enteredPin = it; pinError = false; if (it == parentalPin) { pendingLockedAction = null; unlockAction() } } },
                    placeholder = { Text("••••", color = Color.Gray) }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
                if (pinError) Text("PIN incorrecto, intenta nuevamente.", color = AccentOrange, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvFocusButton("Desbloquear", AccentCyan) { if (enteredPin == parentalPin) { pendingLockedAction = null; unlockAction() } else pinError = true }
                    TvFocusButton("Cancelar", CardFocus) { pendingLockedAction = null }
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
            Column(modifier = Modifier.width(560.dp).clip(RoundedCornerShape(16.dp)).background(PanelSlate).border(2.dp, AccentPurple, RoundedCornerShape(16.dp)).padding(22.dp)) {
                Text("Agregar Lista M3U o Cuenta Xtream Codes", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(10.dp))
                Text("Opción 1: Enlace M3U / M3U8 (HTTP o HTTPS)", color = AccentCyan, fontSize = 13.sp)
                OutlinedTextField(value = inputUrl, onValueChange = { inputUrl = it }, placeholder = { Text("http://... o https://.../lista.m3u", color = Color.Gray) }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White))
                Spacer(Modifier.height(12.dp))
                Text("Opción 2: Servidor Xtream Codes (Películas con Sinopsis y Partidos)", color = AccentCyan, fontSize = 13.sp)
                OutlinedTextField(value = xHost, onValueChange = { xHost = it }, placeholder = { Text("Servidor (ej. http://iptv.servidor.com:8080)", color = Color.Gray) }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White))
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = xUser, onValueChange = { xUser = it }, placeholder = { Text("Usuario", color = Color.Gray) }, singleLine = true, modifier = Modifier.weight(1f), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White))
                    OutlinedTextField(value = xPass, onValueChange = { xPass = it }, placeholder = { Text("Contraseña", color = Color.Gray) }, singleLine = true, modifier = Modifier.weight(1f), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White))
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvFocusButton("Guardar y Cargar", AccentCyan) {
                        val nextList = savedSources.toMutableList()
                        if (inputUrl.isNotBlank()) nextList.add(inputUrl.trim())
                        if (xHost.isNotBlank() && xUser.isNotBlank() && xPass.isNotBlank()) nextList.add("${xHost.trim()}|${xUser.trim()}|${xPass.trim()}")
                        savedSources = nextList
                        val arr = JSONArray(); nextList.forEach { arr.put(it) }
                        prefs.edit().putString("sources_json", arr.toString()).apply()
                        showSourceDialog = false
                        scope.launch { reloadAllData(nextList) }
                    }
                    TvFocusButton("Borrar listas", AccentOrange) {
                        savedSources = emptyList(); prefs.edit().putString("sources_json", "[]").apply(); showSourceDialog = false; scope.launch { reloadAllData(emptyList()) }
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
        modifier = Modifier.clip(RoundedCornerShape(50))
            .background(if (focused) AccentCyan else baseColor)
            .border(if (focused) 1.5.dp else 0.dp, Color.White, RoundedCornerShape(50))
            .onFocusChanged { focused = it.isFocused }.focusable().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 8.dp)
    ) { Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, softWrap = false) }
}

@Composable
fun TvBannerIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier.size(36.dp).clip(CircleShape).background(if (focused) Color.White else Color.White.copy(alpha = 0.18f))
            .onFocusChanged { focused = it.isFocused }.focusable().clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Icon(icon, contentDescription = desc, tint = if (focused) Color(0xFF1E3A8A) else Color.White, modifier = Modifier.size(19.dp)) }
}

fun formatDurationMs(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSec = ms / 1000L; val h = totalSec / 3600L; val m = (totalSec % 3600L) / 60L; val s = totalSec % 60L
    return if (h > 0L) String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s) else String.format(Locale.getDefault(), "%02d:%02d", m, s)
}
