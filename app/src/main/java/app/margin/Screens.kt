package app.margin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

const val Z_CAPTURE = 0
const val Z_QUESTION = 1
const val Z_CRITIQUE = 2
const val Z_TEACH = 3
const val Z_CANCEL = 4

fun zoneOf(dx: Float, dy: Float, dead: Float): Int {
    if (dx * dx + dy * dy < dead * dead) return Z_CAPTURE
    return if (abs(dx) > abs(dy)) {
        if (dx < 0) Z_CRITIQUE else Z_TEACH
    } else {
        if (dy < 0) Z_QUESTION else Z_CANCEL
    }
}

/**
 * Press and hold. Dragging away from where the finger landed picks a direction
 * (up, left, right, down); letting go reports the last one.
 */
fun Modifier.directionalHold(
    onStart: () -> Unit,
    onZone: (Int) -> Unit,
    onDrag: (Offset) -> Unit,
    onEnd: (Int) -> Unit
): Modifier = composed {
    val s = rememberUpdatedState(onStart)
    val z = rememberUpdatedState(onZone)
    val d = rememberUpdatedState(onDrag)
    val e = rememberUpdatedState(onEnd)
    pointerInput(Unit) {
        val dead = 46.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            down.consume()
            s.value()
            var zone = Z_CAPTURE
            var clean = false
            try {
                while (true) {
                    val ev = awaitPointerEvent()
                    val ch = ev.changes.firstOrNull { it.id == down.id }
                    if (ch == null) { clean = true; break }
                    ch.consume()
                    val off = ch.position - down.position
                    d.value(off)
                    val nz = zoneOf(off.x, off.y, dead)
                    if (nz != zone) { zone = nz; z.value(nz) }
                    if (!ch.pressed) { clean = true; break }
                }
            } finally {
                d.value(Offset.Zero)
                e.value(if (clean) zone else Z_CANCEL)
            }
        }
    }
}

/** 0 book, 1 mic, 2 chat, 3 camera, 4 person, 5 pin, 6 thread, 7 question, 8 alert, 9 check, 10 info, 11 pencil, 12 bulb, 13 close, 14 gear */
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
            3 -> {
                drawRoundRect(color, Offset(s * .08f, s * .28f), Size(s * .84f, s * .58f), CornerRadius(s * .12f), style = st)
                drawCircle(color, s * .16f, Offset(s * .5f, s * .57f), style = st)
                drawLine(color, Offset(s * .36f, s * .18f), Offset(s * .64f, s * .18f), w, StrokeCap.Round)
            }
            4 -> {
                drawCircle(color, s * .16f, Offset(s * .5f, s * .3f), style = st)
                drawArc(color, 180f, 180f, false, Offset(s * .2f, s * .56f), Size(s * .6f, s * .6f), style = st)
            }
            5 -> {
                drawCircle(color, s * .26f, Offset(s * .5f, s * .38f), style = st)
                drawLine(color, Offset(s * .32f, s * .56f), Offset(s * .5f, s * .9f), w, StrokeCap.Round)
                drawLine(color, Offset(s * .68f, s * .56f), Offset(s * .5f, s * .9f), w, StrokeCap.Round)
                drawCircle(color, s * .07f, Offset(s * .5f, s * .38f))
            }
            6 -> {
                val p = androidx.compose.ui.graphics.Path()
                p.moveTo(s * .14f, s * .66f)
                p.cubicTo(s * .3f, s * .2f, s * .45f, s * .2f, s * .5f, s * .5f)
                p.cubicTo(s * .55f, s * .8f, s * .7f, s * .8f, s * .86f, s * .34f)
                drawPath(p, color, style = st)
                drawCircle(color, s * .06f, Offset(s * .14f, s * .66f))
                drawCircle(color, s * .06f, Offset(s * .86f, s * .34f))
            }
            7 -> {
                drawArc(color, 180f, 230f, false, Offset(s * .28f, s * .1f), Size(s * .44f, s * .4f), style = st)
                drawLine(color, Offset(s * .5f, s * .5f), Offset(s * .5f, s * .66f), w, StrokeCap.Round)
                drawCircle(color, s * .055f, Offset(s * .5f, s * .86f))
            }
            8 -> {
                drawCircle(color, s * .38f, Offset(s * .5f, s * .5f), style = st)
                drawLine(color, Offset(s * .5f, s * .28f), Offset(s * .5f, s * .54f), w, StrokeCap.Round)
                drawCircle(color, s * .05f, Offset(s * .5f, s * .7f))
            }
            9 -> {
                drawCircle(color, s * .38f, Offset(s * .5f, s * .5f), style = st)
                drawLine(color, Offset(s * .32f, s * .5f), Offset(s * .45f, s * .63f), w, StrokeCap.Round)
                drawLine(color, Offset(s * .45f, s * .63f), Offset(s * .69f, s * .37f), w, StrokeCap.Round)
            }
            11 -> {
                drawLine(color, Offset(s * .3f, s * .7f), Offset(s * .7f, s * .3f), w * 2.6f, StrokeCap.Round)
                drawLine(color, Offset(s * .16f, s * .84f), Offset(s * .3f, s * .7f), w * 1.2f, StrokeCap.Round)
                drawLine(color, Offset(s * .5f, s * .9f), Offset(s * .88f, s * .9f), w, StrokeCap.Round)
            }
            12 -> {
                drawCircle(color, s * .24f, Offset(s * .5f, s * .4f), style = st)
                drawLine(color, Offset(s * .4f, s * .6f), Offset(s * .4f, s * .74f), w, StrokeCap.Round)
                drawLine(color, Offset(s * .6f, s * .6f), Offset(s * .6f, s * .74f), w, StrokeCap.Round)
                drawLine(color, Offset(s * .4f, s * .74f), Offset(s * .6f, s * .74f), w, StrokeCap.Round)
                drawLine(color, Offset(s * .43f, s * .88f), Offset(s * .57f, s * .88f), w, StrokeCap.Round)
            }
            13 -> {
                drawLine(color, Offset(s * .24f, s * .24f), Offset(s * .76f, s * .76f), w * 1.3f, StrokeCap.Round)
                drawLine(color, Offset(s * .76f, s * .24f), Offset(s * .24f, s * .76f), w * 1.3f, StrokeCap.Round)
            }
            14 -> {
                drawCircle(color, s * .24f, Offset(s * .5f, s * .5f), style = st)
                drawCircle(color, s * .07f, Offset(s * .5f, s * .5f))
                for (k in 0 until 8) {
                    val ang = k * Math.PI / 4
                    val dx = Math.cos(ang).toFloat()
                    val dy = Math.sin(ang).toFloat()
                    drawLine(
                        color,
                        Offset(s * .5f + dx * s * .32f, s * .5f + dy * s * .32f),
                        Offset(s * .5f + dx * s * .43f, s * .5f + dy * s * .43f),
                        w * 1.5f, StrokeCap.Round
                    )
                }
            }
            else -> {
                drawCircle(color, s * .38f, Offset(s * .5f, s * .5f), style = st)
                drawCircle(color, s * .05f, Offset(s * .5f, s * .3f))
                drawLine(color, Offset(s * .5f, s * .45f), Offset(s * .5f, s * .72f), w, StrokeCap.Round)
            }
        }
    }
}


private val DOCK_W = 340.dp
private val DOCK_H = 312.dp
private val MIC_CX = 170.dp
private val MIC_CY = 214.dp
private val CUE_W = 108.dp
private val CUE_H = 36.dp

private val CAPTIONS = listOf(
    "Capture" to "Let go to save to your story",
    "Question" to "Ask me, or think aloud and I\u2019ll probe",
    "Critique" to "Honest notes on what you say",
    "Teach" to "Craft and research, explained",
    "Cancel" to "Let go to discard"
)

@Composable
fun HoldScrim(recording: Boolean, zone: Int, live: String, top: Dp) {
    val c = LocalMarginColors.current
    AnimatedVisibility(recording, enter = fadeIn(tween(160)), exit = fadeOut(tween(160))) {
        Box(Modifier.fillMaxSize().background(c.paper.copy(alpha = 0.95f))) {
            Column(Modifier.padding(start = 26.dp, end = 26.dp, top = top + 10.dp)) {
                val cap = CAPTIONS[zone]
                Text(
                    cap.first.uppercase(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.sp,
                    color = if (zone == Z_CANCEL) errorTone(c) else c.accent
                )
                Text(cap.second, fontSize = 13.sp, color = c.graphite, modifier = Modifier.padding(top = 2.dp, bottom = 16.dp))
                Text(
                    if (live.isEmpty()) "Listening\u2026" else live.takeLast(280),
                    fontFamily = FontFamily.Cursive,
                    fontSize = if (live.isEmpty()) 24.sp else 28.sp,
                    lineHeight = 38.sp,
                    color = if (live.isEmpty()) c.graphite else c.ink
                )
            }
        }
    }
}

@Composable
private fun Cue(label: String, glyph: Int, active: Boolean, danger: Boolean, fan: Float, modifier: Modifier) {
    val c = LocalMarginColors.current
    val sc by animateFloatAsState(if (active) 1.14f else 1f, tween(120), label = "cue")
    val bg = when {
        active && danger -> errorTone(c)
        active -> c.accent
        else -> c.card
    }
    val fg = if (active) (if (danger) Color.White else c.onAccent) else c.ink
    Row(
        modifier
            .size(CUE_W, CUE_H)
            .graphicsLayer {
                alpha = fan
                val k = sc * (0.7f + 0.3f * fan)
                scaleX = k
                scaleY = k
            }
            .shadow(if (active) 8.dp else 2.dp, CircleShape)
            .background(bg, CircleShape)
            .border(1.dp, if (active) bg else c.line, CircleShape),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Glyph(glyph, fg, 16.dp)
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = fg)
    }
}

@Composable
fun MicDock(
    recording: Boolean,
    zone: Int,
    drag: () -> Offset,
    hint: Boolean,
    onStart: () -> Unit,
    onZone: (Int) -> Unit,
    onDrag: (Offset) -> Unit,
    onEnd: (Int) -> Unit,
    onPhoto: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalMarginColors.current
    val pulse = rememberInfiniteTransition(label = "mic")
    val ring by pulse.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1700, easing = LinearEasing), RepeatMode.Restart),
        label = "ring"
    )
    val fan by animateFloatAsState(if (recording) 1f else 0f, tween(200), label = "fan")
    val grow by animateFloatAsState(if (recording) 1.14f else 1f, tween(160), label = "grow")
    val danger = recording && zone == Z_CANCEL
    val micColor by animateColorAsState(if (danger) errorTone(c) else c.accent, tween(150), label = "micColor")

    Box(modifier.size(DOCK_W, DOCK_H)) {
        if (fan > 0.01f) {
            Cue("Question", 7, zone == Z_QUESTION, false, fan, Modifier.offset(MIC_CX - CUE_W / 2, MIC_CY - 122.dp - CUE_H / 2))
            Cue("Critique", 11, zone == Z_CRITIQUE, false, fan, Modifier.offset(MIC_CX - 112.dp - CUE_W / 2, MIC_CY - CUE_H / 2))
            Cue("Teach", 12, zone == Z_TEACH, false, fan, Modifier.offset(MIC_CX + 112.dp - CUE_W / 2, MIC_CY - CUE_H / 2))
            Cue("Cancel", 13, zone == Z_CANCEL, true, fan, Modifier.offset(MIC_CX - CUE_W / 2, MIC_CY + 70.dp - CUE_H / 2))
        }

        if (!recording && hint) {
            Text(
                "Hold to record \u00b7 drag to talk it through",
                modifier = Modifier.fillMaxWidth().offset(y = MIC_CY - 82.dp),
                fontSize = 12.sp,
                color = c.graphite,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        Box(
            Modifier
                .offset(26.dp, MIC_CY - 26.dp)
                .size(52.dp)
                .graphicsLayer { alpha = 1f - fan }
                .border(1.5.dp, c.line, CircleShape)
                .clip(CircleShape)
                .semantics { contentDescription = "Photograph a pencil page" }
                .clickable(enabled = !recording) { onPhoto() },
            contentAlignment = Alignment.Center
        ) { Glyph(3, c.ink, 24.dp) }

        // halo: slow breathing when idle, strong pulses while recording
        for (k in 0..1) {
            val p = (ring + k * 0.5f) % 1f
            val a = if (recording) (1f - p) * 0.28f else if (k == 0) (1f - p) * 0.10f else 0f
            val sz = 80.dp + (if (recording) 74.dp else 34.dp) * p
            Box(
                Modifier
                    .offset(MIC_CX - sz / 2, MIC_CY - sz / 2)
                    .size(sz)
                    .background(micColor.copy(alpha = a), CircleShape)
            )
        }

        Box(
            Modifier
                .offset(MIC_CX - 48.dp, MIC_CY - 48.dp)
                .size(96.dp)
                .graphicsLayer {
                    val o = drag()
                    val m = 26.dp.toPx()
                    translationX = (o.x * 0.3f).coerceIn(-m, m)
                    translationY = (o.y * 0.3f).coerceIn(-m, m)
                }
                .semantics { contentDescription = "Hold to record. Drag up for a question, left for critique, right to be taught." }
                .directionalHold(onStart, onZone, onDrag, onEnd),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(80.dp)
                    .graphicsLayer { scaleX = grow; scaleY = grow }
                    .shadow(if (recording) 14.dp else 8.dp, CircleShape)
                    .background(micColor, CircleShape),
                contentAlignment = Alignment.Center
            ) { Glyph(if (danger) 13 else 1, c.onAccent, 34.dp) }
        }
    }
}

private val NAV_NAMES = listOf("Bible", "Workshop")
private val NAV_GLYPHS = listOf(0, 2)

@Composable
fun NavBar(position: Float, onSelect: (Int) -> Unit, onSettings: () -> Unit) {
    val c = LocalMarginColors.current
    val cell = 42.dp
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .width(cell * 2 + 8.dp)
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
                for (i in 0..1) {
                    Box(
                        Modifier
                            .size(cell)
                            .clip(CircleShape)
                            .semantics { contentDescription = NAV_NAMES[i] }
                            .clickable { onSelect(i) },
                        contentAlignment = Alignment.Center
                    ) {
                        Glyph(NAV_GLYPHS[i], if (kotlin.math.round(position).toInt() == i) c.onAccent else c.graphite)
                    }
                }
            }
        }
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
                .size(42.dp)
                .border(1.dp, c.line, CircleShape)
                .clip(CircleShape)
                .semantics { contentDescription = "Settings" }
                .clickable { onSettings() },
            contentAlignment = Alignment.Center
        ) { Glyph(14, c.graphite, 20.dp) }
    }
}
