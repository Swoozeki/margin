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
    sb.append("You are a warm, sharp fiction-writing companion for an aspiring writer who prefers talking to typing. Talk like a real person in a relaxed conversation: contractions, natural rhythm, a brief reaction before your point. Keep every reply to one to three short sentences. No lists, no markdown, no emojis. Never write their story for them.\n\n")
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
fun WorkshopScreen(
    ideas: List<Idea>,
    bible: List<Entry>,
    top: androidx.compose.ui.unit.Dp,
    bottom: androidx.compose.ui.unit.Dp,
    onHold: (Boolean) -> Unit
) {
    val c = LocalMarginColors.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val ideasNow by rememberUpdatedState(ideas)
    val bibleNow by rememberUpdatedState(bible)

    var messages by remember { mutableStateOf(listOf<Msg>()) }
    var talkMode by remember { mutableIntStateOf(-1) }
    var holding by remember { mutableStateOf(false) }
    var lastMode by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var part by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val tts = remember { mutableStateOf<TextToSpeech?>(null) }

    LaunchedEffect(holding) { onHold(holding) }
    LaunchedEffect(Unit) {
        SpeechOut.prepare(context)
        SpeechIn.prepare(context)
    }

    DisposableEffect(Unit) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { st ->
            if (st == TextToSpeech.SUCCESS) tts.value = engine
        }
        onDispose { engine?.shutdown() }
    }

    fun say(sentence: String) {
        val clean = sentence.replace("*", "").replace("#", "").replace("_", " ").trim()
        if (clean.length < 2) return
        if (SpeechOut.ready) SpeechOut.speak(clean)
        else tts.value?.speak(clean, TextToSpeech.QUEUE_ADD, null, "reply")
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
                var spoken = 0
                LocalAi.reply(context, system, text) { piece ->
                    sb.append(piece)
                    messages = messages.dropLast(1) + Msg(false, sb.toString())
                    if (mode != 0) {
                        val m = Regex("[.!?\\n](\\s|$)").findAll(sb.substring(spoken)).lastOrNull()
                        if (m != null) {
                            val end = spoken + m.range.last + 1
                            say(sb.substring(spoken, end))
                            spoken = end
                        }
                    }
                }
                if (mode != 0 && spoken < sb.length) say(sb.substring(spoken))
            } catch (e: Throwable) {
                note = "The model hit a problem: " + (e.message ?: "unknown error")
            }
            busy = false
        }
    }

    val listener = remember {
        Listener(
            context = context,
            onPartial = { part = it },
            onFinal = { t ->
                val mode = talkMode
                talkMode = -1
                part = ""
                if (mode >= 0) send(t, mode)
            },
            onStatus = { status = it },
            onError = { note = it }
        )
    }
    DisposableEffect(Unit) { onDispose { listener.release() } }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) note = "Microphone permission is needed to talk."
    }

    fun begin(mode: Int) {
        if (busy || holding) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        tts.value?.stop()
        SpeechOut.stop()
        note = ""
        part = ""
        talkMode = mode
        lastMode = mode
        holding = true
        listener.start()
    }

    fun end() {
        if (holding) {
            holding = false
            listener.stop()
        }
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
    LaunchedEffect(messages.size, messages.lastOrNull()?.text, part) {
        val n = messages.size + (if (part.isNotEmpty()) 1 else 0)
        if (n > 0) listState.scrollToItem(n - 1)
    }

    val voiceNote = when {
        SpeechIn.downloading -> "Downloading speech model " + (SpeechIn.progress * 100).toInt() + "%"
        else -> ""
    }
    val line = if (note.isNotEmpty()) note else if (status.isNotEmpty()) status else if (busy) "Thinking" else voiceNote

    Box(Modifier.fillMaxSize().background(tint)) {
        if (!ready) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(start = 20.dp, end = 20.dp, top = top + 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Doodle(R.drawable.art_coffee_time, 170.dp)
                Text(
                    "One-time download of the on-device model. Use Wi-Fi.",
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
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, top = top + 4.dp, bottom = 130.dp + bottom
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "scene") { androidx.compose.animation.Crossfade(lastMode, label = "mode") { Doodle(MODE_DOODLES[it], 160.dp) } }
                if (LocalAi.canUpgrade(context)) {
                    item {
                        Text(
                            "Get the larger, smarter model (3.7 GB)",
                            modifier = Modifier
                                .clickable { LocalAi.startDownload(context) }
                                .padding(vertical = 6.dp),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = c.accent
                        )
                    }
                }
                items(messages.size) { i ->
                    val m = messages[i]
                    if (m.mine) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                            Text(
                                m.text,
                                modifier = Modifier
                                    .fillMaxWidth(0.82f)
                                    .background(c.ink, RoundedCornerShape(18.dp))
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                fontSize = 15.sp,
                                lineHeight = 21.sp,
                                color = c.paper
                            )
                        }
                    } else {
                        Text(
                            m.text,
                            modifier = Modifier.fillMaxWidth(0.92f).padding(horizontal = 4.dp),
                            fontFamily = FontFamily.Serif,
                            fontSize = 18.sp,
                            lineHeight = 26.sp,
                            color = c.ink
                        )
                    }
                }
                if (part.isNotEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                            Text(
                                part,
                                modifier = Modifier
                                    .fillMaxWidth(0.82f)
                                    .border(1.5.dp, c.line, RoundedCornerShape(18.dp))
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                fontStyle = FontStyle.Italic,
                                fontSize = 15.sp,
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

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        0f to Color.Transparent, 0.35f to c.paper.copy(alpha = 0.9f), 1f to c.paper
                    )
                )
                .padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = bottom + 14.dp)
        ) {
            Text(
                line,
                modifier = Modifier.fillMaxWidth().height(22.dp).padding(horizontal = 6.dp),
                fontSize = 13.sp,
                color = if (note.isNotEmpty()) c.accent else c.graphite
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(20.dp))
            ) {
                MODE_WORDS.forEachIndexed { i, w ->
                    val on = holding && talkMode == i
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
