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
    val scope = rememberCoroutineScope()

    var ideas by remember { mutableStateOf(store.load()) }
    var bible by remember { mutableStateOf(bibleStore.load()) }
    var recording by remember { mutableStateOf(false) }
    var latchedStop by remember { mutableStateOf(false) }
    var live by remember { mutableStateOf("") }
    var wsHold by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var extracting by remember { mutableStateOf(false) }
    var proposals by remember { mutableStateOf<List<Proposal>?>(null) }

    fun runExtract(text: String) {
        if (LocalAi.state == AiState.Missing || LocalAi.state == AiState.Downloading) {
            message = "Set up the model in Workshop to fill your Bible automatically."
            return
        }
        scope.launch {
            extracting = true
            try {
                val found = LocalAi.extractBible(context, text, bibleStore.load())
                if (found.isEmpty()) message = "Nothing new for the Bible."
                else proposals = found.map { Proposal(it.first, it.second) }
            } catch (e: Throwable) {
                message = "Couldn't update the Bible: " + (e.message ?: "unknown error")
            }
            extracting = false
        }
    }

    val voice = remember {
        Listener(
            context = context,
            onPartial = { live = it },
            onFinal = { text ->
                recording = false
                live = ""
                if (text.isNotEmpty()) {
                    ideas = store.add(text)
                    runExtract(text)
                }
            },
            onStatus = { message = it.ifEmpty { null } },
            onError = { message = it }
        )
    }
    DisposableEffect(Unit) { onDispose { voice.release() } }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) message = "Microphone permission is needed to record."
    }

    fun startRec() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        message = null
        recording = true
        voice.start()
    }

    fun stopRec() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        recording = false
        voice.stop()
    }

    LaunchedEffect(Unit) {
        LocalAi.refresh(context)
        SpeechIn.prepare(context)
        SpeechOut.prepare(context)
    }

    val pagerState = rememberPagerState(initialPage = 1) { 3 }

    val photoFile = remember { File(context.cacheDir, "page.jpg") }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) {
            message = "Reading your page"
            scope.launch {
                try {
                    val prepared = withContext(Dispatchers.IO) {
                        prepareImage(photoFile, File(context.cacheDir, "page_small.jpg"))
                    }
                    val text = LocalAi.transcribe(context, prepared.absolutePath)
                    if (text.isNotBlank()) {
                        ideas = store.add(text)
                        message = null
                        runExtract(text)
                    } else {
                        message = "Couldn't read that page."
                    }
                } catch (e: Throwable) {
                    message = "Couldn't read the page: " + (e.message ?: "unknown error")
                }
            }
        }
    }

    fun photo() {
        if (LocalAi.state == AiState.Missing || LocalAi.state == AiState.Downloading) {
            message = "Set up the model in Workshop first."
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

    var firstSettle by remember { mutableStateOf(true) }
    LaunchedEffect(pagerState.settledPage) {
        if (firstSettle) firstSettle = false
        else haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    val sbTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val topPad = sbTop + 62.dp

    Box(Modifier.fillMaxSize().background(c.paper)) {
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 2,
            userScrollEnabled = !(recording || wsHold),
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> BibleScreen(bible, topPad, navBottom) { entry -> bible = bibleStore.remove(entry.id) }
                1 -> RecordScreen(
                    recording = recording,
                    live = live,
                    status = if (extracting) "Updating your Bible" else message,
                    history = ideas,
                    top = topPad,
                    bottom = navBottom,
                    onPress = {
                        if (recording) {
                            latchedStop = true
                            stopRec()
                        } else startRec()
                    },
                    onRelease = {
                        if (latchedStop) latchedStop = false
                        else if (recording) stopRec()
                    },
                    onPhoto = { photo() },
                    onDelete = { idea -> ideas = store.remove(idea.id) },
                    onExtract = { idea -> runExtract(idea.text) }
                )
                else -> WorkshopScreen(ideas, bible, topPad, navBottom) { wsHold = it }
            }
        }
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to c.paper, 0.75f to c.paper.copy(alpha = 0.85f), 1f to Color.Transparent
                    )
                )
                .padding(top = sbTop, bottom = 12.dp)
        ) {
            NavBar(
                position = pagerState.currentPage + pagerState.currentPageOffsetFraction,
                onSelect = { scope.launch { pagerState.animateScrollToPage(it) } }
            )
        }
        val pending = proposals
        if (pending != null) {
            ProposalDialog(
                proposals = pending,
                onToggle = { i -> proposals = pending.mapIndexed { j, p -> if (j == i) p.copy(on = !p.on) else p } },
                onConfirm = {
                    bible = bibleStore.addAll(pending.filter { it.on }.map { it.category to it.text })
                    proposals = null
                },
                onDismiss = { proposals = null }
            )
        }
    }
}
