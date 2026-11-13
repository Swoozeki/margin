package app.margin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class Tone { Working, Info, Success, Error }

data class Notice(
    val id: Long,
    val tone: Tone,
    val text: String,
    val detail: String? = null,
    val action: String? = null,
    val onAction: (() -> Unit)? = null
)

fun errorTone(c: MarginColors): Color =
    if (c.paper.luminance() < 0.5f) Color(0xFFD9806D) else Color(0xFFA23B2B)

@Composable
fun Spinner(color: Color, size: Dp = 20.dp) {
    val t = rememberInfiniteTransition(label = "spin")
    val a by t.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "a"
    )
    Canvas(Modifier.size(size)) {
        val s = this.size.width
        val w = s * 0.11f
        val tl = Offset(w / 2, w / 2)
        val sz = Size(s - w, s - w)
        drawArc(color.copy(alpha = 0.25f), 0f, 360f, false, tl, sz, style = Stroke(w))
        drawArc(color, a, 100f, false, tl, sz, style = Stroke(w, cap = StrokeCap.Round))
    }
}

@Composable
fun NoticeBanner(notice: Notice?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalMarginColors.current
    val last = remember { mutableStateOf(notice) }
    if (notice != null) last.value = notice
    AnimatedVisibility(
        visible = notice != null,
        modifier = modifier,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut()
    ) {
        val n = last.value
        if (n != null) {
            val bg = if (n.tone == Tone.Error) errorTone(c) else c.ink
            val fg = if (n.tone == Tone.Error) Color.White else c.paper
            val shape = RoundedCornerShape(18.dp)
            val act = n.onAction
            Row(
                Modifier
                    .padding(horizontal = 14.dp)
                    .fillMaxWidth()
                    .shadow(8.dp, shape)
                    .background(bg, shape)
                    .pointerInput(n.id) {
                        detectVerticalDragGestures { _, d -> if (d < -12f && n.tone != Tone.Working) onDismiss() }
                    }
                    .clickable(enabled = n.tone != Tone.Working) { onDismiss() }
                    .padding(start = 14.dp, end = 10.dp, top = 11.dp, bottom = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (n.tone) {
                    Tone.Working -> Spinner(fg)
                    Tone.Success -> Glyph(9, fg, 20.dp)
                    Tone.Error -> Glyph(8, fg, 20.dp)
                    Tone.Info -> Glyph(10, fg, 20.dp)
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(n.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = fg)
                    if (!n.detail.isNullOrBlank()) {
                        Text(
                            n.detail,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = fg.copy(alpha = 0.78f)
                        )
                    }
                }
                if (n.action != null && act != null) {
                    Box(
                        Modifier
                            .padding(start = 8.dp)
                            .height(34.dp)
                            .border(1.dp, fg.copy(alpha = 0.5f), CircleShape)
                            .clickable { act(); onDismiss() }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) { Text(n.action, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = fg) }
                }
            }
        }
    }
}
