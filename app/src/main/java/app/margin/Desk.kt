package app.margin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import kotlinx.coroutines.launch

val RAIL_INSET = 16.dp
private val RAIL_NAMES = listOf("Story", "Characters", "Places", "Threads", "Questions")
private val RAIL_GLYPHS = listOf(2, 4, 5, 6, 7)

@Composable
fun BiblePage(
    p: Int,
    bible: List<Entry>,
    top: androidx.compose.ui.unit.Dp,
    bottom: androidx.compose.ui.unit.Dp,
    onRemove: (Entry) -> Unit
) {
    val c = LocalMarginColors.current
    val cat = BIBLE_CATEGORIES[p]
    val group = bible.filter { it.category == cat }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = RAIL_INSET, end = 18.dp, top = top, bottom = bottom),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "scene") { Doodle(BIBLE_DOODLES[p], 150.dp) }
        item(key = "title") {
            Text(
                cat.uppercase(),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp,
                color = c.accent,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )
        }
        if (group.isEmpty()) {
            item {
                Text(
                    "Nothing here yet. It fills in as you record.",
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    fontFamily = FontFamily.Serif,
                    color = c.graphite
                )
            }
        }
        items(group.size, key = { group[it].id }) { i ->
            val e = group[i]
            val cut = e.text.indexOf(": ")
            val name = if (cut in 1..40) e.text.substring(0, cut) else null
            val detail = if (name != null) e.text.substring(cut + 2) else e.text
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(c.card, RoundedCornerShape(14.dp))
                    .padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                    if (name != null) {
                        Text(name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
                    }
                    if (detail.isNotEmpty()) {
                        Text(
                            detail,
                            fontFamily = FontFamily.Serif,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = if (name != null) c.graphite else c.ink
                        )
                    }
                }
                Box(
                    Modifier.size(44.dp).clickable { onRemove(e) },
                    contentAlignment = Alignment.Center
                ) { Text("×", fontSize = 22.sp, color = c.graphite) }
            }
        }
    }
}

@Composable
fun SectionRail(
    position: Float,
    current: Int,
    counts: List<Int>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalMarginColors.current
    val cellW = 26.dp
    val cellH = 33.dp
    Box(
        modifier
            .width(cellW + 4.dp)
            .height(cellH * RAIL_NAMES.size + 4.dp)
            .background(c.card.copy(alpha = 0.82f), RoundedCornerShape(15.dp))
            .border(1.dp, c.line.copy(alpha = 0.7f), RoundedCornerShape(15.dp))
    ) {
        Box(
            Modifier
                .offset(x = 2.dp, y = 2.dp + cellH * position)
                .size(cellW, cellH)
                .background(c.accent, RoundedCornerShape(13.dp))
        )
        Column(Modifier.padding(2.dp)) {
            RAIL_NAMES.forEachIndexed { i, name ->
                val on = current == i
                val tint = if (on) c.onAccent else c.ink
                Column(
                    Modifier
                        .size(cellW, cellH)
                        .clip(RoundedCornerShape(13.dp))
                        .clickable { onSelect(i) }
                        .androidx_semantics(name),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Glyph(RAIL_GLYPHS[i], tint, 15.dp)
                    Text(
                        counts.getOrElse(i) { 0 }.toString(),
                        fontSize = 9.sp,
                        lineHeight = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tint.copy(alpha = if (on) 1f else 0.7f),
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }
            }
        }
    }
}

private fun Modifier.androidx_semantics(label: String): Modifier =
    this.then(Modifier.semantics { contentDescription = label })

@Composable
fun ProposalDialog(
    proposals: List<Proposal>,
    onToggle: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalMarginColors.current
    val count = proposals.count { it.on }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(c.paper, RoundedCornerShape(24.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Update your Bible", fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = c.ink)
            LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(proposals.size) { i ->
                    val p = proposals[i]
                    val tone = if (p.act == Act.REMOVE) errorTone(c) else c.accent
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(c.card, RoundedCornerShape(12.dp))
                            .clickable { onToggle(i) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            Modifier
                                .padding(top = 2.dp)
                                .size(20.dp)
                                .background(if (p.on) tone else c.paper, CircleShape)
                                .border(1.5.dp, if (p.on) tone else c.line, CircleShape),
                            contentAlignment = Alignment.Center
                        ) { if (p.on) Text("✓", fontSize = 12.sp, color = c.onAccent) }
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(
                                p.act.name + " · " + p.category.uppercase(),
                                fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = tone
                            )
                            if (p.old != null) {
                                Text(
                                    p.old,
                                    fontFamily = FontFamily.Serif,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    color = c.graphite,
                                    textDecoration = TextDecoration.LineThrough
                                )
                            }
                            if (p.act != Act.REMOVE) {
                                Text(p.text, fontFamily = FontFamily.Serif, fontSize = 16.sp, lineHeight = 22.sp, color = c.ink)
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(50.dp)
                        .border(1.5.dp, c.line, RoundedCornerShape(14.dp))
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) { Text("Skip", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.ink) }
                Box(
                    Modifier
                        .weight(2f)
                        .height(50.dp)
                        .background(if (count > 0) c.accent else c.line, RoundedCornerShape(14.dp))
                        .clickable(enabled = count > 0) { onConfirm() },
                    contentAlignment = Alignment.Center
                ) { Text("Apply " + count, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (count > 0) c.onAccent else c.graphite) }
            }
        }
    }
}
