package app.margin

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.activity.compose.BackHandler
import android.content.Intent as ShareIntent
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.core.content.FileProvider

class MainActivity : ComponentActivity() {
    private var autoStart by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        autoStart = intent?.getBooleanExtra(EXTRA_AUTO_START, false) ?: false
        setContent {
            MarginTheme {
                MarginApp(autoStart = autoStart, onAutoStartHandled = { autoStart = false })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        autoStart = intent.getBooleanExtra(EXTRA_AUTO_START, false)
    }

    companion object {
        const val EXTRA_AUTO_START = "auto_start"
    }
}

@Composable
fun MarginApp(autoStart: Boolean, onAutoStartHandled: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val c = LocalMarginColors.current
    val store = remember { IdeaStore(context.applicationContext) }
    val bibleStore = remember { BibleStore(context.applicationContext) }
    val threads = remember { ThreadStore(context.applicationContext) }
    val prefs = remember { context.getSharedPreferences("margin", 0) }
    val scope = rememberCoroutineScope()
    remember { AppSettings.init(context) }
    var showSettings by remember { mutableStateOf(false) }

    var ideas by remember { mutableStateOf(store.load()) }
    var exchanges by remember { mutableStateOf(threads.load()) }
    var bible by remember { mutableStateOf(bibleStore.load()) }
    var recording by remember { mutableStateOf(false) }
    var latchedStop by remember { mutableStateOf(false) }
    var live by remember { mutableStateOf("") }
    var zone by remember { mutableIntStateOf(Z_CAPTURE) }
    var drag by remember { mutableStateOf(Offset.Zero) }
    var liveEx by remember { mutableStateOf<Exchange?>(null) }
    var busy by remember { mutableStateOf(false) }
    var dragUses by remember { mutableIntStateOf(prefs.getInt("dragUses", 0)) }
    val pendingZone = remember { intArrayOf(Z_CAPTURE) }

    var notice by remember { mutableStateOf<Notice?>(null) }
    val seq = remember { longArrayOf(0L) }
    var proposals by remember { mutableStateOf<List<Proposal>?>(null) }
    val pagerState = rememberPagerState(initialPage = 1) { 2 }

    val tts = remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { st -> if (st == TextToSpeech.SUCCESS) tts.value = engine }
        onDispose { engine?.shutdown() }
    }

    fun say(tone: Tone, text: String, detail: String? = null, action: String? = null, onAction: (() -> Unit)? = null) {
        seq[0] = seq[0] + 1
        notice = Notice(seq[0], tone, text, detail, action, onAction)
    }

    fun speak(sentence: String) {
        val clean = sentence.replace("*", "").replace("#", "").replace("_", " ").trim()
        if (clean.length < 2 || !AppSettings.speakReplies) return
        if (SpeechOut.ready) SpeechOut.speak(clean)
        else tts.value?.speak(clean, TextToSpeech.QUEUE_ADD, null, "reply")
    }

    LaunchedEffect(notice?.id) {
        val n = notice
        if (n != null) {
            val ms = when (n.tone) {
                Tone.Working -> 0L
                Tone.Info, Tone.Success -> 3500L
                Tone.Error -> 9000L
            }
            if (ms > 0) {
                delay(ms)
                if (notice?.id == n.id) notice = null
            }
        }
    }

    fun needsModel(): Boolean = LocalAi.state == AiState.Missing || LocalAi.state == AiState.Downloading

    fun runExtract(text: String) {
        if (needsModel()) {
            say(Tone.Info, "Bible auto-fill needs the on-device model", null, "Set up") {
                scope.launch { pagerState.animateScrollToPage(1) }
            }
            return
        }
        scope.launch {
            say(Tone.Working, "Updating your Bible")
            try {
                val found = LocalAi.extractBible(context, text, bibleStore.load())
                if (found.isEmpty()) {
                    val raw = LocalAi.lastRaw.replace("\n", " ").trim()
                    say(
                        Tone.Info, "Your Bible is already up to date",
                        if (raw.isEmpty() || raw.equals("none", ignoreCase = true)) null else "Model said: " + raw.take(120)
                    )
                } else {
                    notice = null
                    proposals = found
                }
            } catch (e: Throwable) {
                say(Tone.Error, "Couldn't update the Bible", e.message, "Retry") { runExtract(text) }
            }
        }
    }

    fun ask(text: String, mode: Int) {
        if (needsModel()) {
            say(Tone.Info, "Talking it through needs the on-device model", null, "Set up") {
                scope.launch { pagerState.animateScrollToPage(1) }
            }
            return
        }
        if (busy) {
            say(Tone.Info, "Still thinking about your last one")
            return
        }
        scope.launch { pagerState.animateScrollToPage(1) }
        scope.launch {
            busy = true
            val id = System.currentTimeMillis()
            liveEx = Exchange(id, mode, text, "", id)
            try {
                val hist = exchanges.takeLast(3).flatMap { listOf(Msg(true, it.ask), Msg(false, it.answer)) }
                val system = buildSystemPrompt(mode, ideas, bible, hist)
                val sb = StringBuilder()
                var spoken = 0
                LocalAi.reply(context, system, text) { piece ->
                    sb.append(piece)
                    liveEx = Exchange(id, mode, text, sb.toString(), id)
                    val m = Regex("[.!?\\n](\\s|$)").findAll(sb.substring(spoken)).lastOrNull()
                    if (m != null) {
                        val end = spoken + m.range.last + 1
                        speak(sb.substring(spoken, end))
                        spoken = end
                    }
                }
                if (spoken < sb.length) speak(sb.substring(spoken))
                val answer = sb.toString().trim()
                if (answer.isNotEmpty()) exchanges = threads.add(mode, text, answer)
                else say(Tone.Info, "No reply this time", "Try asking again.")
            } catch (e: Throwable) {
                say(Tone.Error, "The model hit a problem", e.message, "Retry") { ask(text, mode) }
            }
            liveEx = null
            busy = false
        }
    }

    fun finish(text: String, z: Int) {
        if (z == Z_CANCEL) return
        if (text.isBlank()) {
            say(Tone.Info, "Didn't catch that", "Hold the mic and try again.")
            return
        }
        if (z == Z_CAPTURE) {
            ideas = store.add(text)
            val added = ideas.maxByOrNull { it.id }
            scope.launch {
                var t = text
                val words = text.trim().split(Regex("\\s+")).size
                val marks = text.count { it == '.' || it == '?' || it == '!' }
                val flat = marks < maxOf(1, words / 30) || text.first().isLowerCase()
                if (AppSettings.tidyText && flat && added != null && !needsModel()) {
                    say(Tone.Working, "Tidying your transcript")
                    try {
                        t = LocalAi.punctuate(context, text)
                        ideas = store.update(added.id, t)
                    } catch (e: Throwable) {
                    }
                    if (notice?.text == "Tidying your transcript") notice = null
                }
                if (AppSettings.autoBible) runExtract(t)
            }
        } else ask(text, z)
    }

    val voice = remember {
        Listener(
            context = context,
            onPartial = { live = it },
            onFinal = { text ->
                recording = false
                live = ""
                finish(text, pendingZone[0])
                pendingZone[0] = Z_CAPTURE
            },
            onStatus = { s ->
                if (s.isEmpty()) {
                    if (notice?.tone == Tone.Working && notice?.text != "Updating your Bible") notice = null
                } else say(Tone.Working, s)
            },
            onError = { say(Tone.Error, "Couldn't capture audio", it) }
        )
    }
    DisposableEffect(Unit) { onDispose { voice.release() } }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) say(Tone.Error, "Microphone access is needed", "Allow it in Settings to record.")
    }

    fun startRec() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if (notice?.tone != Tone.Working) notice = null
        tts.value?.stop()
        SpeechOut.stop()
        zone = Z_CAPTURE
        drag = Offset.Zero
        pendingZone[0] = Z_CAPTURE
        recording = true
        voice.start()
    }

    fun stopRec(z: Int) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        pendingZone[0] = z
        recording = false
        voice.stop()
    }

    LaunchedEffect(Unit) {
        LocalAi.refresh(context)
        SpeechIn.prepare(context)
        SpeechOut.prepare(context)
    }

    val photoFile = remember { File(context.cacheDir, "page.jpg") }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) {
            say(Tone.Working, "Reading your page")
            scope.launch {
                try {
                    val prepared = withContext(Dispatchers.IO) {
                        prepareImage(photoFile, File(context.cacheDir, "page_small.jpg"))
                    }
                    val text = LocalAi.transcribe(context, prepared.absolutePath)
                    if (text.isNotBlank()) {
                        ideas = store.add(text)
                        notice = null
                        if (AppSettings.autoBible) runExtract(text)
                    } else {
                        say(Tone.Error, "Couldn't read that page", "Try again with better light and a flatter page.")
                    }
                } catch (e: Throwable) {
                    say(Tone.Error, "Couldn't read the page", e.message)
                }
            }
        }
    }

    fun photo() {
        if (needsModel()) {
            say(Tone.Info, "Page reading needs the on-device model", null, "Set up") {
                scope.launch { pagerState.animateScrollToPage(1) }
            }
            return
        }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", photoFile)
        camera.launch(uri)
    }

    LaunchedEffect(autoStart) {
        if (autoStart) {
            onAutoStartHandled()
            pagerState.scrollToPage(1)
            if (!recording) startRec()
        }
    }

    val sbTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val topPad = sbTop + 64.dp
    val bottomPad = 176.dp + navBottom

    Box(Modifier.fillMaxSize().background(c.paper)) {
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            userScrollEnabled = !recording,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            if (page == 0) {
                BibleScreen(
                    bible = bible,
                    top = topPad,
                    bottom = bottomPad,
                    onRemove = { bible = bibleStore.remove(it.id) },
                    onProbe = { e -> ask("Help me think through " + e.text, Z_QUESTION) }
                )
            } else {
                StreamPage(
                    ideas = ideas,
                    exchanges = exchanges,
                    live = liveEx,
                    top = topPad,
                    bottom = bottomPad,
                    onExtract = { runExtract(it.text) },
                    onDeleteIdea = { ideas = store.remove(it.id) },
                    onDeleteEx = { exchanges = threads.remove(it.id) }
                )
            }
        }

        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(0f to c.paper, 0.75f to c.paper.copy(alpha = 0.88f), 1f to Color.Transparent))
                .padding(top = sbTop, bottom = 12.dp)
        ) {
            NavBar(
                position = pagerState.currentPage + pagerState.currentPageOffsetFraction,
                onSelect = { scope.launch { pagerState.animateScrollToPage(it) } },
                onSettings = { showSettings = true }
            )
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(190.dp + navBottom)
                .background(Brush.verticalGradient(0f to Color.Transparent, 0.45f to c.paper.copy(alpha = 0.9f), 1f to c.paper))
        )

        HoldScrim(recording = recording, zone = zone, live = live, top = topPad)

        MicDock(
            recording = recording,
            zone = zone,
            drag = { drag },
            hint = dragUses < 3,
            onStart = {
                if (recording) {
                    latchedStop = true
                    stopRec(Z_CAPTURE)
                } else startRec()
            },
            onZone = { z ->
                zone = z
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            },
            onDrag = { drag = it },
            onEnd = { z ->
                if (latchedStop) latchedStop = false
                else if (recording) {
                    if (z in Z_QUESTION..Z_TEACH) {
                        dragUses += 1
                        prefs.edit().putInt("dragUses", dragUses).apply()
                    }
                    stopRec(z)
                }
            },
            onPhoto = { photo() },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = navBottom + 2.dp)
        )

        BackHandler(enabled = showSettings) { showSettings = false }
        if (showSettings) {
            SettingsScreen(
                top = sbTop + 10.dp,
                bottom = navBottom,
                onClose = { showSettings = false },
                onExport = {
                    val sb = StringBuilder("# Story Bible\n")
                    for (cat in BIBLE_CATEGORIES) {
                        val items = bible.filter { it.category == cat }
                        if (items.isEmpty()) continue
                        sb.append("\n## ").append(cat).append("\n")
                        for (e in items) sb.append("- ").append(e.text).append("\n")
                    }
                    sb.append("\n# Recordings\n")
                    val fmt = java.text.SimpleDateFormat("MMM d, yyyy h:mm a", java.util.Locale.getDefault())
                    for (i in ideas.sortedBy { it.createdAt }) {
                        sb.append("\n### ").append(fmt.format(java.util.Date(i.createdAt))).append("\n").append(i.text).append("\n")
                    }
                    val send = ShareIntent(ShareIntent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(ShareIntent.EXTRA_SUBJECT, "My story")
                        putExtra(ShareIntent.EXTRA_TEXT, sb.toString())
                    }
                    context.startActivity(ShareIntent.createChooser(send, "Export your story"))
                }
            )
        }

        NoticeBanner(
            notice = notice,
            onDismiss = { notice = null },
            modifier = Modifier.align(Alignment.TopCenter).padding(top = sbTop + 8.dp)
        )

        val pending = proposals
        if (pending != null) {
            ProposalDialog(
                proposals = pending,
                onToggle = { i -> proposals = pending.mapIndexed { j, p -> if (j == i) p.copy(on = !p.on) else p } },
                onConfirm = {
                    val chosen = pending.filter { it.on }
                    bible = bibleStore.commit(chosen)
                    proposals = null
                    val summary = listOf(Act.ADD to "added", Act.EDIT to "edited", Act.REMOVE to "removed")
                        .map { (act, word) -> chosen.count { it.act == act } to word }
                        .filter { it.first > 0 }
                        .joinToString(" · ") { it.first.toString() + " " + it.second }
                    say(Tone.Success, "Bible updated", summary)
                },
                onDismiss = { proposals = null }
            )
        }
    }
}
