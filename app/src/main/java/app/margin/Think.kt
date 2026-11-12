package app.margin

import android.Manifest
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

private val MODE_WORDS = listOf("Listen", "Question", "Critique", "Teach")

private val MODE_BLURBS = listOf(
    "Stay quiet and write down your thinking. No interruptions.",
    "One good question at a time, about your story.",
    "Honest notes on what is working and what is not.",
    "Craft and research answers, short and spoken."
)

private val MODE_INSTRUCTIONS = listOf(
    "Listen mode: stay quiet while I think out loud. Reply with only a few words of acknowledgement. When I say 'done', summarize my thinking and list any decisions and open questions.",
    "Question mode: ask me one good question at a time about my story. Do not give me answers or ideas unless I ask.",
    "Critique mode: give me honest, specific notes on what is working and what is not. Do not flatter me.",
    "Teach mode: answer my craft and research questions briefly, in a spoken style, and ask if I want to go deeper."
)

data class Msg(val mine: Boolean, val text: String)

fun buildSystemPrompt(modeIndex: Int, ideas: List<Idea>, bible: List<Entry>): String {
    val sb = StringBuilder()
    sb.append("You are a warm, sharp fiction-writing companion for an aspiring writer who prefers talking to typing. Keep every reply to one to three short sentences that are easy to say aloud. Never write their story for them.\n\n")
    sb.append(MODE_INSTRUCTIONS[modeIndex]).append("\n\n")
    var anyBible = false
    for (cat in BIBLE_CATEGORIES) {
        val items = bible.filter { it.category == cat }
        if (items.isNotEmpty()) {
            if (!anyBible) {
                sb.append("Their story bible so far:\n")
                anyBible = true
            }
            sb.append(cat).append(":\n")
            for (e in items) sb.append("- ").append(e.text).append("\n")
        }
    }
    if (anyBible) sb.append("\n")
    val recent = ideas.take(8)
    if (recent.isNotEmpty()) {
        sb.append("Recent loose ideas from their desk:\n")
        for (i in recent) sb.append("- ").append(i.text).append("\n")
    }
    return sb.toString()
}

@Composable
fun ThinkScreen(ideas: List<Idea>, bible: List<Entry>) {
    val c = LocalMarginColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ideasNow by rememberUpdatedState(ideas)
    val bibleNow by rememberUpdatedState(bible)

    var mode by remember { mutableIntStateOf(0) }
    var messages by remember { mutableStateOf(listOf<Msg>()) }
    var chat by remember { mutableStateOf<LocalAi.Chat?>(null) }
    var busy by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var buf by remember { mutableStateOf("") }
    var part by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val tts = remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(Unit) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) tts.value = engine
        }
        onDispose {
            engine?.shutdown()
            chat?.close()
        }
    }

    fun speak(text: String) {
        val clean = text.replace("*", "").replace("#", "").trim()
        if (clean.isNotEmpty()) tts.value?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "reply")
    }

    fun send(text: String) {
        if (text.isBlank() || busy) return
        scope.launch {
            busy = true
            note = ""
            try {
                val conv = chat ?: LocalAi.startChat(context, buildSystemPrompt(mode, ideasNow, bibleNow))
                chat = conv
                messages = messages + Msg(true, text) + Msg(false, "...")
                val sb = StringBuilder()
                conv.send(text).collect { piece ->
                    sb.append(piece)
                    messages = messages.dropLast(1) + Msg(false, sb.toString())
                }
                speak(sb.toString())
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
                listening = false
                val t = (buf + " " + part).trim()
                buf = ""
                part = ""
                send(t)
            },
            onError = { note = it }
        )
    }
    DisposableEffect(Unit) { onDispose { voice.release() } }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            listening = true
            voice.start()
        } else {
            note = "Microphone permission is needed to talk."
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) LocalAi.startImport(context, uri)
    }

    fun toggleTalk() {
        if (listening) {
            listening = false
            voice.stop()
            return
        }
        if (busy) return
        tts.value?.stop()
        note = ""
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            listening = true
            voice.start()
        } else {
            permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun pickMode(i: Int) {
        if (i == mode) return
        mode = i
        chat?.close()
        chat = null
        messages = emptyList()
        note = ""
    }

    val state = LocalAi.state
    val ready = state == AiState.Installed || state == AiState.Loading ||
        state == AiState.Ready || state == AiState.Failed

    val tints = listOf(
        Color(0x1A786E5F),
        Color(0x1AB8432A),
        Color(0x203C648C),
        Color(0x20467350)
    )
    val tint by animateColorAsState(tints[mode], tween(600), label = "tint")
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Box(Modifier.fillMaxSize().background(tint)) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "THINK WITH ME",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                color = c.graphite
            )
            Text(
                MODE_WORDS[mode],
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 38.sp,
                color = c.ink
            )
            Text(
                MODE_BLURBS[mode],
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                color = c.graphite
            )
            Row(Modifier.fillMaxWidth()) {
                MODE_WORDS.forEachIndexed { i, w ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clickable { pickMode(i) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            w,
                            fontSize = 15.sp,
                            fontWeight = if (mode == i) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (mode == i) c.ink else c.graphite
                        )
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(c.line)
            )

            if (!ready) {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Your writing companion runs entirely on this phone. It needs a one-time download of the Gemma 4 model (about 2.6 GB). Use Wi-Fi and keep the app open until it finishes.",
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
                        Text("Downloading...", fontSize = 14.sp, color = c.graphite)
                    } else {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .background(c.accent, RoundedCornerShape(16.dp))
                                .clickable { LocalAi.startDownload(context) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Download the model", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.onAccent)
                        }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clickable { importer.launch(arrayOf("*/*")) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Or import a model file", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.ink)
                        }
                    }
                    if (LocalAi.message.isNotEmpty()) {
                        Text(LocalAi.message, fontSize = 14.sp, lineHeight = 20.sp, color = c.accent)
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (messages.isEmpty()) {
                        item {
                            Text(
                                if (state == AiState.Failed) LocalAi.message else "Tap the button below and just start talking.",
                                fontSize = 15.sp,
                                lineHeight = 22.sp,
                                color = c.graphite
                            )
                        }
                    }
                    items(messages.size) { i ->
                        val m = messages[i]
                        Box(Modifier.fillMaxWidth(), contentAlignment = if (m.mine) Alignment.CenterEnd else Alignment.CenterStart) {
                            Text(
                                m.text,
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .background(
                                        if (m.mine) c.ink else c.card,
                                        RoundedCornerShape(18.dp)
                                    )
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                fontFamily = if (m.mine) FontFamily.SansSerif else FontFamily.Serif,
                                fontSize = 16.sp,
                                lineHeight = 22.sp,
                                color = if (m.mine) c.paper else c.ink
                            )
                        }
                    }
                }
            }

            if (note.isNotEmpty()) {
                Text(note, fontSize = 13.sp, lineHeight = 18.sp, color = c.accent)
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .background(if (ready) c.accent else c.line, RoundedCornerShape(18.dp))
                    .clickable(enabled = ready) { toggleTalk() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (!ready) "Download the model first"
                    else if (listening) "Listening. Tap to stop."
                    else if (busy) "Thinking..."
                    else "Tap to talk",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (ready) c.onAccent else c.graphite
                )
            }
        }
    }
}
