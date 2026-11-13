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

@Composable
fun BibleScreen(
    bible: List<Entry>,
    top: androidx.compose.ui.unit.Dp,
    bottom: androidx.compose.ui.unit.Dp,
    onRemove: (Entry) -> Unit
) {
    val c = LocalMarginColors.current
    var sel by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val cat = BIBLE_CATEGORIES[sel]
    val group = bible.filter { it.category == cat }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, end = 20.dp, top = top + 52.dp, bottom = 24.dp + bottom
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (group.isEmpty()) {
                item {
                    Text(
                        "Nothing here yet",
                        modifier = Modifier.padding(top = 40.dp).fillMaxWidth(),
                        fontSize = 15.sp,
                        color = c.graphite,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
                        Modifier
                            .size(44.dp)
                            .clickable { onRemove(e) },
                        contentAlignment = Alignment.Center
                    ) { Text("\u00d7", fontSize = 22.sp, color = c.graphite) }
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        0f to c.paper.copy(alpha = 0.92f), 1f to androidx.compose.ui.graphics.Color.Transparent
                    )
                )
                .padding(start = 20.dp, end = 20.dp, top = top, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BIBLE_CATEGORIES.forEachIndexed { i, name ->
                val n = bible.count { it.category == name }
                val on = i == sel
                Box(
                    Modifier
                        .weight(1f)
                        .height(36.dp)
                        .background(if (on) c.ink else c.card, CircleShape)
                        .clickable { sel = i },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        name + (if (n > 0) " " + n else ""),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        color = if (on) c.paper else c.ink
                    )
                }
            }
        }
    }
}

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
            Text("Add to your Bible", fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = c.ink)
            LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(proposals.size) { i ->
                    val p = proposals[i]
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
                                .background(if (p.on) c.accent else c.paper, CircleShape)
                                .border(1.5.dp, if (p.on) c.accent else c.line, CircleShape),
                            contentAlignment = Alignment.Center
                        ) { if (p.on) Text("✓", fontSize = 12.sp, color = c.onAccent) }
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(p.category.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = c.accent)
                            Text(p.text, fontFamily = FontFamily.Serif, fontSize = 16.sp, lineHeight = 22.sp, color = c.ink)
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
                ) { Text("Add " + count, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (count > 0) c.onAccent else c.graphite) }
            }
        }
    }
}
