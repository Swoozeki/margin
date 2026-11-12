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
import kotlinx.coroutines.launch

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

    var ideas by remember { mutableStateOf(store.load()) }
    var bible by remember { mutableStateOf(bibleStore.load()) }
    var recording by remember { mutableStateOf(false) }
    var buffer by remember { mutableStateOf("") }
    var partial by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    val voice = remember {
        VoiceCapture(
            context = context,
            onPartial = { partial = it },
            onSegment = { seg ->
                buffer = (buffer + " " + seg).trim()
                partial = ""
            },
            onDone = {
                recording = false
                val text = (buffer + " " + partial).trim()
                if (text.isNotEmpty()) {
                    ideas = store.add(text)
                    message = "Saved to your Desk."
                }
                buffer = ""
                partial = ""
            },
            onError = { message = it }
        )
    }
    DisposableEffect(Unit) { onDispose { voice.release() } }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            message = null
            recording = true
            voice.start()
        } else {
            message = "Microphone permission is needed to capture ideas by voice."
        }
    }

    fun toggle() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if (recording) {
            recording = false
            voice.stop()
        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            message = null
            recording = true
            voice.start()
        } else {
            permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val pagerState = rememberPagerState(initialPage = 1) { 3 }
    val scope = rememberCoroutineScope()

    LaunchedEffect(autoStart) {
        if (autoStart) {
            onAutoStartHandled()
            pagerState.scrollToPage(1)
            if (!recording) toggle()
        }
    }

    var firstSettle by remember { mutableStateOf(true) }
    LaunchedEffect(pagerState.settledPage) {
        if (firstSettle) {
            firstSettle = false
        } else {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.paper)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        RoomTabs(
            position = pagerState.currentPage + pagerState.currentPageOffsetFraction,
            onSelect = { scope.launch { pagerState.animateScrollToPage(it) } }
        )
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            when (page) {
                0 -> DeskScreen(
                    ideas = ideas,
                    bible = bible,
                    onFile = { idea, category ->
                        bible = bibleStore.add(category, idea.text)
                        ideas = store.remove(idea.id)
                    },
                    onDelete = { idea -> ideas = store.remove(idea.id) },
                    onRemoveEntry = { entry -> bible = bibleStore.remove(entry.id) }
                )
                1 -> PageScreen(
                    recording = recording,
                    live = (buffer + " " + partial).trim(),
                    message = message,
                    onToggle = { toggle() }
                )
                else -> ThinkScreen(ideas, bible)
            }
        }
    }
}
