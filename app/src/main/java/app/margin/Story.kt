package app.margin

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun clock(t: Long): String = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(t))

private fun dayLabel(t: Long): String {
    val now = java.util.Calendar.getInstance()
    val then = java.util.Calendar.getInstance().apply { timeInMillis = t }
    val days = ((now.get(java.util.Calendar.YEAR) - then.get(java.util.Calendar.YEAR)) * 366 +
        now.get(java.util.Calendar.DAY_OF_YEAR) - then.get(java.util.Calendar.DAY_OF_YEAR))
    return when (days) {
        0 -> "Today"
        1 -> "Yesterday"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(t))
    }
}

/** First sentence as a title, the rest as the body. */
private fun titleAndBody(text: String): Pair<String, String> {
    val t = text.trim()
    val m = Regex("[.!?](\\s|$)").find(t)
    if (m != null && m.range.first in 8..70) {
        val title = t.substring(0, m.range.first).trim()
        return title to t.substring(m.range.last + 1).trim()
    }
    val title = if (t.length > 56) t.take(56).trimEnd() + "\u2026" else t
    return title to (if (t.length > 56) t else "")
}


private sealed class TLItem(val at: Long) {
    class Rec(val idea: Idea) : TLItem(idea.createdAt)
    class Ex(val ex: Exchange) : TLItem(ex.createdAt)
}

@Composable
fun StreamPage(
    ideas: List<Idea>,
    exchanges: List<Exchange>,
    live: Exchange?,
    top: Dp,
    bottom: Dp,
    onExtract: (Idea) -> Unit,
    onDeleteIdea: (Idea) -> Unit,
    onDeleteEx: (Exchange) -> Unit
) {
    val c = LocalMarginColors.current
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var open by remember { mutableStateOf<String?>(null) }

    val timeline = remember(ideas, exchanges) {
        (ideas.map { TLItem.Rec(it) as TLItem } + exchanges.map { TLItem.Ex(it) }).sortedByDescending { it.at }
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) LocalAi.startImport(context, uri)
    }
    val state = LocalAi.state
    val ready = state == AiState.Installed || state == AiState.Loading ||
        state == AiState.Ready || state == AiState.Failed

    LaunchedEffect(live?.id) { if (live != null) listState.animateScrollToItem(0) }

    LazyColumn(
        state = listState,
        reverseLayout = true,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = RAIL_INSET, end = 18.dp, top = top, bottom = bottom),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (live != null) {
            item(key = "live") { ExchangeCard(live, false, {}, {}) }
        }
        for ((i, t) in timeline.withIndex()) {
            when (t) {
                is TLItem.Rec -> item(key = "r" + t.idea.id) {
                    RecCard(
                        t.idea,
                        open == "r" + t.idea.id,
                        { open = if (open == "r" + t.idea.id) null else "r" + t.idea.id },
                        { onExtract(t.idea) },
                        { onDeleteIdea(t.idea) }
                    )
                }
                is TLItem.Ex -> item(key = "e" + t.ex.id) {
                    ExchangeCard(
                        t.ex,
                        open == "e" + t.ex.id,
                        { open = if (open == "e" + t.ex.id) null else "e" + t.ex.id },
                        { onDeleteEx(t.ex) }
                    )
                }
            }
            val label = dayLabel(t.at)
            val next = timeline.getOrNull(i + 1)
            if (next == null || dayLabel(next.at) != label) {
                item(key = "d_" + label) {
                    Text(
                        label.uppercase(),
                        modifier = Modifier.padding(start = 4.dp, top = 10.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.5.sp,
                        color = c.accent
                    )
                }
            }
        }
        if (timeline.isEmpty() && live == null) {
            item(key = "empty") {
                Text(
                    "Hold the mic and tell me about your story.",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    fontSize = 17.sp,
                    lineHeight = 24.sp,
                    textAlign = TextAlign.Center,
                    color = c.graphite
                )
            }
        }
        if (ready && LocalAi.canUpgrade(context)) {
            item(key = "upgrade") {
                Text(
                    "Get the larger, smarter model (3.7 GB)",
                    modifier = Modifier.clickable { LocalAi.startDownload(context) }.padding(vertical = 6.dp, horizontal = 4.dp),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = c.accent
                )
            }
        }
        if (SpeechIn.downloading) {
            item(key = "voice") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Downloading voice model " + (SpeechIn.progress * 100).toInt() + "%",
                        fontSize = 13.sp,
                        color = c.graphite
                    )
                    LinearProgressIndicator(
                        progress = { SpeechIn.progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = c.accent,
                        trackColor = c.line
                    )
                }
            }
        }
        if (!ready) {
            item(key = "setup") {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Doodle(R.drawable.art_coffee_time, 150.dp)
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
                                .height(44.dp)
                                .clickable { importer.launch(arrayOf("*/*")) },
                            contentAlignment = Alignment.Center
                        ) { Text("Import a model file", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.ink) }
                    }
                    if (LocalAi.message.isNotEmpty()) {
                        Text(LocalAi.message, fontSize = 14.sp, lineHeight = 20.sp, color = c.accent)
                    }
                }
            }
        }
        item(key = "scene") { Doodle(R.drawable.art_book_writer, 170.dp) }
    }
}

@Composable
private fun RecCard(idea: Idea, open: Boolean, onToggle: () -> Unit, onExtract: () -> Unit, onDelete: () -> Unit) {
    val c = LocalMarginColors.current
    val tb = remember(idea.text) { titleAndBody(idea.text) }
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.card, RoundedCornerShape(16.dp))
            .clickable { onToggle() }
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Glyph(1, c.graphite, 14.dp)
            Text(clock(idea.createdAt), modifier = Modifier.padding(start = 6.dp), fontSize = 12.sp, color = c.graphite)
        }
        Text(
            tb.first,
            modifier = Modifier.padding(top = 6.dp),
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp,
            lineHeight = 23.sp,
            maxLines = if (open) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis,
            color = c.ink
        )
        if (tb.second.isNotEmpty()) {
            Text(
                tb.second,
                modifier = Modifier.padding(top = 4.dp),
                fontFamily = FontFamily.Serif,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                maxLines = if (open) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                color = c.graphite
            )
        }
        if (open) {
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                Text(
                    "Update Bible",
                    modifier = Modifier.clickable { onExtract() }.padding(vertical = 6.dp),
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.accent
                )
                Text(
                    "Delete",
                    modifier = Modifier.clickable { onDelete() }.padding(vertical = 6.dp),
                    fontSize = 14.sp, fontWeight = FontWeight.Medium, color = c.graphite
                )
            }
        }
    }
}

@Composable
private fun ExchangeCard(ex: Exchange, open: Boolean, onToggle: () -> Unit, onDelete: () -> Unit) {
    val c = LocalMarginColors.current
    Column(
        Modifier.fillMaxWidth().clickable(enabled = ex.id > 0) { onToggle() },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
            Text(
                MODE_WORDS[ex.mode.coerceIn(1, 3)].uppercase(),
                modifier = Modifier.padding(end = 6.dp, bottom = 3.dp),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp,
                color = c.graphite
            )
            Text(
                ex.ask,
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .background(c.ink, RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                fontSize = 15.sp,
                lineHeight = 21.sp,
                color = c.paper
            )
        }
        Row(Modifier.fillMaxWidth(0.94f).height(IntrinsicSize.Min)) {
            Box(Modifier.width(2.dp).fillMaxHeight().background(c.accent.copy(alpha = 0.55f), RoundedCornerShape(1.dp)))
            Text(
                if (ex.answer.isEmpty()) "…" else ex.answer,
                modifier = Modifier.padding(start = 12.dp),
                fontFamily = FontFamily.Serif,
                fontSize = 18.sp,
                lineHeight = 26.sp,
                color = c.ink
            )
        }
        if (open) {
            Text(
                "Delete",
                modifier = Modifier.clickable { onDelete() }.padding(start = 14.dp, top = 2.dp, bottom = 4.dp),
                fontSize = 14.sp, fontWeight = FontWeight.Medium, color = c.graphite
            )
        }
    }
}
