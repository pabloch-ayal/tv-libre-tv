package com.tvlibre.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

data class TvChannel(
    val id: String,
    val title: String,
    val category: String,
    val streamUrl: String,
    val logoUrl: String = "",
    val isLive: Boolean = true,
    val userAgent: String = "",
    val referer: String = ""
)

data class VodItem(
    val id: String,
    val title: String,
    val category: String,
    val streamUrl: String,
    val posterUrl: String = "",
    val plot: String = "Sin sinopsis disponible para este título.",
    val year: String = "",
    val rating: String = "",
    val isSeries: Boolean = false,
    val xtreamHost: String = "",
    val xtreamUser: String = "",
    val xtreamPass: String = "",
    val userAgent: String = "",
    val referer: String = ""
)

data class MatchEvent(
    val title: String,
    val teamA: String,
    val teamB: String,
    val timeLabel: String,
    val league: String,
    val streamUrl: String,
    val userAgent: String = "",
    val referer: String = ""
)

fun fetchUrlSmart(urlStr: String): String {
    var currentUrl = urlStr.trim()
    repeat(6) {
        val conn = URL(currentUrl).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = false
        conn.setRequestProperty("User-Agent", "VLC/3.0.18 LibVLC/3.0.18")
        conn.setRequestProperty("Accept", "*/*")
        conn.setRequestProperty("Accept-Encoding", "gzip, identity")
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        val status = conn.responseCode
        if (status in 301..308) {
            val loc = conn.getHeaderField("Location") ?: return ""
            currentUrl = URL(URL(currentUrl), loc).toString()
            conn.disconnect()
        } else {
            val stream = if ("gzip".equals(conn.contentEncoding, ignoreCase = true)) {
                GZIPInputStream(conn.inputStream)
            } else {
                conn.inputStream
            }
            return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }.removePrefix("\uFEFF")
        }
    }
    return ""
}

fun parseMatchFromChannel(ch: TvChannel): MatchEvent? {
    val text = "${ch.title} ${ch.category}"
    val vsRegex = Regex("""(.+?)\s+(?:vs\.?|v\.?|contra)\s+(.+)""", RegexOption.IGNORE_CASE)
    val match = vsRegex.find(ch.title) ?: return null

    val timeRegex = Regex("""\b([01]?\d|2[0-3]):[0-5]\d\b""")
    val timeFound = timeRegex.find(ch.title)?.value ?: "EN VIVO"

    val rawA = match.groupValues[1]
        .replace(timeRegex, "")
        .replace(Regex("""^[^a-zA-Z0-9áéíóúÁÉÍÓÚñÑ]+"""), "")
        .substringAfterLast(":")
        .substringAfterLast("|")
        .trim()

    val rawB = match.groupValues[2]
        .replace(Regex("""\[.*?\]|\(.*?\)"""), "")
        .trim()

    val lower = text.lowercase()
    val league = when {
        listOf("liga 1", "peru", "perú", "libertadores", "sudamericana", "eliminatorias", "conmebol").any { it in lower } -> "Sudamérica / Liga 1"
        listOf("champions", "laliga", "premier", "serie a", "bundesliga", "europa", "uefa").any { it in lower } -> "Fútbol Europeo"
        listOf("concacaf", "nations", "mls", "mx", "amistoso", "friendly", "fifa").any { it in lower } -> "Selecciones / Amistosos"
        listOf("nba", "ufc", "box", "f1", "tenis", "nfl").any { it in lower } -> "Otros Deportes"
        else -> ch.category.ifEmpty { "Evento Deportivo" }
    }

    if (rawA.length < 2 || rawB.length < 2) return null
    return MatchEvent(ch.title, rawA, rawB, timeFound, league, ch.streamUrl)
}

fun fetchVodPlotXtream(item: VodItem): String {
    if (item.xtreamHost.isEmpty() || item.id.isEmpty()) return item.plot
    return try {
        val action = if (item.isSeries) "get_series_info&series_id=${item.id}" else "get_vod_info&vod_id=${item.id}"
        val url = "${item.xtreamHost}/player_api.php?username=${item.xtreamUser}&password=${item.xtreamPass}&action=$action"
        val json = JSONObject(fetchUrlSmart(url))
        val info = json.optJSONObject("info") ?: return item.plot
        val plot = info.optString("plot", "").ifEmpty { info.optString("description", "") }
        val cast = info.optString("cast", "")
        val director = info.optString("director", "")
        buildString {
            if (plot.isNotEmpty()) append(plot) else append(item.plot)
            if (director.isNotEmpty()) append("\n\nDirector: $director")
            if (cast.isNotEmpty()) append("\nReparto: $cast")
        }
    } catch (_: Exception) {
        item.plot
    }
}

data class LoadedCatalog(
    val channels: List<TvChannel>,
    val movies: List<VodItem>,
    val series: List<VodItem>,
    val todayMatches: List<MatchEvent>
)

fun parseUniversalSource(
    rawInput: String,
    originName: String = "Lista"
): LoadedCatalog {
    val channels = mutableListOf<TvChannel>()
    val movies = mutableListOf<VodItem>()
    val series = mutableListOf<VodItem>()
    val matches = mutableListOf<MatchEvent>()

    val trimmedInput = rawInput.trim()

    // Caso 1: Credenciales Xtream Codes en formato HOST|USER|PASS o URL get.php
    val xtreamParts = if ("|" in trimmedInput && !trimmedInput.startsWith("#")) {
        trimmedInput.split("|").map { it.trim() }
    } else if ("get.php?" in trimmedInput && "username=" in trimmedInput) {
        val host = trimmedInput.substringBefore("/get.php")
        val user = Regex("""username=([^&]+)""").find(trimmedInput)?.groupValues?.getOrNull(1) ?: ""
        val pass = Regex("""password=([^&]+)""").find(trimmedInput)?.groupValues?.getOrNull(1) ?: ""
        if (user.isNotEmpty() && pass.isNotEmpty()) listOf(host, user, pass) else emptyList()
    } else emptyList()

    if (xtreamParts.size >= 3) {
        val host = xtreamParts[0].trimEnd('/')
        val user = xtreamParts[1]
        val pass = xtreamParts[2]

        // 1.1 Canales en Vivo y Partidos de Hoy
        try {
            val catMap = mutableMapOf<String, String>()
            val catArr = JSONArray(fetchUrlSmart("$host/player_api.php?username=$user&password=$pass&action=get_live_categories"))
            for (i in 0 until catArr.length()) {
                val c = catArr.getJSONObject(i)
                catMap[c.optString("category_id")] = c.optString("category_name", "General")
            }
            val liveArr = JSONArray(fetchUrlSmart("$host/player_api.php?username=$user&password=$pass&action=get_live_streams"))
            for (i in 0 until liveArr.length()) {
                val o = liveArr.getJSONObject(i)
                val id = o.optString("stream_id")
                val name = o.optString("name", "Canal $id")
                val catName = catMap[o.optString("category_id")] ?: "En Vivo"
                val icon = o.optString("stream_icon", "")
                val url = "$host/live/$user/$pass/$id.m3u8"
                val ch = TvChannel(id, name, catName, url, icon, true)
                channels.add(ch)
                parseMatchFromChannel(ch)?.let { matches.add(it) }
            }
        } catch (_: Exception) {}

        // 1.2 Películas VOD con carátula
        try {
            val vodCatMap = mutableMapOf<String, String>()
            val vCatArr = JSONArray(fetchUrlSmart("$host/player_api.php?username=$user&password=$pass&action=get_vod_categories"))
            for (i in 0 until vCatArr.length()) {
                val c = vCatArr.getJSONObject(i)
                vodCatMap[c.optString("category_id")] = c.optString("category_name", "Cine")
            }
            val vodArr = JSONArray(fetchUrlSmart("$host/player_api.php?username=$user&password=$pass&action=get_vod_streams"))
            for (i in 0 until vodArr.length()) {
                val o = vodArr.getJSONObject(i)
                val id = o.optString("stream_id")
                val name = o.optString("name", "Película $id")
                val ext = o.optString("container_extension", "mp4")
                val catName = vodCatMap[o.optString("category_id")] ?: "Películas"
                val icon = o.optString("stream_icon", "")
                val rating = o.optString("rating", "")
                val url = "$host/movie/$user/$pass/$id.$ext"
                movies.add(VodItem(id, name, catName, url, icon, "Cargando sinopsis...", "", rating, false, host, user, pass))
            }
        } catch (_: Exception) {}

        // 1.3 Series
        try {
            val serArr = JSONArray(fetchUrlSmart("$host/player_api.php?username=$user&password=$pass&action=get_series"))
            for (i in 0 until serArr.length()) {
                val o = serArr.getJSONObject(i)
                val id = o.optString("series_id")
                val name = o.optString("name", "Serie $id")
                val cover = o.optString("cover", "")
                val plot = o.optString("plot", "Serie disponible en catálogo.")
                val year = o.optString("releaseDate", "")
                val rating = o.optString("rating", "")
                series.add(VodItem(id, name, "Series", "", cover, plot, year, rating, true, host, user, pass))
            }
        } catch (_: Exception) {}

        return LoadedCatalog(channels, movies, series, matches)
    }

    // Caso 2: URL M3U directa o texto M3U
    val rawM3u = if (trimmedInput.startsWith("http://", true) || trimmedInput.startsWith("https://", true)) {
        fetchUrlSmart(trimmedInput)
    } else trimmedInput

    var curTitle = ""
    var curGroup = ""
    var curLogo = ""
    var curPlot = ""
    var idx = 1

    for (line in rawM3u.lines()) {
        val t = line.trim().removePrefix("\uFEFF")
        if (t.isEmpty()) continue
        if (t.startsWith("#EXTINF:", true)) {
            curGroup = Regex("""group-title="([^"]*)"""", RegexOption.IGNORE_CASE).find(t)?.groupValues?.getOrNull(1)?.trim() ?: ""
            curLogo = Regex("""tvg-logo="([^"]*)"""", RegexOption.IGNORE_CASE).find(t)?.groupValues?.getOrNull(1)?.trim() ?: ""
            curPlot = Regex("""(?:plot|description|tvg-desc)="([^"]*)"""", RegexOption.IGNORE_CASE).find(t)?.groupValues?.getOrNull(1)?.trim() ?: ""
            val afterComma = if ("," in t) t.substringAfterLast(",").trim() else ""
            val tvgName = Regex("""tvg-name="([^"]*)"""", RegexOption.IGNORE_CASE).find(t)?.groupValues?.getOrNull(1)?.trim() ?: ""
            curTitle = afterComma.ifEmpty { tvgName }
        } else if (t.startsWith("http://", true) || t.startsWith("https://", true)) {
            val title = curTitle.ifEmpty { "Canal $idx ($originName)" }
            idx++
            val groupLower = curGroup.lowercase()
            val urlLower = t.lowercase()
            val isSeries = listOf("/series/", "serie", "temporada", "s01", "cap ", "capitulo").any { it in groupLower || it in title.lowercase() }
            val isMovie = !isSeries && (listOf(".mp4", ".mkv", ".avi", "/movie/", "pelicula", "película", "cine", "vod").any { it in urlLower || it in groupLower })

            when {
                isSeries -> series.add(VodItem("$idx", title, curGroup.ifEmpty { "Series" }, t, curLogo, curPlot.ifEmpty { "Episodio de serie ($originName)." }, isSeries = true))
                isMovie -> movies.add(VodItem("$idx", title, curGroup.ifEmpty { "Películas" }, t, curLogo, curPlot.ifEmpty { "Película disponible en $originName." }, isSeries = false))
                else -> {
                    val ch = TvChannel("$idx", title, curGroup.ifEmpty { "TV en Vivo" }, t, curLogo, true)
                    channels.add(ch)
                    parseMatchFromChannel(ch)?.let { matches.add(it) }
                }
            }
            curTitle = ""
            curGroup = ""
            curLogo = ""
            curPlot = ""
        }
    }

    return LoadedCatalog(channels, movies, series, matches)
}
