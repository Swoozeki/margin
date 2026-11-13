package app.margin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

val RAIL_INSET = 18.dp

private val WASH_LIGHT = listOf(Color(0xFFEBDCC8), Color(0xFFDCE5D3), Color(0xFFD8E0EA), Color(0xFFEAD5D2), Color(0xFFE2D9EC))
private val WASH_DARK = listOf(Color(0xFF3F362C), Color(0xFF2E382B), Color(0xFF2A3440), Color(0xFF3F2E2D), Color(0xFF362F40))

private fun splitEntry(text: String): Pair<String, String> {
    val cut = text.indexOf(": ")
    return if (cut in 1..40) text.substring(0, cut) to text.substring(cut + 2) else text to ""
}

@Composable
private fun washFor(name: String): Color {
    val c = LocalMarginColors.current
    val list = if (c.paper.luminance() < 0.5f) WASH_DARK else WASH_LIGHT
    return list[(name.lowercase().hashCode() and 0x7fffffff) % list.size]
}

@Composable
private fun ShelfHeader(title: String, count: Int) {
    val c = LocalMarginColors.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = RAIL_INSET).padding(top = 10.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(title, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, color = c.ink, modifier = Modifier.weight(1f))
        Text(count.toString(), fontSize = 13.sp, color = c.graphite)
    }
}

@Composable
private fun CharacterCard(e: Entry, onClick: () -> Unit) {
    val c = LocalMarginColors.current
    val (name, detail) = splitEntry(e.text)
    Column(
        Modifier
            .width(124.dp)
            .height(168.dp)
            .background(c.card, RoundedCornerShape(18.dp))
            .border(1.dp, c.line, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(56.dp).background(washFor(name), CircleShape), contentAlignment = Alignment.Center) {
            Text(
                name.trim().take(1).uppercase(),
                fontFamily = FontFamily.Serif, fontSize = 26.sp, color = c.ink
            )
        }
        Text(
            name, modifier = Modifier.padding(top = 10.dp),
            fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.ink,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
        )
        if (detail.isNotEmpty()) {
            Text(
                detail, modifier = Modifier.padding(top = 3.dp),
                fontFamily = FontFamily.Serif, fontSize = 12.5.sp, lineHeight = 16.sp, color = c.graphite,
                maxLines = 3, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PlaceCard(e: Entry, onClick: () -> Unit) {
    val c = LocalMarginColors.current
    val (name, detail) = splitEntry(e.text)
    val line = c.ink.copy(alpha = 0.4f)
    Column(
        Modifier
            .width(184.dp)
            .height(138.dp)
            .background(c.card, RoundedCornerShape(18.dp))
            .border(1.dp, c.line, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() }
    ) {
        Box(Modifier.fillMaxWidth().height(58.dp).background(washFor(name))) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val st = Stroke(width = 1.5.dp.toPx())
                val p = Path()
                p.moveTo(0f, h * 0.74f)
                p.quadraticTo(w * 0.25f, h * 0.5f, w * 0.5f, h * 0.74f)
                p.quadraticTo(w * 0.75f, h * 0.96f, w, h * 0.7f)
                drawPath(p, line, style = st)
                drawCircle(line, 6.dp.toPx(), Offset(w * 0.8f, h * 0.3f), style = st)
                val house = Path()
                house.moveTo(w * 0.2f, h * 0.7f)
                house.lineTo(w * 0.2f, h * 0.42f)
                house.lineTo(w * 0.26f, h * 0.3f)
                house.lineTo(w * 0.32f, h * 0.42f)
                house.lineTo(w * 0.32f, h * 0.72f)
                drawPath(house, line, style = st)
            }
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail.isNotEmpty()) {
                Text(
                    detail, modifier = Modifier.padding(top = 2.dp),
                    fontFamily = FontFamily.Serif, fontSize = 12.5.sp, lineHeight = 16.sp, color = c.graphite,
                    maxLines = 3, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ThreadRow(e: Entry, first: Boolean, last: Boolean, onClick: () -> Unit) {
    val c = LocalMarginColors.current
    val (name, detail) = splitEntry(e.text)
    val lineColor = c.line
    val accent = c.accent
    val paper = c.paper
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = RAIL_INSET)
            .height(IntrinsicSize.Min)
            .clickable { onClick() }
    ) {
        Canvas(Modifier.width(24.dp).fillMaxHeight()) {
            val cx = size.width / 2
            val cy = 15.dp.toPx()
            val sw = 1.5.dp.toPx()
            if (!first) drawLine(lineColor, Offset(cx, 0f), Offset(cx, cy), sw)
            if (!last) drawLine(lineColor, Offset(cx, cy), Offset(cx, size.height), sw)
            drawCircle(paper, 6.dp.toPx(), Offset(cx, cy))
            drawCircle(accent, 6.dp.toPx(), Offset(cx, cy), style = Stroke(2.dp.toPx()))
        }
        Column(Modifier.padding(start = 10.dp, top = 5.dp, bottom = 16.dp)) {
            Text(name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
            if (detail.isNotEmpty()) {
                Text(detail, modifier = Modifier.padding(top = 2.dp), fontFamily = FontFamily.Serif, fontSize = 13.5.sp, lineHeight = 19.sp, color = c.graphite)
            }
        }
    }
}

@Composable
private fun QuestionCard(e: Entry, i: Int, onClick: () -> Unit) {
    val c = LocalMarginColors.current
    val (name, detail) = splitEntry(e.text)
    Row(
        Modifier
            .padding(horizontal = RAIL_INSET)
            .fillMaxWidth()
            .graphicsLayer { rotationZ = if (i % 2 == 0) -0.6f else 0.5f }
            .background(c.card, RoundedCornerShape(14.dp))
            .border(1.dp, c.line, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text("?", fontFamily = FontFamily.Serif, fontSize = 28.sp, color = c.accent, modifier = Modifier.width(26.dp))
        Column(Modifier.padding(start = 6.dp)) {
            Text(name, fontFamily = FontFamily.Serif, fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
            if (detail.isNotEmpty()) {
                Text(detail, modifier = Modifier.padding(top = 2.dp), fontSize = 13.sp, lineHeight = 18.sp, color = c.graphite)
            }
        }
    }
}

@Composable
private fun TodayCard(q: Entry, more: Boolean, onMore: () -> Unit, onClick: () -> Unit) {
    val c = LocalMarginColors.current
    val (name, detail) = splitEntry(q.text)
    Column(
        Modifier
            .padding(horizontal = RAIL_INSET)
            .fillMaxWidth()
            .background(c.card, RoundedCornerShape(20.dp))
            .border(1.dp, c.line, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(18.dp)
    ) {
        Text("TODAY’S QUESTION", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp, color = c.accent)
        Text(name, modifier = Modifier.padding(top = 6.dp), fontFamily = FontFamily.Serif, fontSize = 22.sp, lineHeight = 29.sp, color = c.ink)
        if (detail.isNotEmpty()) {
            Text(detail, modifier = Modifier.padding(top = 4.dp), fontFamily = FontFamily.Serif, fontSize = 14.sp, lineHeight = 20.sp, color = c.graphite)
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.background(c.accent.copy(alpha = 0.14f), CircleShape).padding(start = 10.dp, end = 14.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Glyph(1, c.accent, 16.dp)
                Text("Hold the mic to answer", modifier = Modifier.padding(start = 6.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.accent)
            }
            Box(Modifier.weight(1f))
            if (more) {
                Text(
                    "Another", modifier = Modifier.clickable { onMore() }.padding(8.dp),
                    fontSize = 13.sp, fontWeight = FontWeight.Medium, color = c.graphite
                )
            }
        }
    }
}

@Composable
fun BibleScreen(
    bible: List<Entry>,
    top: androidx.compose.ui.unit.Dp,
    bottom: androidx.compose.ui.unit.Dp,
    onRemove: (Entry) -> Unit,
    onProbe: (Entry) -> Unit
) {
    val c = LocalMarginColors.current
    var detail by remember { mutableStateOf<Entry?>(null) }
    var bump by remember { mutableIntStateOf(0) }
    val day = remember { java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR) }

    val chars = bible.filter { it.category == "Characters" }
    val places = bible.filter { it.category == "Places" }
    val threads = bible.filter { it.category == "Threads" }.sortedBy { it.createdAt }
    val qs = bible.filter { it.category == "Questions" }
    val today = if (qs.isEmpty()) null else qs[(day + bump) % qs.size]

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = top, bottom = bottom),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "scene") { Doodle(BIBLE_DOODLES[0], 130.dp, Modifier.padding(horizontal = RAIL_INSET)) }

        if (bible.isEmpty()) {
            item(key = "empty") {
                Text(
                    "Your Bible fills in as you record.\nCharacters, places, threads and questions will gather here.",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                    fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic,
                    fontSize = 17.sp, lineHeight = 25.sp, textAlign = TextAlign.Center, color = c.graphite
                )
            }
        }

        if (today != null) {
            item(key = "today") { TodayCard(today, qs.size > 1, { bump += 1 }) { detail = today } }
        }

        if (chars.isNotEmpty()) {
            item(key = "h_chars") { ShelfHeader("Cast", chars.size) }
            item(key = "r_chars") {
                LazyRow(contentPadding = PaddingValues(horizontal = RAIL_INSET), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(chars.size, key = { chars[it].id }) { i -> CharacterCard(chars[i]) { detail = chars[i] } }
                }
            }
        }

        if (places.isNotEmpty()) {
            item(key = "h_places") { ShelfHeader("Places", places.size) }
            item(key = "r_places") {
                LazyRow(contentPadding = PaddingValues(horizontal = RAIL_INSET), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(places.size, key = { places[it].id }) { i -> PlaceCard(places[i]) { detail = places[i] } }
                }
            }
        }

        if (threads.isNotEmpty()) {
            item(key = "h_threads") { ShelfHeader("Threads", threads.size) }
            items(threads.size, key = { "t" + threads[it].id }) { i ->
                ThreadRow(threads[i], i == 0, i == threads.size - 1) { detail = threads[i] }
            }
        }

        if (qs.isNotEmpty()) {
            item(key = "h_qs") { ShelfHeader("Open questions", qs.size) }
            items(qs.size, key = { "q" + qs[it].id }) { i -> QuestionCard(qs[i], i) { detail = qs[i] } }
        }
    }

    val d = detail
    if (d != null) {
        val (name, text) = splitEntry(d.text)
        Dialog(onDismissRequest = { detail = null }) {
            Column(
                Modifier.fillMaxWidth().background(c.paper, RoundedCornerShape(24.dp)).padding(22.dp)
            ) {
                Text(d.category.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp, color = c.accent)
                Text(name, modifier = Modifier.padding(top = 6.dp), fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 27.sp, lineHeight = 33.sp, color = c.ink)
                if (text.isNotEmpty()) {
                    Text(text, modifier = Modifier.padding(top = 8.dp), fontFamily = FontFamily.Serif, fontSize = 17.sp, lineHeight = 25.sp, color = c.ink)
                }
                Row(Modifier.fillMaxWidth().padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier.weight(1f).height(50.dp)
                            .border(1.5.dp, c.line, RoundedCornerShape(14.dp))
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onRemove(d); detail = null },
                        contentAlignment = Alignment.Center
                    ) { Text("Remove", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.ink) }
                    Box(
                        Modifier.weight(2f).height(50.dp)
                            .background(c.accent, RoundedCornerShape(14.dp))
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onProbe(d); detail = null },
                        contentAlignment = Alignment.Center
                    ) { Text("Think it through", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.onAccent) }
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
