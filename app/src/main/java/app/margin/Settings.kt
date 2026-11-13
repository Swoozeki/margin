package app.margin

import android.content.Context
import android.content.SharedPreferences
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object AppSettings {
    private var prefs: SharedPreferences? = null

    var speakReplies by mutableStateOf(true)
        private set
    var tidyText by mutableStateOf(true)
        private set
    var autoBible by mutableStateOf(true)
        private set
    var mobileData by mutableStateOf(false)
        private set

    fun init(ctx: Context) {
        if (prefs != null) return
        val p = ctx.applicationContext.getSharedPreferences("margin", Context.MODE_PRIVATE)
        prefs = p
        speakReplies = p.getBoolean("speakReplies", true)
        tidyText = p.getBoolean("tidyText", true)
        autoBible = p.getBoolean("autoBible", true)
        mobileData = p.getBoolean("mobileData", false)
    }

    private fun put(key: String, v: Boolean) {
        prefs?.edit()?.putBoolean(key, v)?.apply()
    }

    fun updateSpeakReplies(v: Boolean) { speakReplies = v; put("speakReplies", v) }
    fun updateTidyText(v: Boolean) { tidyText = v; put("tidyText", v) }
    fun updateAutoBible(v: Boolean) { autoBible = v; put("autoBible", v) }
    fun updateMobileData(v: Boolean) { mobileData = v; put("mobileData", v) }
}

@Composable
private fun Toggle(on: Boolean) {
    val c = LocalMarginColors.current
    val bg by animateColorAsState(if (on) c.accent else c.line, tween(160), label = "tb")
    val x by animateDpAsState(if (on) 21.dp else 3.dp, tween(160), label = "tx")
    Box(Modifier.width(48.dp).height(30.dp).clip(CircleShape).background(bg)) {
        Box(Modifier.offset(x, 3.dp).size(24.dp).background(if (on) c.onAccent else c.card, CircleShape))
    }
}

@Composable
private fun SectionLabel(text: String) {
    val c = LocalMarginColors.current
    Text(
        text.uppercase(),
        modifier = Modifier.padding(start = 6.dp, top = 22.dp, bottom = 8.dp),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.5.sp,
        color = c.accent
    )
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    val c = LocalMarginColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.card, RoundedCornerShape(18.dp))
            .border(1.dp, c.line, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp)
    ) { content() }
}

@Composable
private fun Divider() {
    val c = LocalMarginColors.current
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.line.copy(alpha = 0.6f)))
}

@Composable
private fun ToggleRow(title: String, desc: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalMarginColors.current
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!on) }.padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontSize = 16.sp, color = c.ink)
            Text(desc, fontSize = 13.sp, lineHeight = 18.sp, color = c.graphite, modifier = Modifier.padding(top = 2.dp))
        }
        Toggle(on)
    }
}

@Composable
private fun ActionRow(title: String, status: String, action: String?, onAction: () -> Unit) {
    val c = LocalMarginColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontSize = 16.sp, color = c.ink)
            Text(status, fontSize = 13.sp, lineHeight = 18.sp, color = c.graphite, modifier = Modifier.padding(top = 2.dp))
        }
        if (action != null) {
            Box(
                Modifier
                    .height(36.dp)
                    .border(1.dp, c.accent, CircleShape)
                    .clip(CircleShape)
                    .clickable { onAction() }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) { Text(action, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.accent) }
        }
    }
}

@Composable
fun SettingsScreen(top: Dp, bottom: Dp, onClose: () -> Unit, onExport: () -> Unit) {
    val c = LocalMarginColors.current
    val ctx = LocalContext.current
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) LocalAi.startImport(ctx, uri)
    }
    val version = remember0 { try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" } catch (e: Throwable) { "" } }

    val speech = when {
        SpeechIn.ready -> "Parakeet v3, running on this phone"
        SpeechIn.downloading -> "Downloading " + (SpeechIn.progress * 100).toInt() + "%"
        else -> "Using your phone's built-in recognizer"
    }
    val aiState = LocalAi.state
    val ai = when (aiState) {
        AiState.Missing -> "Not downloaded yet"
        AiState.Downloading -> "Downloading " + (LocalAi.progress * 100).toInt() + "%"
        AiState.Failed -> "Couldn't load the model"
        else -> "Gemma 4, running on this phone"
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(c.paper)
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, top = top + 4.dp, bottom = bottom + 32.dp)
        ) {
            Text(
                "Settings",
                modifier = Modifier.padding(start = 6.dp, bottom = 4.dp),
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 30.sp,
                color = c.ink
            )

            SectionLabel("Voice")
            Group {
                ToggleRow("Speak replies", "Read answers aloud as they arrive.", AppSettings.speakReplies) { AppSettings.updateSpeakReplies(it) }
                Divider()
                ActionRow(
                    "Speech recognition", speech,
                    if (!SpeechIn.ready && !SpeechIn.downloading) "Download" else null
                ) { SpeechIn.prepare(ctx, force = true) }
                Divider()
                ToggleRow(
                    "Download over mobile data", "Off keeps the 640 MB voice model to Wi-Fi.",
                    AppSettings.mobileData
                ) {
                    AppSettings.updateMobileData(it)
                    if (it) SpeechIn.prepare(ctx)
                }
            }

            SectionLabel("Writing")
            Group {
                ToggleRow("Fill the Bible after recordings", "You always confirm the changes first.", AppSettings.autoBible) { AppSettings.updateAutoBible(it) }
                Divider()
                ToggleRow("Add punctuation to transcripts", "Tidies what you said once you let go.", AppSettings.tidyText) { AppSettings.updateTidyText(it) }
            }

            SectionLabel("Model")
            Group {
                ActionRow(
                    "On-device AI", ai,
                    if (aiState == AiState.Missing || aiState == AiState.Failed) "Download" else null
                ) { LocalAi.startDownload(ctx) }
                if (LocalAi.canUpgrade(ctx)) {
                    Divider()
                    ActionRow("Larger model", "Smarter replies, 3.7 GB.", "Get") { LocalAi.startDownload(ctx) }
                }
                Divider()
                ActionRow("Import a model file", "Use a model you've already downloaded.", "Choose") { importer.launch(arrayOf("*/*")) }
            }

            SectionLabel("Your data")
            Group {
                ActionRow("Export your story", "Share the Bible and recordings as text.", "Export") { onExport() }
                Divider()
                Row(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
                    Text(
                        "Everything stays on this phone. Nothing is uploaded.",
                        fontSize = 13.sp, lineHeight = 18.sp, color = c.graphite
                    )
                }
            }

            Text(
                "Margin" + (if (version.isNotEmpty()) " " + version else ""),
                modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
                fontSize = 12.sp,
                color = c.graphite,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = top, end = 16.dp)
                .size(44.dp)
                .background(c.card, CircleShape)
                .border(1.dp, c.line, CircleShape)
                .clip(CircleShape)
                .semantics { contentDescription = "Close settings" }
                .clickable { onClose() },
            contentAlignment = Alignment.Center
        ) { Glyph(13, c.ink, 20.dp) }
    }
}

@Composable
private fun <T> remember0(calc: () -> T): T = androidx.compose.runtime.remember { calc() }
