package app.margin

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RoomTabs(position: Float, onSelect: (Int) -> Unit) {
    val c = LocalMarginColors.current
    val names = listOf("Desk", "Page", "Think")
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            names.forEachIndexed { i, name ->
                val selected = kotlin.math.abs(position - i) < 0.5f
                Box(
                    Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clickable { onSelect(i) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = name,
                        fontSize = 16.sp,
                        fontFamily = if (selected) FontFamily.Serif else FontFamily.SansSerif,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (selected) c.ink else c.graphite
                    )
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(2.dp).background(c.line)) {
            val w = maxWidth / 3
            Box(
                Modifier
                    .offset(x = w * position)
                    .width(w)
                    .height(2.dp)
                    .background(c.accent)
            )
        }
    }
}

@Composable
private fun Rings(active: Boolean, color: Color) {
    if (!active) return
    val t = rememberInfiniteTransition(label = "rings")
    val p by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "p"
    )
    Box(
        Modifier
            .size(132.dp)
            .graphicsLayer {
                scaleX = 1f + p * 1.1f
                scaleY = 1f + p * 1.1f
                alpha = 0.45f * (1f - p)
            }
            .background(color, CircleShape)
    )
}

@Composable
private fun MicIcon(color: Color) {
    Canvas(Modifier.size(44.dp)) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.07f
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.375f, h * 0.10f),
            size = Size(w * 0.25f, h * 0.46f),
            cornerRadius = CornerRadius(w * 0.125f, w * 0.125f)
        )
        drawArc(
            color = color,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.24f, h * 0.26f),
            size = Size(w * 0.52f, h * 0.46f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawLine(
            color = color,
            start = Offset(w * 0.5f, h * 0.72f),
            end = Offset(w * 0.5f, h * 0.88f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun PageScreen(recording: Boolean, live: String, message: String?, onToggle: () -> Unit, onPhoto: () -> Unit) {
    val c = LocalMarginColors.current
    val date = remember { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date()) }
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 26.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            date,
            modifier = Modifier.fillMaxWidth(),
            fontFamily = FontFamily.Serif,
            fontStyle = FontStyle.Italic,
            fontSize = 16.sp,
            color = c.graphite
        )
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.CenterStart
        ) {
            if (recording && live.isNotBlank()) {
                Text(
                    live,
                    fontFamily = FontFamily.Cursive,
                    fontSize = 32.sp,
                    lineHeight = 38.sp,
                    color = c.ink
                )
            } else {
                Text(
                    "Say it out loud,\nor just breathe.",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Medium,
                    fontSize = 34.sp,
                    lineHeight = 41.sp,
                    color = c.ink
                )
            }
        }
        Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
            Rings(recording, c.accent)
            Box(
                Modifier
                    .size(132.dp)
                    .shadow(10.dp, CircleShape)
                    .background(c.accent, CircleShape)
                    .clip(CircleShape)
                    .clickable { onToggle() }
                    .semantics { contentDescription = "Record an idea" },
                contentAlignment = Alignment.Center
            ) {
                MicIcon(c.onAccent)
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            if (recording) "Listening. Tap to stop." else "Tap to begin",
            fontSize = 14.sp,
            color = c.graphite
        )
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .height(48.dp)
                .border(1.5.dp, c.line, RoundedCornerShape(999.dp))
                .clickable { onPhoto() }
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Photograph a pencil page", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = c.ink)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            message ?: "",
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            color = c.accent
        )
    }
}
