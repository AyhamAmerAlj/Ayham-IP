package com.ayham.player.data

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class XtreamClient(private val ctx: Context, val acc: Account) {

    val base: String = acc.server.trim().trimEnd('/').let {
        if (it.startsWith("http://") || it.startsWith("https://")) it else "http://$it"
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun api(action: String? = null, extra: String = ""): String {
        val a = if (action != null) "&action=$action" else ""
        return "$base/player_api.php?username=${enc(acc.user)}&password=${enc(acc.pass)}$a$extra"
    }

    private fun md5(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun http(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 90000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android TV) AyhamPlayer/1.0")
        try {
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private suspend fun get(url: String, cacheName: String? = null, ttlMs: Long = 6 * 3600_000L): String =
        withContext(Dispatchers.IO) {
            if (cacheName == null) {
                http(url)
            } else {
                val f = File(ctx.cacheDir, "x_" + md5(acc.server + acc.user + cacheName))
                if (f.exists() && System.currentTimeMillis() - f.lastModified() < ttlMs) {
                    f.readText()
                } else {
                    val t = http(url)
                    f.writeText(t)
                    t
                }
            }
        }

    private fun arr(text: String): JSONArray = try {
        JSONArray(text)
    } catch (e: Exception) {
        JSONArray()
    }

    suspend fun login(): JSONObject {
        val o = JSONObject(get(api()))
        val ui = o.optJSONObject("user_info") ?: throw IOException("Invalid server response")
        if (ui.optString("auth") != "1") throw IOException("Authentication failed (wrong user / password)")
        return ui
    }

    suspend fun categories(kind: String): List<Category> {
        val action = when (kind) {
            "live" -> "get_live_categories"
            "movie" -> "get_vod_categories"
            else -> "get_series_categories"
        }
        val a = arr(get(api(action), "cat_$kind"))
        val out = ArrayList<Category>(a.length())
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            out.add(Category(o.optString("category_id"), o.optString("category_name")))
        }
        return out
    }

    suspend fun items(kind: String): List<Item> {
        val action = when (kind) {
            "live" -> "get_live_streams"
            "movie" -> "get_vod_streams"
            else -> "get_series"
        }
        val a = arr(get(api(action), "items_$kind"))
        val out = ArrayList<Item>(a.length())
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            val name = o.optString("name").trim()
            val item = when (kind) {
                "live" -> Item(
                    type = "live",
                    id = o.optString("stream_id"),
                    name = name,
                    poster = o.optString("stream_icon"),
                    categoryId = o.optString("category_id"),
                    epgId = o.optString("epg_channel_id"),
                    added = o.optString("added").toLongOrNull() ?: 0L,
                    norm = Norm.n(name)
                )
                "movie" -> Item(
                    type = "movie",
                    id = o.optString("stream_id"),
                    name = name,
                    poster = o.optString("stream_icon"),
                    ext = o.optString("container_extension").ifBlank { "mp4" },
                    categoryId = o.optString("category_id"),
                    rating = o.optString("rating"),
                    added = o.optString("added").toLongOrNull() ?: 0L,
                    norm = Norm.n(name)
                )
                else -> Item(
                    type = "series",
                    id = o.optString("series_id"),
                    name = name,
                    poster = o.optString("cover"),
                    categoryId = o.optString("category_id"),
                    rating = o.optString("rating"),
                    added = o.optString("last_modified").toLongOrNull() ?: 0L,
                    norm = Norm.n(name)
                )
            }
            if (item.id.isNotBlank() && item.name.isNotBlank()) out.add(item)
        }
        return out
    }

    private fun firstOf(a: JSONArray?): String = if (a != null && a.length() > 0) a.optString(0) else ""

    suspend fun vodInfo(id: String): Details {
        val o = JSONObject(get(api("get_vod_info", "&vod_id=$id")))
        val info = o.optJSONObject("info") ?: JSONObject()
        val year = info.optString("releasedate").take(4).ifBlank { info.optString("year") }
        return Details(
            plot = info.optString("plot").ifBlank { info.optString("description") },
            year = year,
            genre = info.optString("genre"),
            cast = info.optString("cast").ifBlank { info.optString("actors") },
            backdrop = firstOf(info.optJSONArray("backdrop_path")),
            duration = info.optString("duration"),
            rating = info.optString("rating")
        )
    }

    suspend fun seriesInfo(id: String): Details {
        val o = JSONObject(get(api("get_series_info", "&series_id=$id")))
        val info = o.optJSONObject("info") ?: JSONObject()
        val eps = ArrayList<Episode>()

        fun addEpisodes(a: JSONArray, defaultSeason: Int) {
            for (i in 0 until a.length()) {
                val e = a.optJSONObject(i) ?: continue
                val ei = e.optJSONObject("info")
                val num = e.optString("episode_num").toIntOrNull() ?: (i + 1)
                eps.add(
                    Episode(
                        id = e.optString("id"),
                        title = e.optString("title").ifBlank { "Episode $num" },
                        season = e.optString("season").toIntOrNull() ?: defaultSeason,
                        num = num,
                        ext = e.optString("container_extension").ifBlank { "mp4" },
                        plot = ei?.optString("plot") ?: "",
                        image = ei?.optString("movie_image") ?: "",
                        durationSecs = ei?.optString("duration_secs")?.toIntOrNull() ?: 0
                    )
                )
            }
        }

        val e = o.opt("episodes")
        if (e is JSONObject) {
            val keys = e.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val a = e.optJSONArray(k) ?: continue
                addEpisodes(a, k.toIntOrNull() ?: 1)
            }
        } else if (e is JSONArray) {
            for (i in 0 until e.length()) {
                val a = e.optJSONArray(i) ?: continue
                addEpisodes(a, i + 1)
            }
        }
        eps.sortWith(compareBy({ it.season }, { it.num }))

        val year = info.optString("releaseDate").take(4).ifBlank { info.optString("release_date").take(4) }
        return Details(
            plot = info.optString("plot"),
            year = year,
            genre = info.optString("genre"),
            cast = info.optString("cast"),
            backdrop = firstOf(info.optJSONArray("backdrop_path")),
            rating = info.optString("rating"),
            episodes = eps
        )
    }

    /** Returns up to [limit] "HH:mm  Title" lines for the channel (now, next). */
    suspend fun shortEpg(streamId: String, limit: Int = 2): List<String> {
        val o = JSONObject(get(api("get_short_epg", "&stream_id=$streamId&limit=$limit")))
        val a = o.optJSONArray("epg_listings") ?: return emptyList()
        val fmt = SimpleDateFormat("HH:mm", Locale.US)
        val out = ArrayList<String>()
        for (i in 0 until a.length()) {
            val e = a.optJSONObject(i) ?: continue
            val title = try {
                String(Base64.decode(e.optString("title"), Base64.DEFAULT), Charsets.UTF_8)
            } catch (ex: Exception) {
                e.optString("title")
            }
            val ts = e.optString("start_timestamp").toLongOrNull()
            val time = if (ts != null) fmt.format(Date(ts * 1000)) else ""
            if (title.isNotBlank()) out.add(if (time.isNotEmpty()) "$time  $title" else title)
        }
        return out
    }

    fun liveUrl(id: String, ext: String): String = "$base/live/${enc(acc.user)}/${enc(acc.pass)}/$id.$ext"
    fun movieUrl(id: String, ext: String): String = "$base/movie/${enc(acc.user)}/${enc(acc.pass)}/$id.$ext"
    fun episodeUrl(id: String, ext: String): String = "$base/series/${enc(acc.user)}/${enc(acc.pass)}/$id.$ext"

    fun clearCache() {
        ctx.cacheDir.listFiles()?.filter { it.name.startsWith("x_") }?.forEach { it.delete() }
    }
}
