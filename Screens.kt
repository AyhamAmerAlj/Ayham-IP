package com.ayham.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayham.player.AppVM
import com.ayham.player.Tab
import com.ayham.player.data.Account
import com.ayham.player.data.Item
import kotlinx.coroutines.delay

private fun rowOpen(vm: AppVM, list: List<Item>): (Item, Int) -> Unit = { item, i -> vm.open(item, list, i) }

private val softPanel = Color(0x66160E2B)

// =====================================================================
// Root
// =====================================================================
@Composable
fun AppRoot(vm: AppVM) {
    val dir = if (Lang.ar) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides dir) {
        BackHandler(enabled = vm.loggedIn && vm.play == null && vm.detail != null) { vm.detail = null }
        BackHandler(
            enabled = vm.loggedIn && vm.play == null && vm.detail == null && vm.tab != Tab.HOME
        ) { vm.tab = Tab.HOME }

        AppBackground {
            if (!vm.loggedIn) {
                if (vm.busy) Splash(vm) else LoginScreen(vm)
            } else {
                MainShell(vm)
            }
            val d = vm.detail
            if (vm.loggedIn && d != null) DetailScreen(vm, d)
            val p = vm.play
            if (vm.loggedIn && p != null) PlayerScreen(vm, p)
        }
    }
}

@Composable
fun Splash(vm: AppVM) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Brand(46)
        Spacer(Modifier.height(28.dp))
        CircularProgressIndicator(color = Neon)
        Spacer(Modifier.height(18.dp))
        Text(vm.status, color = Muted, fontSize = 16.sp)
    }
}

// =====================================================================
// Login
// =====================================================================
@Composable
private fun AField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Neon,
            unfocusedBorderColor = Color(0x44FFFFFF),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = Lilac,
            focusedLabelColor = Lilac,
            unfocusedLabelColor = Muted
        ),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun LoginScreen(vm: AppVM) {
    val saved = remember { vm.store.account() }
    var server by remember { mutableStateOf(saved?.server ?: "http://") }
    var user by remember { mutableStateOf(saved?.user ?: "") }
    var pass by remember { mutableStateOf(saved?.pass ?: "") }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(200)
        runCatching { first.requestFocus() }
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .width(560.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(softPanel)
                    .border(1.dp, Color(0x33C4B5FD), RoundedCornerShape(28.dp))
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Brand(30)
                Text(
                    tr("سجّل دخولك بحساب Xtream Codes", "Sign in with your Xtream Codes account"),
                    color = Muted, fontSize = 14.sp
                )
                Spacer(Modifier.height(4.dp))
                AField(server, { server = it }, "Server URL  (http://host:port)", Modifier.focusRequester(first))
                AField(user, { user = it }, "Username")
                AField(pass, { pass = it }, "Password")
                vm.error?.let {
                    Text("⚠ $it", color = Color(0xFFFF8A9B), fontSize = 14.sp, maxLines = 3)
                }
                Spacer(Modifier.height(4.dp))
                NeonButton(
                    tr("دخول  Sign in", "Sign in"),
                    { vm.connect(Account(server, user, pass)) },
                    primary = true
                )
                Text(
                    tr(
                        "نصيحة: يمكنك لصق رابط M3U كامل في خانة السيرفر وسيتم استخراج البيانات تلقائيًا.",
                        "Tip: you can paste the full M3U link into the server field; details are extracted automatically."
                    ),
                    color = Muted, fontSize = 12.sp
                )
            }
        }
    }
}

// =====================================================================
// Shell + navigation
// =====================================================================
@Composable
fun MainShell(vm: AppVM) {
    val blocked = vm.detail != null || vm.play != null
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(200)
        runCatching { first.requestFocus() }
    }
    Row(
        Modifier
            .fillMaxSize()
            .focusProperties { canFocus = !blocked }
    ) {
        NavRail(vm, first)
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when (vm.tab) {
                Tab.HOME -> HomeScreen(vm)
                Tab.LIVE -> LiveScreen(vm)
                Tab.MOVIES -> BrowseScreen(vm, "movie")
                Tab.SERIES -> BrowseScreen(vm, "series")
                Tab.SEARCH -> SearchScreen(vm)
                Tab.MYLIST -> MyListScreen(vm)
                Tab.SETTINGS -> SettingsScreen(vm)
            }
        }
    }
}

@Composable
private fun NavRail(vm: AppVM, first: FocusRequester) {
    val tabs = listOf(
        Triple(Tab.HOME, "🏠", tr("الرئيسية", "Home")),
        Triple(Tab.LIVE, "📺", tr("مباشر", "Live")),
        Triple(Tab.MOVIES, "🎬", tr("أفلام", "Movies")),
        Triple(Tab.SERIES, "🍿", tr("مسلسلات", "Series")),
        Triple(Tab.SEARCH, "🔎", tr("بحث", "Search")),
        Triple(Tab.MYLIST, "⭐", tr("قائمتي", "My list")),
        Triple(Tab.SETTINGS, "⚙", tr("الإعدادات", "Settings"))
    )
    Column(
        Modifier
            .width(116.dp)
            .fillMaxHeight()
            .background(Color(0x33000000))
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "A",
            style = androidx.compose.ui.text.TextStyle(
                brush = Brush.linearGradient(listOf(Lilac, Magenta)),
                fontSize = 38.sp,
                fontWeight = FontWeight.Black
            )
        )
        Spacer(Modifier.height(4.dp))
        tabs.forEachIndexed { i, (tab, icon, label) ->
            val req = if (i == 0) Modifier.focusRequester(first) else Modifier
            val selected = vm.tab == tab
            FocusBox({ vm.tab = tab }, Modifier.width(96.dp).then(req), RoundedCornerShape(18.dp), 1.05f) { f ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            when {
                                f -> Purple.copy(alpha = 0.7f)
                                selected -> Purple.copy(alpha = 0.35f)
                                else -> Color.Transparent
                            }
                        )
                        .padding(vertical = 9.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(icon, fontSize = 22.sp)
                    Text(label, color = Color.White, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }
}

// =====================================================================
// Home
// =====================================================================
@Composable
private fun Hero(vm: AppVM, list: List<Item>) {
    if (list.isEmpty()) return
    var idx by remember { mutableIntStateOf(0) }
    var heroFocused by remember { mutableStateOf(false) }
    LaunchedEffect(list.size) {
        while (true) {
            delay(7000)
            if (!heroFocused && list.isNotEmpty()) idx = (idx + 1) % list.size
        }
    }
    val cur = list[idx.coerceIn(0, list.size - 1)]
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 10.dp)
            .height(300.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF3B1D7A), Color(0xFF1B0E3C), Color(0xFF2A1055))
                )
            )
            .border(1.dp, Color(0x33C4B5FD), RoundedCornerShape(28.dp))
            .onFocusChanged { heroFocused = it.hasFocus }
            .padding(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "✦ " + tr("مميز لك", "FEATURED") + "  ·  " +
                    if (cur.type == "movie") tr("فيلم", "MOVIE") else tr("مسلسل", "SERIES"),
                color = Lilac, fontSize = 13.sp, letterSpacing = 2.sp
            )
            Text(
                cur.name, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            if (cur.rating.isNotBlank() && cur.rating != "0") {
                Text("★ ${cur.rating}", color = Color(0xFFFFD166), fontSize = 16.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (cur.type == "movie") {
                    NeonButton("▶ " + tr("تشغيل", "Play"), { vm.playMovie(cur) }, primary = true)
                }
                NeonButton("ℹ " + tr("التفاصيل", "Details"), { vm.detail = cur })
            }
        }
        Spacer(Modifier.width(24.dp))
        Poster(
            cur.poster,
            Modifier
                .fillMaxHeight()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF241446))
        )
    }
}

@Composable
fun HomeScreen(vm: AppVM) {
    val featured = remember(vm.movies, vm.series) {
        vm.movies.sortedByDescending { it.added }.take(8) + vm.series.sortedByDescending { it.added }.take(4)
    }
    val latestMovies = remember(vm.movies) { vm.movies.sortedByDescending { it.added }.take(40) }
    val latestSeries = remember(vm.series) { vm.series.sortedByDescending { it.added }.take(40) }
    val favChannels = vm.favs.filter { it.type == "live" }
    val done: (Item) -> Boolean = { vm.has("watched", it) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 18.dp)) {
        item {
            Row(
                Modifier.padding(horizontal = 48.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Brand(24)
            }
        }
        item { Hero(vm, featured) }
        item { ContentRow(tr("متابعة المشاهدة", "Continue watching"), vm.recent, rowOpen(vm, vm.recent), done) }
        item { ContentRow(tr("قنواتي المفضلة", "Favorite channels"), favChannels, rowOpen(vm, favChannels)) }
        item {
            val l = vm.later
            ContentRow(tr("مشاهدة لاحقًا", "Watch later"), l, rowOpen(vm, l), done)
        }
        item { ContentRow(tr("أحدث الأفلام", "Latest movies"), latestMovies, rowOpen(vm, latestMovies), done) }
        item { ContentRow(tr("أحدث المسلسلات", "Latest series"), latestSeries, rowOpen(vm, latestSeries), done) }
        item {
            val f = vm.favs.filter { it.type != "live" }
            ContentRow(tr("المفضلة", "Favorites"), f, rowOpen(vm, f), done)
        }
    }
}

// =====================================================================
// Movies / Series browse
// =====================================================================
@Composable
fun BrowseScreen(vm: AppVM, kind: String) {
    val all = if (kind == "movie") vm.movies else vm.series
    val cats = if (kind == "movie") vm.movieCats else vm.seriesCats
    var sel by remember(kind) { mutableStateOf<String?>(null) }
    val byCat = remember(all) { all.groupBy { it.categoryId } }
    val latest = remember(all) { all.sortedByDescending { it.added }.take(40) }
    val done: (Item) -> Boolean = { vm.has("watched", it) }

    Column(Modifier.fillMaxSize().padding(top = 16.dp)) {
        SectionTitle(if (kind == "movie") tr("الأفلام", "Movies") else tr("المسلسلات", "Series"))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Chip(tr("الكل", "All"), sel == null, { sel = null }) }
            items(cats.size) { i ->
                val c = cats[i]
                Chip(c.name, sel == c.id, { sel = c.id })
            }
        }
        Box(Modifier.weight(1f)) {
            val s = sel
            if (s == null) {
                LazyColumn(Modifier.fillMaxSize()) {
                    item { ContentRow(tr("أُضيف حديثًا", "Recently added"), latest, rowOpen(vm, latest), done) }
                    items(cats.size) { i ->
                        val c = cats[i]
                        val l = byCat[c.id]
                        if (l != null) {
                            val shown = l.take(40)
                            ContentRow(c.name, shown, rowOpen(vm, shown), done)
                        }
                    }
                }
            } else {
                val l = byCat[s].orEmpty()
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    contentPadding = PaddingValues(horizontal = 48.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(l.size) { i ->
                        val it = l[i]
                        PosterCard(it, { vm.detail = it }, width = 150.dp, done = done(it))
                    }
                }
            }
        }
    }
}

// =====================================================================
// Live
// =====================================================================
@Composable
private fun CatRow(text: String, selected: Boolean, onClick: () -> Unit) {
    FocusBox(onClick, Modifier.fillMaxWidth(), RoundedCornerShape(14.dp), 1.03f) { f ->
        Text(
            text,
            color = Color.White,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    when {
                        f -> Purple.copy(alpha = 0.7f)
                        selected -> Purple.copy(alpha = 0.35f)
                        else -> Color(0x14FFFFFF)
                    }
                )
                .padding(horizontal = 14.dp, vertical = 12.dp)
        )
    }
}

@Composable
fun LiveScreen(vm: AppVM) {
    var cat by remember { mutableStateOf("all") }
    val list = remember(cat, vm.live, vm.favs) {
        when (cat) {
            "all" -> vm.live
            "fav" -> vm.favs.filter { it.type == "live" }
            else -> vm.live.filter { it.categoryId == cat }
        }
    }
    Row(Modifier.fillMaxSize().padding(top = 16.dp)) {
        LazyColumn(
            Modifier.width(270.dp).fillMaxHeight(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { CatRow("📺  " + tr("كل القنوات", "All channels"), cat == "all") { cat = "all" } }
            item { CatRow("⭐  " + tr("المفضلة", "Favorites"), cat == "fav") { cat = "fav" } }
            items(vm.liveCats.size) { i ->
                val c = vm.liveCats[i]
                CatRow(c.name, cat == c.id) { cat = c.id }
            }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxHeight(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(list.size) { i ->
                val ch = list[i]
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FocusBox({ vm.open(ch, list, i) }, Modifier.weight(1f).height(64.dp), RoundedCornerShape(14.dp), 1.02f) { f ->
                        Row(
                            Modifier
                                .fillMaxSize()
                                .background(if (f) Color(0x44A855F7) else Color(0x14FFFFFF))
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Poster(ch.poster, Modifier.size(46.dp), ContentScale.Fit)
                            Spacer(Modifier.width(14.dp))
                            Text(
                                ch.name, color = Color.White, fontSize = 17.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    val isFav = vm.has("fav", ch)
                    FocusBox({ vm.toggle("fav", ch) }, Modifier.size(56.dp), CircleShape, 1.1f) { f ->
                        Box(
                            Modifier.fillMaxSize().background(if (f) Purple.copy(alpha = 0.6f) else Color(0x14FFFFFF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(if (isFav) "★" else "☆", fontSize = 24.sp, color = if (isFav) Color(0xFFFFD166) else Muted)
                        }
                    }
                }
            }
        }
    }
}

// =====================================================================
// Search
// =====================================================================
@Composable
fun SearchScreen(vm: AppVM) {
    var q by remember { mutableStateOf("") }
    val res = remember(q) { vm.search(q) }
    val done: (Item) -> Boolean = { vm.has("watched", it) }
    Column(Modifier.fillMaxSize().padding(top = 20.dp)) {
        OutlinedTextField(
            value = q,
            onValueChange = { q = it },
            singleLine = true,
            placeholder = { Text(tr("ابحث بالعربي أو English…", "Search in Arabic or English…"), color = Muted) },
            leadingIcon = { Text("🔎", fontSize = 18.sp) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = RoundedCornerShape(50),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Neon,
                unfocusedBorderColor = Color(0x44FFFFFF),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Lilac
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp)
        )
        Spacer(Modifier.height(8.dp))
        if (q.isBlank()) {
            Text(
                tr("اكتب اسم قناة أو فيلم أو مسلسل", "Type a channel, movie or series name"),
                color = Muted, modifier = Modifier.padding(horizontal = 52.dp, vertical = 20.dp)
            )
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item { ContentRow(tr("القنوات", "Channels"), res.first, rowOpen(vm, res.first)) }
            item { ContentRow(tr("الأفلام", "Movies"), res.second, rowOpen(vm, res.second), done) }
            item { ContentRow(tr("المسلسلات", "Series"), res.third, rowOpen(vm, res.third), done) }
        }
    }
}

// =====================================================================
// My list
// =====================================================================
@Composable
fun MyListScreen(vm: AppVM) {
    var sub by remember { mutableIntStateOf(0) }
    val l = when (sub) {
        0 -> vm.favs
        1 -> vm.later
        else -> vm.watched
    }
    Column(Modifier.fillMaxSize().padding(top = 16.dp)) {
        SectionTitle(tr("قائمتي", "My list"))
        Row(
            Modifier.padding(horizontal = 48.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Chip("⭐ " + tr("المفضلة", "Favorites"), sub == 0, { sub = 0 })
            Chip("⏰ " + tr("مشاهدة لاحقًا", "Watch later"), sub == 1, { sub = 1 })
            Chip("✔ " + tr("تمت المشاهدة", "Watched"), sub == 2, { sub = 2 })
        }
        if (l.isEmpty()) {
            Text(tr("لا يوجد شيء هنا بعد", "Nothing here yet"), color = Muted, modifier = Modifier.padding(52.dp))
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(l.size) { i ->
                val it = l[i]
                if (it.type == "live") ChannelCard(it, { vm.open(it, l.filter { x -> x.type == "live" }, 0) }, width = 150.dp)
                else PosterCard(it, { vm.detail = it }, width = 150.dp, done = vm.has("watched", it))
            }
        }
    }
}

// =====================================================================
// Settings
// =====================================================================
@Composable
fun SettingsScreen(vm: AppVM) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Brand(26) }
        item {
            val acc = vm.store.account()
            Text(
                (acc?.user ?: "") + if (vm.expiry.isNotBlank()) "   ·   " + tr("ينتهي الاشتراك: ", "Expires: ") + vm.expiry else "",
                color = Muted, fontSize = 15.sp
            )
        }
        item {
            Text(tr("اللغة / Language", "Language / اللغة"), color = Lilac, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip("العربية", Lang.ar, { vm.setArabic(true) })
                Chip("English", !Lang.ar, { vm.setArabic(false) })
            }
        }
        item {
            Text(tr("صيغة البث المباشر", "Live stream format"), color = Lilac, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip("HLS (m3u8)", vm.liveExt == "m3u8", { vm.setLiveExt("m3u8") })
                Chip("TS", vm.liveExt == "ts", { vm.setLiveExt("ts") })
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NeonButton("🔄 " + tr("تحديث المحتوى", "Refresh content"), { vm.refresh() })
                NeonButton("⎋ " + tr("تسجيل الخروج", "Sign out"), { vm.logout() })
            }
        }
        item {
            Text(
                "Ayham Player 1.0\n" + tr(
                    "مشغّل فقط — لا يحتوي على أي قنوات أو محتوى.",
                    "Player only — contains no channels or content."
                ),
                color = Muted, fontSize = 13.sp
            )
        }
    }
}

// =====================================================================
// Detail (movie / series)
// =====================================================================
@Composable
fun DetailScreen(vm: AppVM, item: Item) {
    val details by produceState<com.ayham.player.data.Details?>(null, item.key) {
        value = runCatching { vm.details(item) }.getOrNull()
    }
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(item.key) {
        delay(150)
        runCatching { playFocus.requestFocus() }
    }
    val eps = details?.episodes.orEmpty()
    val seasons = remember(eps) { eps.map { it.season }.distinct().sorted() }
    var season by remember(item.key) { mutableIntStateOf(1) }
    LaunchedEffect(seasons) {
        if (seasons.isNotEmpty() && season !in seasons) season = seasons.first()
    }

    AppBackground(Modifier.focusProperties { canFocus = vm.play == null }) {
        Row(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 36.dp)) {
            Column(Modifier.width(250.dp)) {
                Poster(
                    item.poster,
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF241446))
                        .border(1.dp, Color(0x44C4B5FD), RoundedCornerShape(20.dp))
                )
            }
            Spacer(Modifier.width(36.dp))
            LazyColumn(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    Text(
                        item.name, color = Color.White, fontSize = 34.sp,
                        fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
                item {
                    val d = details
                    val meta = listOfNotNull(
                        d?.year?.ifBlank { null },
                        d?.genre?.ifBlank { null },
                        d?.duration?.ifBlank { null },
                        (d?.rating ?: item.rating).takeIf { !it.isNullOrBlank() && it != "0" }?.let { "★ $it" }
                    ).joinToString("   ·   ")
                    if (meta.isNotBlank()) Text(meta, color = Lilac, fontSize = 15.sp)
                }
                item {
                    val p = details?.plot.orEmpty()
                    if (p.isNotBlank()) Text(p, color = Color(0xFFE5E0F5), fontSize = 15.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
                    else if (details == null) Text(tr("جارٍ التحميل…", "Loading…"), color = Muted)
                }
                item {
                    val cast = details?.cast.orEmpty()
                    if (cast.isNotBlank()) Text(
                        tr("الممثلون: ", "Cast: ") + cast, color = Muted, fontSize = 13.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (item.type == "movie") {
                            val resume = vm.store.progress("m:${item.id}") > 10_000
                            NeonButton(
                                "▶ " + if (resume) tr("استئناف", "Resume") else tr("تشغيل", "Play"),
                                { vm.playMovie(item) }, primary = true, focusRequester = playFocus
                            )
                        } else {
                            NeonButton(
                                "▶ " + tr("تشغيل", "Play"),
                                {
                                    if (eps.isNotEmpty()) {
                                        val idx = eps.indexOfFirst { !vm.isDone("e:${it.id}") }.let { if (it < 0) 0 else it }
                                        vm.playEpisode(item, eps, idx)
                                    }
                                },
                                primary = true, focusRequester = playFocus
                            )
                        }
                        val fav = vm.has("fav", item)
                        val later = vm.has("later", item)
                        val seen = vm.has("watched", item)
                        NeonButton(if (fav) "★ " + tr("في المفضلة", "Favorited") else "☆ " + tr("المفضلة", "Favorite"), { vm.toggle("fav", item) })
                        NeonButton(if (later) "⏰ ✓" else "⏰ " + tr("لاحقًا", "Later"), { vm.toggle("later", item) })
                        NeonButton(if (seen) "✔ " + tr("تمت", "Watched") else "✔ " + tr("تمت المشاهدة", "Mark watched"), { vm.toggle("watched", item) })
                    }
                }
                if (item.type == "series" && seasons.isNotEmpty()) {
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                            items(seasons.size) { i ->
                                val s = seasons[i]
                                Chip(tr("الموسم ", "Season ") + s, season == s, { season = s })
                            }
                        }
                    }
                    val list = eps.filter { it.season == season }
                    items(list.size) { i ->
                        val e = list[i]
                        val seen = vm.isDone("e:${e.id}")
                        FocusBox(
                            { vm.playEpisode(item, eps, eps.indexOf(e)) },
                            Modifier.fillMaxWidth(), RoundedCornerShape(14.dp), 1.02f
                        ) { f ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .background(if (f) Color(0x44A855F7) else Color(0x14FFFFFF))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Poster(
                                    e.image,
                                    Modifier.width(128.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Color(0xFF241446))
                                )
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "${e.num}.  ${e.title}", color = Color.White, fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                    if (e.plot.isNotBlank()) Text(e.plot, color = Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                if (seen) Text("✓", color = Lilac, fontSize = 20.sp, modifier = Modifier.padding(horizontal = 8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
