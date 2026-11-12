package app.margin

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
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

private val MODE_WORDS = listOf("Listen", "Question", "Critique", "Teach")

private val MODE_BLURBS = listOf(
    "Stay quiet and write down your thinking. No interruptions.",
    "One good question at a time, about your story.",
    "Honest notes on what is working and what is not.",
    "Craft and research answers, short and spoken."
)

private val MODE_INSTRUCTIONS = listOf(
    "Listen mode: stay quiet while I think out loud. Only acknowledge me in a few words. When I say 'done', summarize my thinking and list any decisions and open questions.",
    "Question mode: ask me one good question at a time about my story. Do not give me answers or ideas unless I ask.",
    "Critique mode: give me honest, specific notes on what is working and what is not. Do not flatter me.",
    "Teach mode: answer my craft and research questions briefly, in a spoken style, and ask if I want to go deeper."
)

fun buildClaudePacket(modeIndex: Int, ideas: List<Idea>, bible: List<Entry>): String {
    val sb = StringBuilder()
    sb.append("You are my fiction-writing companion. I am an aspiring writer who prefers talking to typing, so keep replies short and easy to say aloud. Never write my story for me.\n\n")
    sb.append(MODE_INSTRUCTIONS[modeIndex]).append("\n\n")
    var anyBible = false
    for (cat in BIBLE_CATEGORIES) {
        val items = bible.filter { it.category == cat }
        if (items.isNotEmpty()) {
            if (!anyBible) {
                sb.append("My story bible so far:\n")
                anyBible = true
            }
            sb.append(cat).append(":\n")
            for (e in items) sb.append("- ").append(e.text).append("\n")
        }
    }
    if (anyBible) sb.append("\n")
    val recent = ideas.take(10)
    if (recent.isNotEmpty()) {
        sb.append("Recent loose ideas from my desk:\n")
        for (i in recent) sb.append("- ").append(i.text).append("\n")
        sb.append("\n")
    }
    sb.append("Begin by asking what I want to work on today.")
    return sb.toString()
}

fun shareToClaude(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    try {
        val direct = Intent(send).apply {
            setPackage("com.anthropic.claude")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(direct)
    } catch (e: ActivityNotFoundException) {
        val chooser = Intent.createChooser(send, "Open in Claude")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}

@Composable
fun ThinkScreen(ideas: List<Idea>, bible: List<Entry>) {
    val c = LocalMarginColors.current
    val context = LocalContext.current
    var mode by remember { mutableIntStateOf(0) }
    val tints = listOf(
        Color(0x1A786E5F),
        Color(0x1AB8432A),
        Color(0x203C648C),
        Color(0x20467350)
    )
    val tint by animateColorAsState(tints[mode], tween(600), label = "tint")
    Box(Modifier.fillMaxSize().background(tint)) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
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
                fontSize = 44.sp,
                color = c.ink
            )
            Text(
                MODE_BLURBS[mode],
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                fontSize = 17.sp,
                lineHeight = 24.sp,
                color = c.graphite
            )
            Row(Modifier.fillMaxWidth()) {
                MODE_WORDS.forEachIndexed { i, w ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clickable { mode = i },
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
            Text(
                "This sends your story bible, recent ideas and this mode to the Claude app, so you talk with your own Claude account. Turn on voice mode there and just start talking.",
                fontSize = 15.sp,
                lineHeight = 22.sp,
                color = c.graphite
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .background(c.accent, RoundedCornerShape(18.dp))
                    .clickable { shareToClaude(context, buildClaudePacket(mode, ideas, bible)) },
                contentAlignment = Alignment.Center
            ) {
                Text("Open in Claude", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.onAccent)
            }
        }
    }
}
