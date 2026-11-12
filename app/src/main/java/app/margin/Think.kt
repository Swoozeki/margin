package app.margin

import android.Manifest
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

private val MODE_WORDS = listOf("Listen", "Question", "Critique", "Teach")

private val MODE_INSTRUCTIONS = listOf(
    "Listen mode: I am thinking out loud. Reply with only a few words of acknowledgement. If I say 'done', summarize my thinking and list decisions and open questions.",
    "Question mode: ask me one good question at a time about my story. Do not give answers or ideas unless I ask.",
    "Critique mode: give me honest, specific notes on what is working and what is not. Do not flatter me.",
    "Teach mode: answer my craft and research question briefly, in a spoken style."
)

data class Msg(val mine: Boolean, val text: String)

fun buildSystemPrompt(mode: Int, ideas: List<Idea>, bible: List<Entry>, history: List<Msg>): String {
    val sb = StringBuilder()
    sb.append("You are a warm, sharp fiction-writing companion for an aspiring writer who prefers talking to typing. Keep every reply to one to three short sentences that are easy to say aloud. Never write their story for them.\n\n")
    sb.append(MODE_INSTRUCTIONS[mode]).append("\n\n")
    var any = false
    for (cat in BIBLE_CATEGORIES) {
        val items = bible.filter { it.category == cat }
        if (items.isEmpty()) continue
        if (!any) {
            sb.append("Their story bible:\n")
            any = true
        }
        sb.append(cat).append(":\n")
        for (e in items.takeLast(15)) sb.append("- ").append(e.text).append("\n")
    }
    if (any) sb.append("\n")
    val recent = ideas.take(4)
    if (recent.isNotEmpty()) {
        sb.append("Their latest recordings:\n")
        for (i in recent) sb.append("- ").append(i.text.take(300)).append("\n")
        sb.append("\n")
    }
    if (history.isNotEmpty()) {
        sb.append("Conversation so far:\n")
        for (m in history) sb.append(if (m.mine) "Writer: " else "You: ").append(m.text.take(400)).append("\n")
    }
    return sb.toString()
}

@Composable
fun WorkshopScreen(ideas: List<Idea>, bible: List<Entry>) {
    val c = LocalMarginColors.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val ideasNow by rememberUpdatedState(ideas)
    val bibleNow by rememberUpdatedState(bible)

    var messages by remember { mutableStateOf(listOf<Msg>()) }
    var talkMode by remember { mutableIntStateOf(-1) }
    var lastMode by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var buf by remember { mutableStateOf("") }
    var part by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val tts = remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(Unit) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) tts.value = engine
        }
        onDispose { engine?.shutdown() }
    }

    fun send(text: String, mode: Int) {
        if (text.isBlank()) return
        scope.launch {
            busy = true
            note = ""
            try {
                val system = buildSystemPrompt(mode, ideasNow, bibleNow, messages.takeLast(6))
                messages = messages + Msg(true, text) + Msg(false, "...")
                val sb = StringBuilder()
                LocalAi.reply(context, system, text) { piece ->
                    sb.append(piece)
                    messages = messages.dropLast(1) + Msg(false, sb.toString())
                }
                if (mode != 0) {
                    val clean = sb.toString().replace("*", "").replace("#", "").trim()
                    if (clean.isNotEmpty()) tts.value?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "reply")
                }
            } catch (e: Throwable) {
                note = "The model hit a problem: " + (e.message ?: "unknown error")
            }
            busy = false
        }
    }

    val voice = remember {
        VoiceCapture(
            context = context,
            onPartial = { part = it },
            onSegment = { seg ->
                buf = (buf + " " + seg).trim()
                part = ""
            },
            onDone = {
                val mode = talkMode
                talkMode = -1
                val t = (buf + " " + part).trim()
                buf = ""
                part = ""
                if (mode >= 0) send(t, mode)
            },
            onError = { note = it }
        )
    }
    DisposableEffect(Unit) { onDispose { voice.release() } }

    var pendingMode by remember { mutableIntStateOf(-1) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) note = "Microphone permission is needed to talk."
        pendingMode = -1
    }

    fun begin(mode: Int) {
        if (busy) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        tts.value?.stop()
        note = ""
        buf = ""
        part = ""
        talkMode = mode
        lastMode = mode
        voice.start()
    }

    fun end() {
        if (talkMode >= 0) voice.stop()
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) LocalAi.startImport(context, uri)
    }

    val state = LocalAi.state
    val ready = state == AiState.Installed || state == AiState.Loading ||
        state == AiState.Ready || state == AiState.Failed

    val tints = listOf(Color(0x1A786E5F), Color(0x1AB8432A), Color(0x203C648C), Color(0x20467350))
    val tint by animateColorAsState(tints[lastMode], tween(600), label = "tint")
    val listState = rememberLazyListState()
    val live = (buf + " " + part).trim()
    LaunchedEffect(messages.size, messages.lastOrNull()?.text, live) {
        val n = messages.size + (if (live.isNotEmpty()) 1 else 0)
        if (n > 0) listState.scrollToItem(n - 1)
    }

    Box(Modifier.fillMaxSize().background(tint)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            if (!ready) {
                Column(
                    Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "One-time download of the on-device model, about 2.6 GB. Use Wi-Fi.",
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        color = c.graphite
                    )
                    if (state == AiState.Downloading) {
                        LinearProgressIndicator(
                            progress = { LocalAi.progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = c.accent,
                            trackColor = c.line
                        )
                    } else {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .background(c.accent, RoundedCornerShape(16.dp))
                                .clickable { LocalAi.startDownload(context) },
                            contentAlignment = Alignment.Center
                        ) { Text("Download model", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.onAccent) }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clickable { importer.launch(arrayOf("*/*")) },
                            contentAlignment = Alignment.Center
                        ) { Text("Import a model file", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.ink) }
                    }
                    if (LocalAi.message.isNotEmpty()) {
                        Text(LocalAi.message, fontSize = 14.sp, lineHeight = 20.sp, color = c.accent)
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages.size) { i ->
                        val m = messages[i]
                        Box(
                            Modifier.fillMaxWidth(),
                            contentAlignment = if (m.mine) Alignment.CenterEnd else Alignment.CenterStart
                        ) {
                            Text(
                                m.text,
                                modifier = Modifier
                                    .fillMaxWidth(0.88f)
                                    .background(if (m.mine) c.ink else c.card, RoundedCornerShape(18.dp))
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                fontFamily = if (m.mine) FontFamily.SansSerif else FontFamily.Serif,
                                fontSize = 16.sp,
                                lineHeight = 22.sp,
                                color = if (m.mine) c.paper else c.ink
                            )
                        }
                    }
                    if (live.isNotEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                                Text(
                                    live,
                                    modifier = Modifier
                                        .fillMaxWidth(0.88f)
                                        .border(1.5.dp, c.line, RoundedCornerShape(18.dp))
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    fontStyle = FontStyle.Italic,
                                    fontSize = 16.sp,
                                    lineHeight = 22.sp,
                                    color = c.graphite
                                )
                            }
                        }
                    }
                    if (state == AiState.Failed && messages.isEmpty()) {
                        item { Text(LocalAi.message, fontSize = 14.sp, color = c.accent) }
                    }
                }
            }

            Text(
                if (note.isNotEmpty()) note else if (busy) "Thinking" else "",
                modifier = Modifier.fillMaxWidth().height(22.dp).padding(horizontal = 6.dp),
                fontSize = 13.sp,
                color = if (note.isNotEmpty()) c.accent else c.graphite
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 22.dp)
                    .height(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(20.dp))
            ) {
                MODE_WORDS.forEachIndexed { i, w ->
                    val on = talkMode == i
                    if (i > 0) Box(Modifier.width(1.dp).fillMaxSize().padding(vertical = 14.dp).background(c.line))
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .background(if (on) c.accent else Color.Transparent)
                            .holdToTalk({ if (ready) begin(i) }, { end() }),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            w,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            color = if (on) c.onAccent else if (ready) c.ink else c.graphite
                        )
                    }
                }
            }
        }
    }
}
