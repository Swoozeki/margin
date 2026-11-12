package app.margin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DeskScreen(
    ideas: List<Idea>,
    bible: List<Entry>,
    onFile: (Idea, String) -> Unit,
    onDelete: (Idea) -> Unit,
    onRemoveEntry: (Entry) -> Unit
) {
    val c = LocalMarginColors.current
    var tab by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Idea?>(null) }
    var suggestion by remember { mutableStateOf<String?>(null) }
    var asking by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fmt = remember { SimpleDateFormat("EEE d MMM, h:mm a", Locale.getDefault()) }
    val tilts = listOf(-1.4f, 1.0f, -0.7f, 1.6f)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(22.dp, 28.dp, 22.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text(
                    "The desk",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 36.sp,
                    color = c.ink
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Where your ideas wait for you.",
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    fontSize = 17.sp,
                    color = c.graphite
                )
            }
        }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, c.line, RoundedCornerShape(14.dp))
                    .padding(4.dp)
            ) {
                listOf("Ideas", "Story bible").forEachIndexed { i, label ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(44.dp)
                            .background(if (tab == i) c.ink else Color.Transparent, RoundedCornerShape(10.dp))
                            .clickable { tab = i },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (tab == i) c.paper else c.graphite
                        )
                    }
                }
            }
        }
        if (tab == 0) {
            if (ideas.isEmpty()) {
                item {
                    Text(
                        "Nothing here yet. Swipe to the Page and say your first idea.",
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        color = c.graphite
                    )
                }
            } else {
                item {
                    Text("Tap an idea to file it into your story bible.", fontSize = 13.sp, color = c.graphite)
                }
            }
            items(ideas.size) { i ->
                val idea = ideas[i]
                Column(
                    Modifier
                        .fillMaxWidth()
                        .rotate(tilts[i % tilts.size])
                        .shadow(3.dp, RoundedCornerShape(14.dp))
                        .background(c.card, RoundedCornerShape(14.dp))
                        .border(1.dp, c.line, RoundedCornerShape(14.dp))
                        .clickable {
                            selected = idea
                            suggestion = null
                        }
                        .padding(16.dp)
                ) {
                    Text(
                        idea.text,
                        fontFamily = FontFamily.Serif,
                        fontSize = 16.sp,
                        lineHeight = 23.sp,
                        color = c.ink
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        fmt.format(Date(idea.createdAt)),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = c.graphite
                    )
                }
            }
        } else {
            BIBLE_CATEGORIES.forEach { cat ->
                val list = bible.filter { it.category == cat }
                item(key = "h-" + cat) {
                    Text(
                        cat.uppercase(),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                        color = c.graphite,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                if (list.isEmpty()) {
                    item(key = "e-" + cat) {
                        Text("Nothing filed yet.", fontSize = 14.sp, color = c.graphite)
                    }
                }
                items(list.size) { j ->
                    val e = list[j]
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(c.card, RoundedCornerShape(14.dp))
                            .border(1.dp, c.line, RoundedCornerShape(14.dp))
                            .padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            e.text,
                            modifier = Modifier.weight(1f),
                            fontFamily = FontFamily.Serif,
                            fontSize = 16.sp,
                            lineHeight = 22.sp,
                            color = c.ink
                        )
                        Box(
                            Modifier
                                .height(48.dp)
                                .clickable { onRemoveEntry(e) }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Remove", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = c.accent)
                        }
                    }
                }
            }
        }
    }

    val current = selected
    if (current != null) {
        Dialog(onDismissRequest = { selected = null }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(c.card, RoundedCornerShape(20.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    current.text,
                    fontFamily = FontFamily.Serif,
                    fontSize = 18.sp,
                    lineHeight = 26.sp,
                    color = c.ink
                )
                if (LocalAi.state == AiState.Installed || LocalAi.state == AiState.Ready || LocalAi.state == AiState.Loading) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(c.paper, RoundedCornerShape(12.dp))
                            .clickable(enabled = !asking) {
                                asking = true
                                scope.launch {
                                    suggestion = try {
                                        LocalAi.suggestCategory(context, current.text)
                                    } catch (e: Throwable) {
                                        null
                                    }
                                    asking = false
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (asking) "Thinking..." else if (suggestion != null) "Suggested: " + suggestion else "Ask the AI where this belongs",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = c.accent
                        )
                    }
                }
                Text(
                    "FILE INTO THE STORY BIBLE",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    color = c.graphite,
                    modifier = Modifier.padding(top = 6.dp)
                )
                BIBLE_CATEGORIES.forEach { cat ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .border(if (cat == suggestion) 2.dp else 1.dp, if (cat == suggestion) c.accent else c.line, RoundedCornerShape(12.dp))
                            .clickable {
                                onFile(current, cat)
                                selected = null
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(cat, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.ink)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Box(
                        Modifier
                            .height(48.dp)
                            .clickable {
                                onDelete(current)
                                selected = null
                            }
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Delete idea", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.accent)
                    }
                    Box(
                        Modifier
                            .height(48.dp)
                            .clickable { selected = null }
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Close", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.graphite)
                    }
                }
            }
        }
    }
}
