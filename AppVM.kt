package com.ayham.player

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ayham.player.data.Account
import com.ayham.player.data.Category
import com.ayham.player.data.Details
import com.ayham.player.data.Episode
import com.ayham.player.data.Item
import com.ayham.player.data.Norm
import com.ayham.player.data.Store
import com.ayham.player.data.XtreamClient
import com.ayham.player.ui.Lang
import com.ayham.player.ui.tr
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Tab { HOME, LIVE, MOVIES, SERIES, SEARCH, MYLIST, SETTINGS }

data class PlayRequest(
    val title: String,
    val url: String,
    val live: Boolean,
    val progressKey: String,
    val item: Item,
    val liveList: List<Item> = emptyList(),
    val liveIndex: Int = 0,
    val episodes: List<Episode> = emptyList(),
    val epIndex: Int = 0
)

class AppVM(app: Application) : AndroidViewModel(app) {
    val store = Store(app)
    var client: XtreamClient? = null

    var loggedIn by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var status by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
    var expiry by mutableStateOf("")

    var liveExt by mutableStateOf(store.liveExt)

    var live by mutableStateOf<List<Item>>(emptyList())
    var movies by mutableStateOf<List<Item>>(emptyList())
    var series by mutableStateOf<List<Item>>(emptyList())
    var liveCats by mutableStateOf<List<Category>>(emptyList())
    var movieCats by mutableStateOf<List<Category>>(emptyList())
    var seriesCats by mutableStateOf<List<Category>>(emptyList())

    var tab by mutableStateOf(Tab.HOME)
    var detail by mutableStateOf<Item?>(null)
    var play by mutableStateOf<PlayRequest?>(null)

    var favs by mutableStateOf(store.list("fav"))
    var later by mutableStateOf(store.list("later"))
    var watched by mutableStateOf(store.list("watched"))
    var recent by mutableStateOf(store.list("recent"))
    private var doneVersion by mutableIntStateOf(0)

    init {
        Lang.ar = store.arabic
        store.account()?.let { connect(it) }
    }

    fun setArabic(v: Boolean) {
        Lang.ar = v
        store.arabic = v
    }

    fun setLiveExt(v: String) {
        liveExt = v
        store.liveExt = v
    }

    private fun parse(raw: Account): Account {
        val s = raw.server.trim()
        if (s.contains("username=") && s.contains("password=")) {
            val u = Uri.parse(s)
            val user = u.getQueryParameter("username")
            val pass = u.getQueryParameter("password")
            if (user != null && pass != null && u.scheme != null && u.authority != null) {
                return Account("${u.scheme}://${u.authority}", user, pass)
            }
        }
        return Account(s, raw.user.trim(), raw.pass.trim())
    }

    fun connect(raw: Account) {
        if (busy) return
        val acc = parse(raw)
        busy = true
        error = null
        viewModelScope.launch {
            try {
                val c = XtreamClient(getApplication(), acc)
                status = tr("جارٍ تسجيل الدخول…", "Signing in…")
                val ui = c.login()
                expiry = ui.optString("exp_date").toLongOrNull()
                    ?.let { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(it * 1000)) } ?: ""
                status = tr("جارٍ تحميل القنوات والأفلام والمسلسلات…", "Loading channels, movies & series…")
                val lv = async { runCatching { c.items("live") }.getOrDefault(emptyList()) }
                val mv = async { runCatching { c.items("movie") }.getOrDefault(emptyList()) }
                val sv = async { runCatching { c.items("series") }.getOrDefault(emptyList()) }
                val lc = async { runCatching { c.categories("live") }.getOrDefault(emptyList()) }
                val mc = async { runCatching { c.categories("movie") }.getOrDefault(emptyList()) }
                val sc = async { runCatching { c.categories("series") }.getOrDefault(emptyList()) }
                live = lv.await()
                movies = mv.await()
                series = sv.await()
                liveCats = lc.await()
                movieCats = mc.await()
                seriesCats = sc.await()
                if (live.isEmpty() && movies.isEmpty() && series.isEmpty()) {
                    throw Exception(tr("لم يتم العثور على محتوى في هذا الاشتراك", "No content found for this subscription"))
                }
                client = c
                store.saveAccount(acc)
                loggedIn = true
            } catch (e: Exception) {
                error = e.message ?: e.toString()
                loggedIn = false
            } finally {
                busy = false
            }
        }
    }

    fun refresh() {
        val acc = store.account() ?: return
        client?.clearCache()
        loggedIn = false
        connect(acc)
    }

    fun logout() {
        client?.clearCache()
        store.saveAccount(null)
        client = null
        loggedIn = false
        live = emptyList(); movies = emptyList(); series = emptyList()
        liveCats = emptyList(); movieCats = emptyList(); seriesCats = emptyList()
        detail = null; play = null; tab = Tab.HOME
    }

    // ---------- lists ----------
    private fun get(name: String): List<Item> = when (name) {
        "fav" -> favs
        "later" -> later
        "watched" -> watched
        else -> recent
    }

    private fun set(name: String, v: List<Item>) {
        when (name) {
            "fav" -> favs = v
            "later" -> later = v
            "watched" -> watched = v
            else -> recent = v
        }
        store.saveList(name, v)
    }

    fun has(name: String, item: Item): Boolean = get(name).any { it.key == item.key }

    fun toggle(name: String, item: Item) {
        val cur = get(name)
        val n = if (cur.any { it.key == item.key }) cur.filterNot { it.key == item.key }
        else listOf(item) + cur
        set(name, n)
    }

    private fun touchRecent(item: Item) {
        val n = (listOf(item) + recent.filterNot { it.key == item.key }).take(30)
        set("recent", n)
    }

    fun isDone(key: String): Boolean {
        doneVersion // read to register dependency
        return store.isDone(key)
    }

    fun markDone(key: String, done: Boolean) {
        store.setDone(key, done)
        doneVersion++
    }

    // ---------- playback ----------
    fun open(item: Item, liveList: List<Item> = emptyList(), liveIndex: Int = 0) {
        if (item.type == "live") {
            val l = if (liveList.isEmpty()) listOf(item) else liveList
            playLive(l, if (liveList.isEmpty()) 0 else liveIndex)
        } else {
            detail = item
        }
    }

    fun playLive(list: List<Item>, idx: Int) {
        val c = client ?: return
        if (list.isEmpty()) return
        val it = list[idx.coerceIn(0, list.size - 1)]
        play = PlayRequest(it.name, c.liveUrl(it.id, liveExt), true, "", it, list, list.indexOf(it))
    }

    fun zap(delta: Int) {
        val r = play ?: return
        if (!r.live || r.liveList.isEmpty()) return
        val n = (r.liveIndex + delta + r.liveList.size) % r.liveList.size
        playLive(r.liveList, n)
    }

    /** Fallback for servers that only serve live as raw .ts */
    fun retryLiveAsTs(): Boolean {
        val r = play ?: return false
        if (!r.live || !r.url.endsWith(".m3u8")) return false
        play = r.copy(url = r.url.removeSuffix(".m3u8") + ".ts")
        return true
    }

    fun playMovie(item: Item) {
        val c = client ?: return
        play = PlayRequest(item.name, c.movieUrl(item.id, item.ext), false, "m:${item.id}", item)
        touchRecent(item)
    }

    fun playEpisode(seriesItem: Item, eps: List<Episode>, idx: Int) {
        val c = client ?: return
        if (eps.isEmpty()) return
        val i = idx.coerceIn(0, eps.size - 1)
        val e = eps[i]
        play = PlayRequest(
            "${seriesItem.name} · S${e.season}E${e.num}",
            c.episodeUrl(e.id, e.ext), false, "e:${e.id}", seriesItem,
            episodes = eps, epIndex = i
        )
        touchRecent(seriesItem)
    }

    fun nextEpisode(): Boolean {
        val r = play ?: return false
        if (r.episodes.isEmpty() || r.epIndex + 1 >= r.episodes.size) return false
        playEpisode(r.item, r.episodes, r.epIndex + 1)
        return true
    }

    fun prevEpisode(): Boolean {
        val r = play ?: return false
        if (r.episodes.isEmpty() || r.epIndex <= 0) return false
        playEpisode(r.item, r.episodes, r.epIndex - 1)
        return true
    }

    suspend fun details(item: Item): Details {
        val c = client ?: return Details()
        return if (item.type == "series") c.seriesInfo(item.id) else c.vodInfo(item.id)
    }

    // ---------- search ----------
    fun search(q: String): Triple<List<Item>, List<Item>, List<Item>> {
        val tokens = Norm.n(q).trim().split(' ').filter { it.isNotBlank() }
        if (tokens.isEmpty()) return Triple(emptyList(), emptyList(), emptyList())
        fun f(src: List<Item>) = src.asSequence().filter { it.norm.isNotEmpty() && tokens.all { t -> it.norm.contains(t) } }.take(60).toList()
        return Triple(f(live), f(movies), f(series))
    }
}
