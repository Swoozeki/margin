package app.margin

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Scenes are from unDraw (free to use, no attribution), recolored per theme
 * (drawable / drawable-night) and given a slow, gentle float.
 */
@Composable
fun Doodle(@DrawableRes res: Int, height: Dp, modifier: Modifier = Modifier) {
    val tr = rememberInfiniteTransition(label = "doodle")
    val f = tr.animateFloat(0f, 1f, infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart), label = "f")
    Image(
        painter = painterResource(res),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer {
                val a = (f.value * 2f * PI).toFloat()
                translationY = sin(a) * 5.dp.toPx()
                rotationZ = sin(a + 1f) * 1.2f
            }
    )
}

val BIBLE_DOODLES = listOf(R.drawable.art_select_character, R.drawable.art_town, R.drawable.art_adventure_map, R.drawable.art_thought_process)
val MODE_DOODLES = listOf(R.drawable.art_audio_conversation, R.drawable.art_questions, R.drawable.art_teacher, R.drawable.art_teaching)
