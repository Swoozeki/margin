package app.margin

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Press and hold: onStart when the finger lands, onEnd when it lifts or is cancelled. */
fun Modifier.holdToTalk(onStart: () -> Unit, onEnd: () -> Unit): Modifier = composed {
    val start = rememberUpdatedState(onStart)
    val end = rememberUpdatedState(onEnd)
    pointerInput(Unit) {
        detectTapGestures(onPress = {
            start.value()
            tryAwaitRelease()
            end.value()
        })
    }
}

/** 0 = book, 1 = mic, 2 = chat, 3 = camera */
@Composable
fun Glyph(kind: Int, color: Color, size: androidx.compose.ui.unit.Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.width
        val w = s * 0.085f
        val st = Stroke(width = w, cap = StrokeCap.Round)
        when (kind) {
            0 -> {
                drawRoundRect(color, Offset(s * .18f, s * .12f), Size(s * .64f, s * .76f), CornerRadius(s * .08f), style = st)
                drawLine(color, Offset(s * .34f, s * .12f), Offset(s * .34f, s * .88f), w, StrokeCap.Round)
                drawLine(color, Offset(s * .48f, s * .36f), Offset(s * .68f, s * .36f), w, StrokeCap.Round)
            }
            1 -> {
                drawRoundRect(color, Offset(s * .36f, s * .08f), Size(s * .28f, s * .5f), CornerRadius(s * .14f), style = st)
                drawArc(color, 0f, 180f, false, Offset(s * .22f, s * .3f), Size(s * .56f, s * .46f), style = st)
                drawLine(color, Offset(s * .5f, s * .76f), Offset(s * .5f, s * .92f), w, StrokeCap.Round)
            }
            2 -> {
                drawRoundRect(color, Offset(s * .1f, s * .14f), Size(s * .8f, s * .54f), CornerRadius(s * .16f), style = st)
                drawLine(color, Offset(s * .32f, s * .68f), Offset(s * .26f, s * .88f), w, StrokeCap.Round)
                drawLine(color, Offset(s * .26f, s * .88f), Offset(s * .5f, s * .68f), w, StrokeCap.Round)
            }
            else -> {
                drawRoundRect(color, Offset(s * .08f, s * .28f), Size(s * .84f, s * .58f), CornerRadius(s * .12f), style = st)
                drawCircle(color, s * .16f, Offset(s * .5f, s * .57f), style = st)
                drawLine(color, Offset(s * .36f, s * .18f), Offset(s * .64f, s * .18f), w, StrokeCap.Round)
            }
        }
    }
}

private val NAV_NAMES = listOf("Bible", "Record", "Workshop")
private val NAV_GLYPHS = listOf(0, 1, 2)

@Composable
fun NavBar(position: Float, onSelect: (Int) -> Unit) {
    val c = LocalMarginColors.current
    val cell = 42.dp
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .width(cell * 3 + 8.dp)
                .height(cell + 8.dp)
                .background(c.card, CircleShape)
                .border(1.dp, c.line, CircleShape)
        ) {
            Box(
                Modifier
                    .offset(x = 4.dp + cell * position, y = 4.dp)
                    .size(cell)
                    .background(c.accent, CircleShape)
            )
            Row(Modifier.padding(4.dp)) {
                for (i in 0..2) {
                    Box(
                        Modifier
                            .size(cell)
                            .clip(CircleShape)
                            .semantics { contentDescription = NAV_NAMES[i] }
                            .clickable { onSelect(i) },
                        contentAlignment = Alignment.Center
                    ) {
                        Glyph(NAV_GLYPHS[i], if (position.roundToInt() == i) c.onAccent else c.graphite)
                    }
                }
            }
        }
    }
}

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

@Composable
fun RecordScreen(
    recording: Boolean,
    live: String,
    status: String?,
    history: List<Idea>,
    top: androidx.compose.ui.unit.Dp,
    bottom: androidx.compose.ui.unit.Dp,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    onPhoto: () -> Unit,
    onDelete: (Idea) -> Unit,
    onExtract: (Idea) -> Unit
) {
    val c = LocalMarginColors.current
    var open by remember { mutableStateOf<Long?>(null) }
    val pulse = rememberInfiniteTransition(label = "rings")
    val ring by pulse.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "ring"
    )
    val grouped = remember(history) { history.groupBy { dayLabel(it.createdAt) } }

    Box(Modifier.fillMaxSize()) {
        if (recording) {
            Text(
                if (live.isEmpty()) "Listening" else live,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = top + 12.dp),
                fontFamily = FontFamily.Cursive,
                fontSize = if (live.isEmpty()) 22.sp else 28.sp,
                lineHeight = 36.sp,
                color = if (live.isEmpty()) c.graphite else c.ink
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp, end = 20.dp, top = top, bottom = 200.dp + bottom
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "scene") { Doodle(R.drawable.art_sittingreading, 180.dp) }
                for ((label, group) in grouped) {
                    item(key = "d_" + label) {
                        Text(
                            label.uppercase(),
                            modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.sp,
                            color = c.graphite
                        )
                    }
                    items(group, key = { it.id }) { idea ->
                        val expanded = open == idea.id
                        val (title, body) = remember(idea.id) { titleAndBody(idea.text) }
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(c.card, RoundedCornerShape(14.dp))
                                .clickable { open = if (expanded) null else idea.id }
                                .padding(horizontal = 14.dp, vertical = 11.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    title,
                                    modifier = Modifier.weight(1f),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    color = c.ink
                                )
                                Text(clock(idea.createdAt), modifier = Modifier.padding(start = 8.dp), fontSize = 12.sp, color = c.graphite)
                            }
                            if (body.isNotEmpty()) {
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    body,
                                    fontFamily = FontFamily.Serif,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                                    color = c.graphite
                                )
                            }
                            if (expanded) {
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                                    Text(
                                        "Update Bible",
                                        modifier = Modifier.clickable { onExtract(idea) }.padding(vertical = 6.dp),
                                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.accent
                                    )
                                    Text(
                                        "Delete",
                                        modifier = Modifier.clickable { onDelete(idea) }.padding(vertical = 6.dp),
                                        fontSize = 14.sp, fontWeight = FontWeight.Medium, color = c.graphite
                                    )
                                }
                            }
                        }
                    }
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
                .padding(top = 28.dp, bottom = bottom + 14.dp)
        ) {
            Text(
                status ?: "",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp)
                    .padding(horizontal = 24.dp),
                fontSize = 13.sp,
                color = c.accent
            )
            Box(Modifier.fillMaxWidth().height(120.dp)) {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 28.dp)
                        .size(52.dp)
                        .border(1.5.dp, c.line, CircleShape)
                        .clip(CircleShape)
                        .semantics { contentDescription = "Photograph a pencil page" }
                        .clickable { onPhoto() },
                    contentAlignment = Alignment.Center
                ) { Glyph(3, c.ink, 24.dp) }

                Box(Modifier.align(Alignment.Center).size(120.dp), contentAlignment = Alignment.Center) {
                    if (recording) {
                        for (k in 0..1) {
                            val p = (ring + k * 0.5f) % 1f
                            Box(
                                Modifier
                                    .size(104.dp)
                                    .graphicsLayer {
                                        scaleX = 1f + p * 0.16f
                                        scaleY = 1f + p * 0.16f
                                        alpha = 0.35f * (1f - p)
                                    }
                                    .background(c.accent, CircleShape)
                            )
                        }
                    }
                    Box(
                        Modifier
                            .size(if (recording) 112.dp else 104.dp)
                            .background(c.accent, CircleShape)
                            .semantics { contentDescription = "Hold to record" }
                            .holdToTalk(onPress, onRelease),
                        contentAlignment = Alignment.Center
                    ) { Glyph(1, c.onAccent, 44.dp) }
                }
            }
        }
    }
}
