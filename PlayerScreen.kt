package com.ayham.player.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.ayham.player.AppVM
import com.ayham.player.PlayRequest
import com.ayham.player.data.Item
import com.ayham.player.data.Norm
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs

private enum class Panel { NONE, AUDIO, SUBS, SUBURL, COMM, PICK }

private class TrackOpt(val label: String, val selected: Boolean, val group: Tracks.Group, val index: Int)

@androidx.annotation.OptIn(UnstableApi::class)
private fun buildExo(ctx: Context, handleFocus: Boolean): ExoPlayer {
    val ds = DefaultHttpDataSource.Factory()
        .setUserAgent("AyhamPlayer/1.0 (Android TV)")
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15000)
        .setReadTimeoutMs(20000)
    val builder = ExoPlayer.Builder(ctx, DefaultRenderersFactory(ctx).setEnableDecoderFallback(true))
        .setMediaSourceFactory(DefaultMediaSourceFactory(ctx).setDataSourceFactory(ds))
    if (handleFocus) {
        builder.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            true
        )
    }
    return builder.build()
}

private fun trackLabel(f: Format, i: Int): String {
    val lang = f.language?.takeIf { it != "und" }?.let { code ->
        Locale(code).displayLanguage.ifBlank { code }
    }
    val parts = listOfNotNull(
        f.label,
        lang,
        if (f.channelCount > 0) "${f.channelCount}ch" else null,
        f.sampleMimeType?.substringAfter('/')?.uppercase()?.takeIf { it.isNotBlank() }
    ).distinct()
    return parts.joinToString(" · ").ifBlank { "Track ${i + 1}" }
}

private fun fmtTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
    else String.format(Locale.US, "%02d:%02d", m, sec)
}

private fun fmtOffset(ms: Long): String = String.format(Locale.US, "%+.1fs", ms / 1000f)

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(vm: AppVM, req: PlayRequest) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val reqNow by rememberUpdatedState(req)

    val main = remember { buildExo(ctx, true) }
    val comm = remember {
        buildExo(ctx, false).also { p ->
            p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
                .build()
        }
    }

    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    var isPlaying by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var posMs by remember { mutableLongStateOf(0L) }
    var durMs by remember { mutableLongStateOf(0L) }
    var controls by remember { mutableStateOf(true) }
    var panel by remember { mutableStateOf(Panel.NONE) }
    var toast by remember { mutableStateOf("") }
    var toastAt by remember { mutableLongStateOf(0L) }
    var lastTouch by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var resize by remember { mutableIntStateOf(0) }
    var subScale by remember { mutableIntStateOf(1) }
    var seekStep by remember { mutableIntStateOf(10) }
    var commItem by remember { mutableStateOf<Item?>(null) }
    var ambience by remember { mutableIntStateOf(0) }
    var syncMs by remember { mutableLongStateOf(0L) }
    var syncStep by remember { mutableIntStateOf(500) }
    var epg by remember { mutableStateOf("") }

    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }

    fun interact() {
        lastTouch = System.currentTimeMillis()
    }

    fun toastMsg(s: String) {
        toast = s
        toastAt = System.currentTimeMillis()
    }

    fun togglePlay() {
        if (main.isPlaying) main.pause() else main.play()
        controls = true
        interact()
    }

    fun seekBy(ms: Long) {
        val d = main.duration
        var t = main.currentPosition + ms
        if (t < 0) t = 0
        if (d != C.TIME_UNSET && t > d - 1000) t = (d - 1000).coerceAtLeast(0)
        main.seekTo(t)
        toastMsg((if (ms > 0) "⏩ +" else "⏪ −") + (abs(ms) / 1000) + tr(" ث", "s"))
    }

    fun finishCurrent() {
        val r = reqNow
        if (r.live) return
        vm.markDone(r.progressKey, true)
        if (r.item.type == "movie" && !vm.has("watched", r.item)) vm.toggle("watched", r.item)
    }

    fun zapBy(d: Int) {
        val r = reqNow
        if (!r.live || r.liveList.isEmpty()) return
        val n = (r.liveIndex + d + r.liveList.size) % r.liveList.size
        toastMsg("📺 " + r.liveList[n].name)
        vm.zap(d)
    }

    fun adjustSync(delayCommentary: Boolean) {
        if (commItem == null) return
        val step = syncStep.toLong()
        if (delayCommentary) {
            if (!comm.isCurrentMediaItemSeekable) {
                toastMsg(tr("هذه القناة لا تدعم التأخير", "This stream can't be delayed"))
                return
            }
            comm.seekTo((comm.currentPosition - step).coerceAtLeast(0))
            syncMs += step
        } else {
            val off = comm.currentLiveOffset
            if (comm.isCurrentMediaItemSeekable && off != C.TIME_UNSET && off >= step) {
                comm.seekTo(comm.currentPosition + step)
            } else if (main.isCurrentMediaItemSeekable) {
                // Commentary is already at the live edge: delay the picture instead (same net effect).
                main.seekTo((main.currentPosition - step).coerceAtLeast(0))
            } else {
                toastMsg(tr("هذه القناة لا تدعم التقديم", "This stream can't be advanced"))
                return
            }
            syncMs -= step
        }
        toastMsg(tr("إزاحة التعليق: ", "Commentary offset: ") + fmtOffset(syncMs))
    }

    fun resyncReset() {
        main.seekToDefaultPosition()
        if (commItem != null) comm.seekToDefaultPosition()
        syncMs = 0
        toastMsg(tr("تمت إعادة المزامنة", "Sync reset"))
    }

    fun audioOptions(): List<TrackOpt> = tracks.groups
        .filter { it.type == C.TRACK_TYPE_AUDIO }
        .flatMap { g ->
            (0 until g.length).filter { g.isTrackSupported(it) }
                .map { i -> TrackOpt(trackLabel(g.getTrackFormat(i), i), g.isTrackSelected(i), g, i) }
        }

    fun textOptions(): List<TrackOpt> = tracks.groups
        .filter { it.type == C.TRACK_TYPE_TEXT }
        .flatMap { g ->
            (0 until g.length).filter { g.isTrackSupported(it) }
                .map { i -> TrackOpt(trackLabel(g.getTrackFormat(i), i), g.isTrackSelected(i), g, i) }
        }

    fun selectAudio(o: TrackOpt) {
        main.trackSelectionParameters = main.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(o.group.mediaTrackGroup, o.index))
            .build()
        panel = Panel.NONE
    }

    fun selectText(o: TrackOpt?) {
        val b = main.trackSelectionParameters.buildUpon()
        if (o == null) {
            b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(TrackSelectionOverride(o.group.mediaTrackGroup, o.index))
        }
        main.trackSelectionParameters = b.build()
        panel = Panel.NONE
    }

    fun loadSubtitle(url: String) {
        val u = url.trim()
        if (u.isEmpty()) return
        val mime = if (u.substringBefore('?').lowercase().endsWith(".vtt")) MimeTypes.TEXT_VTT
        else MimeTypes.APPLICATION_SUBRIP
        val sub = MediaItem.SubtitleConfiguration.Builder(Uri.parse(u))
            .setMimeType(mime)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
        val pos = main.currentPosition
        main.setMediaItem(
            MediaItem.Builder().setUri(reqNow.url).setSubtitleConfigurations(listOf(sub)).build(),
            pos
        )
        main.prepare()
        main.playWhenReady = true
        main.trackSelectionParameters = main.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
        panel = Panel.NONE
        toastMsg(tr("جارٍ تحميل الترجمة…", "Loading subtitles…"))
    }

    // ---------------- listeners ----------------
    DisposableEffect(main) {
        val l = object : Player.Listener {
            override fun onTracksChanged(t: Tracks) {
                tracks = t
            }

            override fun onIsPlayingChanged(p: Boolean) {
                isPlaying = p
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                comm.playWhenReady = playWhenReady
            }

            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_ENDED && !reqNow.live) {
                    finishCurrent()
                    if (reqNow.episodes.isNotEmpty()) {
                        if (!vm.nextEpisode()) vm.play = null
                    } else {
                        vm.play = null
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (reqNow.live && vm.retryLiveAsTs()) return
                errorText = tr("تعذّر تشغيل هذا المحتوى", "Couldn't play this stream") + "  (" + error.errorCodeName + ")"
            }
        }
        main.addListener(l)
        onDispose { main.removeListener(l) }
    }
    DisposableEffect(comm) {
        val l = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                toastMsg(tr("تعذّر تشغيل قناة التعليق", "Couldn't play the commentary channel"))
            }
        }
        comm.addListener(l)
        onDispose { comm.removeListener(l) }
    }
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            val r = reqNow
            if (!r.live && r.progressKey.isNotEmpty() && main.currentPosition > 0) {
                vm.store.setProgress(r.progressKey, main.currentPosition)
            }
            comm.release()
            main.release()
        }
    }

    // ---------------- load media ----------------
    LaunchedEffect(req.url) {
        errorText = null
        val saved = if (!req.live) vm.store.progress(req.progressKey) else 0L
        val start = if (saved > 15_000 && !vm.isDone(req.progressKey)) saved else 0L
        main.setMediaItem(MediaItem.fromUri(req.url), start)
        main.prepare()
        main.playWhenReady = true
        controls = true
        interact()
        if (req.live) {
            epg = ""
            epg = runCatching {
                vm.client?.shortEpg(req.item.id)?.joinToString("   ›   ") ?: ""
            }.getOrDefault("")
        }
    }

    // ---------------- commentary player ----------------
    LaunchedEffect(commItem) {
        val ci = commItem
        syncMs = 0
        if (ci == null) {
            comm.stop()
            comm.clearMediaItems()
        } else {
            val u = vm.client?.liveUrl(ci.id, vm.liveExt) ?: return@LaunchedEffect
            comm.setMediaItem(MediaItem.fromUri(u))
            comm.volume = 1f
            comm.prepare()
            comm.playWhenReady = main.playWhenReady
        }
    }
    LaunchedEffect(ambience, commItem) {
        main.volume = if (commItem != null) ambience / 100f else 1f
    }

    // ---------------- ticker ----------------
    LaunchedEffect(Unit) {
        var tick = 0
        while (true) {
            delay(500)
            posMs = main.currentPosition.coerceAtLeast(0)
            durMs = if (main.duration == C.TIME_UNSET) 0L else main.duration
            tick++
            val r = reqNow
            if (tick % 10 == 0 && !r.live && durMs > 0 && main.isPlaying) {
                vm.store.setProgress(r.progressKey, posMs)
                if (posMs > durMs * 0.93 && !vm.isDone(r.progressKey)) finishCurrent()
            }
            val now = System.currentTimeMillis()
            if (controls && panel == Panel.NONE && now - lastTouch > 6000) controls = false
            if (toast.isNotEmpty() && now - toastAt > 1800) toast = ""
        }
    }

    // ---------------- focus management ----------------
    LaunchedEffect(controls, panel) {
        delay(80)
        if (!controls && panel == Panel.NONE) runCatching { rootFocus.requestFocus() }
        else if (panel == Panel.NONE) runCatching { playFocus.requestFocus() }
    }

    BackHandler(enabled = true) {
        when {
            panel == Panel.PICK -> panel = Panel.COMM
            panel == Panel.SUBURL -> panel = Panel.SUBS
            panel != Panel.NONE -> panel = Panel.NONE
            controls -> controls = false
            else -> vm.play = null
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusRequester(rootFocus)
                .focusable()
                .onPreviewKeyEvent { ev ->
                    if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val k = ev.key
                    val live = reqNow.live
                    // Hardware media keys always work
                    if (k == Key.MediaPlayPause || k == Key.MediaPlay || k == Key.MediaPause) {
                        togglePlay(); return@onPreviewKeyEvent true
                    }
                    if (k == Key.MediaFastForward && !live) {
                        seekBy(seekStep * 1000L); return@onPreviewKeyEvent true
                    }
                    if (k == Key.MediaRewind && !live) {
                        seekBy(-seekStep * 1000L); return@onPreviewKeyEvent true
                    }
                    if (k == Key.MediaNext || k == Key.ChannelUp) {
                        if (live) zapBy(1) else vm.nextEpisode()
                        return@onPreviewKeyEvent true
                    }
                    if (k == Key.MediaPrevious || k == Key.ChannelDown) {
                        if (live) zapBy(-1) else vm.prevEpisode()
                        return@onPreviewKeyEvent true
                    }
                    if (k == Key.Menu) {
                        controls = true; interact(); return@onPreviewKeyEvent true
                    }
                    // While the overlay is open, normal focus navigation handles everything
                    if (controls || panel != Panel.NONE) {
                        interact(); return@onPreviewKeyEvent false
                    }
                    // Overlay hidden: remote keys act as quick shortcuts
                    when (k) {
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            togglePlay(); true
                        }
                        Key.DirectionLeft -> {
                            if (commItem != null) adjustSync(true)
                            else if (!live) seekBy(-seekStep * 1000L)
                            else { controls = true; interact() }
                            true
                        }
                        Key.DirectionRight -> {
                            if (commItem != null) adjustSync(false)
                            else if (!live) seekBy(seekStep * 1000L)
                            else { controls = true; interact() }
                            true
                        }
                        Key.DirectionUp -> {
                            if (live) zapBy(1) else { controls = true; interact() }
                            true
                        }
                        Key.DirectionDown -> {
                            if (live) zapBy(-1) else { controls = true; interact() }
                            true
                        }
                        else -> false
                    }
                }
        ) {
            AndroidView(
                factory = { c ->
                    PlayerView(c).apply {
                        useController = false
                        this.player = main
                        isFocusable = false
                        isFocusableInTouchMode = false
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                update = { pv ->
                    pv.resizeMode = when (resize) {
                        1 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        2 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                    val scales = floatArrayOf(0.8f, 1.0f, 1.35f, 1.8f)
                    pv.subtitleView?.setFractionalTextSize(
                        SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * scales[subScale.coerceIn(0, 3)]
                    )
                },
                modifier = Modifier.fillMaxSize()
            )

            if (buffering && errorText == null) {
                CircularProgressIndicator(color = Neon, modifier = Modifier.align(Alignment.Center))
            }
            errorText?.let {
                Text(
                    "⚠ $it", color = Color(0xFFFF8A9B), fontSize = 20.sp,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color(0xCC000000), RoundedCornerShape(16.dp))
                        .padding(24.dp)
                )
            }
            if (toast.isNotEmpty()) {
                Text(
                    toast, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 36.dp)
                        .background(Color(0xCC1B0E3C), RoundedCornerShape(50))
                        .padding(horizontal = 24.dp, vertical = 10.dp)
                )
            }

            if (controls) {
                // ---- top info ----
                Column(
                    Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                        .padding(horizontal = 48.dp, vertical = 24.dp)
                ) {
                    Text(
                        req.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    if (req.live && epg.isNotBlank()) {
                        Text(epg, color = Lilac, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    commItem?.let {
                        Text(
                            "🎙 " + it.name + "   ·   " + fmtOffset(syncMs) +
                                "   ·   " + tr("صوت الملعب ", "Stadium ") + ambience + "%",
                            color = Cyan, fontSize = 14.sp, maxLines = 1
                        )
                    }
                }

                // ---- bottom bar ----
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
                        .padding(horizontal = 48.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (!req.live) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(fmtTime(posMs), color = Color.White, fontSize = 14.sp)
                            Spacer(Modifier.width(12.dp))
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0x33FFFFFF))
                            ) {
                                val frac = if (durMs > 0) (posMs.toFloat() / durMs).coerceIn(0f, 1f) else 0f
                                Box(
                                    Modifier
                                        .fillMaxWidth(frac)
                                        .fillMaxHeight()
                                        .background(Brush.horizontalGradient(listOf(Purple, Magenta)))
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(fmtTime(durMs), color = Color.White, fontSize = 14.sp)
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (req.live) {
                            Chip("⏮ CH", false, { zapBy(-1); interact() })
                        } else if (req.episodes.isNotEmpty()) {
                            Chip("⏮", false, { vm.prevEpisode(); interact() })
                        }
                        if (!req.live) {
                            Chip("⏪ $seekStep", false, { seekBy(-seekStep * 1000L); interact() })
                        }
                        Chip(
                            if (isPlaying) "⏸" else "▶", false, { togglePlay() },
                            Modifier.focusRequester(playFocus)
                        )
                        if (!req.live) {
                            Chip("$seekStep ⏩", false, { seekBy(seekStep * 1000L); interact() })
                        }
                        if (req.live) {
                            Chip("CH ⏭", false, { zapBy(1); interact() })
                        } else if (req.episodes.isNotEmpty()) {
                            Chip("⏭", false, { vm.nextEpisode(); interact() })
                        }
                        Spacer(Modifier.width(10.dp))
                        Chip("🔊 " + tr("الصوت", "Audio"), false, { panel = Panel.AUDIO })
                        Chip("💬 " + tr("الترجمة", "Subs"), false, { panel = Panel.SUBS })
                        if (req.live) {
                            Chip("🎙 " + tr("التعليق", "Commentary"), commItem != null, { panel = Panel.COMM })
                        }
                        Chip("▭ " + listOf("Fit", "Zoom", "Fill")[resize], false, {
                            resize = (resize + 1) % 3; interact()
                        })
                        if (!req.live) {
                            Chip("⏱ $seekStep" + tr("ث", "s"), false, {
                                seekStep = when (seekStep) {
                                    10 -> 30
                                    30 -> 60
                                    else -> 10
                                }
                                interact()
                            })
                        }
                    }
                }
            }

            // ---- panels ----
            when (panel) {
                Panel.AUDIO -> {
                    val opts = audioOptions()
                    SidePanel(tr("مسار الصوت", "Audio track")) {
                        val first = remember { FocusRequester() }
                        LaunchedEffect(Unit) { delay(80); runCatching { first.requestFocus() } }
                        if (opts.isEmpty()) Text(tr("لا توجد مسارات", "No tracks"), color = Muted)
                        opts.forEachIndexed { i, o ->
                            PanelRow(o.label, o.selected, if (i == 0) first else null) { selectAudio(o) }
                        }
                    }
                }
                Panel.SUBS -> {
                    val opts = textOptions()
                    SidePanel(tr("الترجمة", "Subtitles")) {
                        val first = remember { FocusRequester() }
                        LaunchedEffect(Unit) { delay(80); runCatching { first.requestFocus() } }
                        PanelRow(tr("إيقاف", "Off"), opts.none { it.selected }, first) { selectText(null) }
                        opts.forEach { o -> PanelRow(o.label, o.selected, null) { selectText(o) } }
                        PanelRow("＋ " + tr("تحميل ترجمة من رابط", "Load from URL"), null, null) { panel = Panel.SUBURL }
                        PanelRow(
                            tr("حجم الخط: ", "Text size: ") + listOf("S", "M", "L", "XL")[subScale.coerceIn(0, 3)],
                            null, null
                        ) { subScale = (subScale + 1) % 4 }
                    }
                }
                Panel.SUBURL -> {
                    var url by remember { mutableStateOf("http://") }
                    SidePanel(tr("رابط الترجمة (.srt / .vtt)", "Subtitle URL (.srt / .vtt)")) {
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Neon,
                                unfocusedBorderColor = Color(0x44FFFFFF),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = Lilac
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        PanelRow("✔ " + tr("تطبيق", "Apply"), null, null) { loadSubtitle(url) }
                    }
                }
                Panel.COMM -> {
                    SidePanel("🎙 " + tr("تعليق من قناة أخرى", "Commentary from another channel")) {
                        val first = remember { FocusRequester() }
                        LaunchedEffect(Unit) { delay(80); runCatching { first.requestFocus() } }
                        Text(
                            commItem?.name ?: tr("لا يوجد تعليق", "No commentary"),
                            color = Cyan, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        PanelRow("📺 " + tr("اختر قناة التعليق", "Choose commentary channel"), null, first) { panel = Panel.PICK }
                        if (commItem != null) {
                            PanelRow("⏪ " + tr("أخّر التعليق", "Delay commentary") + " " + syncStep + "ms", null, null) { adjustSync(true) }
                            PanelRow("⏩ " + tr("قدّم التعليق", "Advance commentary") + " " + syncStep + "ms", null, null) { adjustSync(false) }
                            PanelRow(
                                "⇆ " + tr("الإزاحة الحالية: ", "Current offset: ") + fmtOffset(syncMs),
                                null, null
                            ) { resyncReset() }
                            PanelRow("⏱ " + tr("حجم الخطوة: ", "Step size: ") + syncStep + "ms", null, null) {
                                syncStep = when (syncStep) {
                                    250 -> 500
                                    500 -> 1000
                                    1000 -> 2000
                                    else -> 250
                                }
                            }
                            PanelRow(
                                "🏟 " + tr("صوت الملعب (القناة الأصلية): ", "Stadium sound (main): ") + ambience + "%",
                                null, null
                            ) {
                                ambience = when (ambience) {
                                    0 -> 10
                                    10 -> 25
                                    25 -> 50
                                    else -> 0
                                }
                            }
                            PanelRow("✖ " + tr("إيقاف التعليق", "Turn commentary off"), null, null) {
                                commItem = null; panel = Panel.NONE
                            }
                        }
                        Text(
                            tr(
                                "نصيحة: بدون قائمة اللمس يمكنك أثناء المشاهدة الضغط يسار/يمين على الريموت لضبط المزامنة بسرعة.",
                                "Tip: while watching with the menu hidden, press Left/Right on the remote to nudge the sync."
                            ),
                            color = Muted, fontSize = 12.sp
                        )
                    }
                }
                Panel.PICK -> {
                    var q by remember { mutableStateOf("") }
                    val results = remember(q) {
                        if (q.isBlank()) {
                            (vm.favs.filter { it.type == "live" } + vm.live).distinctBy { it.id }.take(50)
                        } else {
                            val tokens = Norm.n(q).trim().split(' ').filter { it.isNotBlank() }
                            vm.live.asSequence()
                                .filter { c -> tokens.all { t -> c.norm.contains(t) } }
                                .take(50).toList()
                        }
                    }
                    SidePanel(tr("اختر قناة التعليق", "Pick commentary channel")) {
                        OutlinedTextField(
                            value = q,
                            onValueChange = { q = it },
                            singleLine = true,
                            placeholder = { Text(tr("ابحث…", "Search…"), color = Muted) },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Neon,
                                unfocusedBorderColor = Color(0x44FFFFFF),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = Lilac
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        val first = remember { FocusRequester() }
                        LaunchedEffect(Unit) { delay(120); runCatching { first.requestFocus() } }
                        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(results.size) { i ->
                                val c = results[i]
                                PanelRow(c.name, commItem?.id == c.id, if (i == 0) first else null) {
                                    commItem = c
                                    panel = Panel.COMM
                                    toastMsg("🎙 " + c.name)
                                }
                            }
                        }
                    }
                }
                Panel.NONE -> {}
            }
        }
    }
}

@Composable
private fun BoxScope.SidePanel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .align(Alignment.CenterEnd)
            .width(420.dp)
            .fillMaxHeight()
            .background(Color(0xF2100A20))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title, color = Lilac, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

/** mark == null → plain action row; true/false → selectable row with a radio dot. */
@Composable
private fun PanelRow(text: String, mark: Boolean?, req: FocusRequester?, onClick: () -> Unit) {
    val m = if (req != null) Modifier.focusRequester(req) else Modifier
    FocusBox(onClick, Modifier.fillMaxWidth().then(m), RoundedCornerShape(12.dp), 1.03f) { f ->
        Text(
            (if (mark == null) "" else if (mark) "●  " else "○  ") + text,
            color = Color.White,
            fontSize = 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (f) Purple.copy(alpha = 0.7f)
                    else if (mark == true) Purple.copy(alpha = 0.3f)
                    else Color(0x14FFFFFF)
                )
                .padding(horizontal = 14.dp, vertical = 11.dp)
        )
    }
}
