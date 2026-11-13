package app.margin

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

private val Skin1 = Color(0xFFE3AB84)
private val Skin2 = Color(0xFF9C6644)
private val Skin3 = Color(0xFFF1CBA6)
private val Hair1 = Color(0xFF2B2622)
private val Hair2 = Color(0xFF6B3F25)
private val Hair3 = Color(0xFFB5895A)
private val Sage = Color(0xFF7A8F6B)
private val Blue = Color(0xFF5E7C96)
private val Mustard = Color(0xFFD9A441)
private val Wood = Color(0xFF8A6A4F)
private val Denim = Color(0xFF3B4252)
private val Face = Color(0xFF2B2622)

@Composable
fun rememberT(): State<Float> {
    val tr = rememberInfiniteTransition(label = "art")
    return tr.animateFloat(0f, 1f, infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart), label = "t")
}

private fun wave(t: Float, phase: Float = 0f): Float = sin(((t + phase) * 2f * PI).toFloat())

private fun DrawScope.sparkle(c: Offset, r: Float, color: Color) {
    val p = Path()
    p.moveTo(c.x, c.y - r)
    p.quadraticTo(c.x, c.y, c.x + r, c.y)
    p.quadraticTo(c.x, c.y, c.x, c.y + r)
    p.quadraticTo(c.x, c.y, c.x - r, c.y)
    p.quadraticTo(c.x, c.y, c.x, c.y - r)
    p.close()
    drawPath(p, color)
}

private fun DrawScope.questionMark(c: Offset, r: Float, color: Color) {
    val st = Stroke(width = r * 0.38f, cap = StrokeCap.Round)
    drawArc(color, -200f, 230f, false, Offset(c.x - r * 0.55f, c.y - r), Size(r * 1.1f, r * 1.1f), style = st)
    drawLine(color, Offset(c.x, c.y + r * 0.1f), Offset(c.x, c.y + r * 0.38f), r * 0.38f, StrokeCap.Round)
    drawCircle(color, r * 0.2f, Offset(c.x, c.y + r * 0.9f))
}

/** Flat, friendly figure. [h] is full standing height; seated figures end at [baseY] (hidden behind a desk). */
private fun DrawScope.person(
    cx: Float, baseY: Float, h: Float,
    skin: Color, hair: Color, top: Color, pants: Color,
    hairStyle: Int, seated: Boolean = false,
    lHand: Offset? = null, rHand: Offset? = null
) {
    val r = h * 0.13f
    val headY = baseY - h + r
    val shoulderY = headY + r + h * 0.06f
    val torsoH = h * 0.36f
    val torsoW = h * 0.30f
    val hipY = shoulderY + torsoH
    if (!seated) {
        val lw = h * 0.10f
        drawRoundRect(pants, Offset(cx - torsoW * 0.42f, hipY - 4f), Size(lw, baseY - hipY + 4f), CornerRadius(lw / 2))
        drawRoundRect(pants, Offset(cx + torsoW * 0.42f - lw, hipY - 4f), Size(lw, baseY - hipY + 4f), CornerRadius(lw / 2))
    }
    if (hairStyle == 1) {
        drawRoundRect(hair, Offset(cx - r * 1.08f, headY - r * 0.3f), Size(r * 2.16f, r * 3.1f), CornerRadius(r * 0.9f))
    }
    val bottom = if (seated) baseY else hipY
    drawRoundRect(top, Offset(cx - torsoW / 2, shoulderY), Size(torsoW, bottom - shoulderY), CornerRadius(h * 0.09f))
    drawRect(skin, Offset(cx - r * 0.28f, headY + r * 0.7f), Size(r * 0.56f, shoulderY - headY - r * 0.7f + 4f))
    val sw = h * 0.075f
    val ls = Offset(cx - torsoW / 2 + sw * 0.5f, shoulderY + sw)
    val rs = Offset(cx + torsoW / 2 - sw * 0.5f, shoulderY + sw)
    val lh = lHand ?: Offset(ls.x - h * 0.03f, ls.y + torsoH * 0.85f)
    val rh = rHand ?: Offset(rs.x + h * 0.03f, rs.y + torsoH * 0.85f)
    drawLine(top, ls, lh, sw, StrokeCap.Round)
    drawLine(top, rs, rh, sw, StrokeCap.Round)
    drawCircle(skin, sw * 0.62f, lh)
    drawCircle(skin, sw * 0.62f, rh)
    drawCircle(skin, r, Offset(cx, headY))
    drawArc(hair, 180f, 180f, true, Offset(cx - r * 1.06f, headY - r * 1.06f), Size(r * 2.12f, r * 1.7f))
    if (hairStyle == 2) drawCircle(hair, r * 0.42f, Offset(cx, headY - r * 1.15f))
    drawCircle(Face, r * 0.08f, Offset(cx - r * 0.36f, headY + r * 0.08f))
    drawCircle(Face, r * 0.08f, Offset(cx + r * 0.36f, headY + r * 0.08f))
    drawArc(Face, 20f, 140f, false, Offset(cx - r * 0.3f, headY + r * 0.12f), Size(r * 0.6f, r * 0.42f),
        style = Stroke(width = r * 0.07f, cap = StrokeCap.Round))
}

private fun DrawScope.plant(x: Float, baseY: Float, s: Float, pot: Color) {
    drawRoundRect(pot, Offset(x - s * 0.5f, baseY - s * 0.7f), Size(s, s * 0.7f), CornerRadius(s * 0.14f))
    for ((dx, dy, a) in listOf(Triple(-0.3f, -1.25f, -25f), Triple(0f, -1.5f, 0f), Triple(0.3f, -1.2f, 25f))) {
        rotate(a, Offset(x + dx * s, baseY - s * 0.7f)) {
            drawOval(Sage, Offset(x + dx * s - s * 0.16f, baseY + dy * s), Size(s * 0.32f, s * 0.78f))
        }
    }
}

private fun DrawScope.bubble(x: Float, y: Float, w: Float, h: Float, fill: Color, line: Color, tailLeft: Boolean) {
    drawRoundRect(fill, Offset(x, y), Size(w, h), CornerRadius(h * 0.4f))
    drawRoundRect(line, Offset(x, y), Size(w, h), CornerRadius(h * 0.4f), style = Stroke(2f))
    val p = Path()
    val tx = if (tailLeft) x + w * 0.2f else x + w * 0.8f
    p.moveTo(tx - 8f, y + h - 1f)
    p.lineTo(tx + (if (tailLeft) -6f else 6f), y + h + 12f)
    p.lineTo(tx + 8f, y + h - 1f)
    p.close()
    drawPath(p, fill)
}

/** Record: a writer at a desk by a window, ideas floating up. */
@Composable
fun WriterScene(height: Dp = 170.dp) {
    val c = LocalMarginColors.current
    val t = rememberT()
    Canvas(Modifier.fillMaxWidth().height(height)) {
        val w = size.width
        val h = size.height
        val tt = t.value
        val bob = wave(tt)
        drawCircle(c.accent.copy(alpha = 0.10f), h * 0.52f, Offset(w * 0.5f, h * 0.5f))

        // window with moon
        val wx = w * 0.07f
        val wy = h * 0.10f
        drawRoundRect(c.card, Offset(wx, wy), Size(w * 0.2f, h * 0.46f), CornerRadius(10f))
        drawRoundRect(c.line, Offset(wx, wy), Size(w * 0.2f, h * 0.46f), CornerRadius(10f), style = Stroke(3f))
        drawLine(c.line, Offset(wx + w * 0.1f, wy), Offset(wx + w * 0.1f, wy + h * 0.46f), 3f)
        drawLine(c.line, Offset(wx, wy + h * 0.23f), Offset(wx + w * 0.2f, wy + h * 0.23f), 3f)
        drawCircle(Mustard, h * 0.06f, Offset(wx + w * 0.05f, wy + h * 0.12f))
        sparkle(Offset(wx + w * 0.15f, wy + h * 0.34f), 7f + 2f * bob, Mustard)

        // desk
        val deskY = h * 0.80f
        drawRoundRect(Wood, Offset(w * 0.10f, deskY), Size(w * 0.80f, h * 0.05f), CornerRadius(8f))
        drawRect(Wood, Offset(w * 0.14f, deskY + h * 0.05f), Size(w * 0.025f, h * 0.15f))
        drawRect(Wood, Offset(w * 0.835f, deskY + h * 0.05f), Size(w * 0.025f, h * 0.15f))

        // books
        drawRoundRect(Blue, Offset(w * 0.17f, deskY - h * 0.06f), Size(w * 0.13f, h * 0.06f), CornerRadius(4f))
        drawRoundRect(c.accent, Offset(w * 0.185f, deskY - h * 0.115f), Size(w * 0.11f, h * 0.055f), CornerRadius(4f))

        // writer
        val handL = Offset(w * 0.49f, deskY - h * 0.02f)
        val handR = Offset(w * 0.60f + bob * 3f, deskY - h * 0.03f)
        person(w * 0.54f, deskY, h * 0.62f, Skin1, Hair2, c.accent, Denim, 0, true, handL, handR)
        // notebook
        drawRoundRect(c.card, Offset(w * 0.47f, deskY - h * 0.035f), Size(w * 0.17f, h * 0.035f), CornerRadius(4f))
        drawRoundRect(c.line, Offset(w * 0.47f, deskY - h * 0.035f), Size(w * 0.17f, h * 0.035f), CornerRadius(4f), style = Stroke(2f))
        // pencil
        rotate(-35f + bob * 4f, handR) {
            drawLine(Mustard, handR, Offset(handR.x + w * 0.07f, handR.y), 6f, StrokeCap.Round)
            drawLine(c.ink, handR, Offset(handR.x - 6f, handR.y), 4f, StrokeCap.Round)
        }

        // lamp with glow
        val lx = w * 0.80f
        drawCircle(Mustard.copy(alpha = 0.16f + 0.08f * bob), h * 0.26f, Offset(lx - w * 0.04f, h * 0.55f))
        drawLine(c.graphite, Offset(lx, deskY), Offset(lx - w * 0.01f, h * 0.50f), 4f, StrokeCap.Round)
        drawLine(c.graphite, Offset(lx - w * 0.01f, h * 0.50f), Offset(lx - w * 0.07f, h * 0.44f), 4f, StrokeCap.Round)
        val shade = Path()
        shade.moveTo(lx - w * 0.10f, h * 0.43f)
        shade.lineTo(lx - w * 0.03f, h * 0.43f)
        shade.lineTo(lx - w * 0.01f, h * 0.55f)
        shade.lineTo(lx - w * 0.12f, h * 0.55f)
        shade.close()
        drawPath(shade, Mustard)
        plant(w * 0.92f, deskY, w * 0.05f, c.accent)

        // floating ideas
        sparkle(Offset(w * 0.40f, h * 0.18f + 5f * wave(tt, 0.0f)), 11f, c.accent)
        sparkle(Offset(w * 0.64f, h * 0.10f + 5f * wave(tt, 0.3f)), 8f, Mustard)
        sparkle(Offset(w * 0.72f, h * 0.30f + 5f * wave(tt, 0.6f)), 6f, Blue)
    }
}

/** Workshop: the writer talks with a companion; the speech bubble changes with the mode. */
@Composable
fun TalkScene(mode: Int, height: Dp = 150.dp) {
    val c = LocalMarginColors.current
    val t = rememberT()
    Canvas(Modifier.fillMaxWidth().height(height)) {
        val w = size.width
        val h = size.height
        val bob = wave(t.value)
        val ground = h * 0.94f
        drawCircle(c.accent.copy(alpha = 0.08f), h * 0.55f, Offset(w * 0.5f, h * 0.55f))
        drawRoundRect(c.line, Offset(w * 0.12f, ground), Size(w * 0.76f, 4f), CornerRadius(2f))
        person(w * 0.30f, ground, h * 0.70f, Skin2, Hair1, Blue, Denim, 0,
            rHand = Offset(w * 0.38f, h * 0.50f + 3f * bob))
        person(w * 0.70f, ground, h * 0.70f, Skin3, Hair3, Mustard, Denim, 2,
            lHand = Offset(w * 0.62f, h * 0.46f - 3f * bob))
        plant(w * 0.07f, ground, w * 0.05f, c.accent)
        plant(w * 0.93f, ground, w * 0.045f, Blue)

        // writer bubble
        bubble(w * 0.14f, h * 0.02f + 2f * bob, w * 0.26f, h * 0.30f, c.card, c.line, true)
        for (i in 0..1) drawLine(c.graphite, Offset(w * 0.17f, h * 0.11f + i * h * 0.1f + 2f * bob),
            Offset(w * (0.33f - i * 0.04f), h * 0.11f + i * h * 0.1f + 2f * bob), 4f, StrokeCap.Round)
        // companion bubble per mode
        val bx = w * 0.58f
        val by = h * 0.02f - 2f * bob
        bubble(bx, by, w * 0.28f, h * 0.30f, c.card, c.line, false)
        val mid = Offset(bx + w * 0.14f, by + h * 0.15f)
        when (mode) {
            0 -> for (i in -1..1) drawCircle(c.graphite, 5f, Offset(mid.x + i * 16f, mid.y + 3f * wave(t.value, i * 0.15f)))
            1 -> questionMark(Offset(mid.x, mid.y - h * 0.04f), h * 0.07f, c.accent)
            2 -> {
                drawCircle(c.accent, h * 0.07f, Offset(mid.x - 4f, mid.y - 3f), style = Stroke(4f))
                drawLine(c.accent, Offset(mid.x + h * 0.05f, mid.y + h * 0.05f), Offset(mid.x + h * 0.11f, mid.y + h * 0.11f), 5f, StrokeCap.Round)
            }
            else -> {
                drawRoundRect(Sage, Offset(mid.x - h * 0.11f, mid.y - h * 0.07f), Size(h * 0.1f, h * 0.14f), CornerRadius(4f))
                drawRoundRect(Sage, Offset(mid.x + h * 0.01f, mid.y - h * 0.07f), Size(h * 0.1f, h * 0.14f), CornerRadius(4f))
            }
        }
    }
}

/** Bible: one small scene per category. */
@Composable
fun BibleScene(category: Int, height: Dp = 120.dp) {
    val c = LocalMarginColors.current
    val t = rememberT()
    Canvas(Modifier.fillMaxWidth().height(height)) {
        val w = size.width
        val h = size.height
        val tt = t.value
        val bob = wave(tt)
        val ground = h * 0.94f
        drawCircle(c.accent.copy(alpha = 0.08f), h * 0.6f, Offset(w * 0.5f, h * 0.5f))
        when (category) {
            0 -> {
                drawRoundRect(c.line, Offset(w * 0.2f, ground), Size(w * 0.6f, 4f), CornerRadius(2f))
                person(w * 0.36f, ground, h * 0.82f, Skin1, Hair1, c.accent, Denim, 1)
                person(w * 0.50f, ground, h * 0.92f, Skin2, Hair2, Sage, Denim, 0)
                person(w * 0.64f, ground, h * 0.80f, Skin3, Hair3, Blue, Denim, 2)
                sparkle(Offset(w * 0.78f, h * 0.2f + 4f * bob), 8f, Mustard)
            }
            1 -> {
                drawCircle(Mustard, h * 0.13f, Offset(w * 0.78f, h * 0.24f + 2f * bob))
                val hill = Path()
                hill.moveTo(w * 0.1f, ground)
                hill.quadraticTo(w * 0.35f, h * 0.35f, w * 0.62f, ground)
                hill.close()
                drawPath(hill, Sage.copy(alpha = 0.55f))
                for ((hx, hw, col) in listOf(Triple(0.30f, 0.12f, c.accent), Triple(0.47f, 0.10f, Blue), Triple(0.60f, 0.12f, Mustard))) {
                    drawRect(col, Offset(w * hx, ground - h * 0.30f), Size(w * hw, h * 0.30f))
                    val roof = Path()
                    roof.moveTo(w * hx - 6f, ground - h * 0.30f)
                    roof.lineTo(w * (hx + hw / 2), ground - h * 0.50f)
                    roof.lineTo(w * (hx + hw) + 6f, ground - h * 0.30f)
                    roof.close()
                    drawPath(roof, Wood)
                    drawRect(c.paper, Offset(w * (hx + hw * 0.38f), ground - h * 0.15f), Size(w * hw * 0.24f, h * 0.15f))
                }
                drawRect(Wood, Offset(w * 0.17f, ground - h * 0.22f), Size(6f, h * 0.22f))
                drawCircle(Sage, h * 0.14f, Offset(w * 0.17f + 3f, ground - h * 0.30f))
                drawRoundRect(c.line, Offset(w * 0.08f, ground), Size(w * 0.84f, 4f), CornerRadius(2f))
            }
            2 -> {
                val cols = listOf(c.accent, Blue, Sage)
                for (i in 0..2) {
                    val p = Path()
                    val y0 = h * (0.34f + i * 0.17f)
                    p.moveTo(w * 0.15f, y0)
                    var x = w * 0.15f
                    while (x < w * 0.85f) {
                        x += 6f
                        val k = (x - w * 0.15f) / (w * 0.7f)
                        p.lineTo(x, y0 + sin((k * 6.0 + i * 1.3 + tt * 2 * PI).toFloat()) * h * 0.08f)
                    }
                    drawPath(p, cols[i], style = Stroke(width = 7f, cap = StrokeCap.Round))
                }
                drawCircle(Mustard, h * 0.09f, Offset(w * 0.9f, h * 0.5f))
                drawCircle(c.paper, h * 0.03f, Offset(w * 0.9f, h * 0.5f))
            }
            else -> {
                drawRoundRect(c.line, Offset(w * 0.3f, ground), Size(w * 0.4f, 4f), CornerRadius(2f))
                person(w * 0.5f, ground, h * 0.86f, Skin2, Hair1, Sage, Denim, 1,
                    rHand = Offset(w * 0.5f + h * 0.02f, h * 0.32f))
                questionMark(Offset(w * 0.30f, h * 0.30f + 4f * wave(tt)), h * 0.10f, c.accent)
                questionMark(Offset(w * 0.70f, h * 0.24f + 4f * wave(tt, 0.4f)), h * 0.12f, Mustard)
                questionMark(Offset(w * 0.78f, h * 0.55f + 4f * wave(tt, 0.7f)), h * 0.08f, Blue)
            }
        }
    }
}
