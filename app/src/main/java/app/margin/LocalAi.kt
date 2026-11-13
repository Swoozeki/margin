package app.margin

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

enum class AiState { Missing, Downloading, Installed, Loading, Ready, Failed }

/** The on-device model (Gemma 4 E2B via LiteRT-LM). Everything here runs on the phone. */
object LocalAi {
    private const val E2B = "gemma-4-E2B-it.litertlm"
    private const val E4B = "gemma-4-E4B-it.litertlm"

    private fun urlFor(size: String) =
        "https://huggingface.co/litert-community/gemma-4-" + size + "-it-litert-lm/resolve/main/gemma-4-" + size + "-it.litertlm"

    private fun bigPhone(ctx: Context): Boolean {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val mi = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return mi.totalMem >= 7_000_000_000L
    }

    private fun targetName(ctx: Context) = if (bigPhone(ctx)) E4B else E2B

    var state by mutableStateOf(AiState.Missing)
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    var message by mutableStateOf("")
        private set

    private var engine: Engine? = null
    private val engineLock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun modelsDir(ctx: Context): File {
        val dir = File(ctx.filesDir, "models")
        dir.mkdirs()
        return dir
    }

    private fun has(f: File) = f.exists() && f.length() > 0

    private fun modelFile(ctx: Context): File {
        val dir = modelsDir(ctx)
        val big = File(dir, E4B)
        val small = File(dir, E2B)
        return if (has(big)) big else if (has(small)) small else File(dir, targetName(ctx))
    }

    /** True when this phone can run the larger model and only the small one is installed. */
    fun canUpgrade(ctx: Context): Boolean =
        bigPhone(ctx) && !has(File(modelsDir(ctx), E4B)) && has(File(modelsDir(ctx), E2B)) &&
            state != AiState.Downloading

    fun refresh(ctx: Context) {
        if (state == AiState.Downloading || state == AiState.Loading || state == AiState.Ready) return
        state = if (has(modelFile(ctx))) AiState.Installed else AiState.Missing
    }

    fun startDownload(ctx: Context) {
        if (state == AiState.Downloading) return
        val app = ctx.applicationContext
        scope.launch { download(app) }
    }

    fun startImport(ctx: Context, uri: Uri) {
        if (state == AiState.Downloading) return
        val app = ctx.applicationContext
        scope.launch { importFrom(app, uri) }
    }

    private fun download(ctx: Context) {
        state = AiState.Downloading
        progress = 0f
        message = ""
        val name = targetName(ctx)
        val target = File(modelsDir(ctx), name)
        val tmp = File(target.parentFile, name + ".part")
        try {
            val existing = if (tmp.exists()) tmp.length() else 0L
            val conn = URL(urlFor(if (name == E4B) "E4B" else "E2B")).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 20000
            conn.readTimeout = 30000
            if (existing > 0) conn.setRequestProperty("Range", "bytes=" + existing + "-")
            val code = conn.responseCode
            if (code == 401 || code == 403) {
                state = AiState.Missing
                message = "This download needs a Hugging Face login. Download the file elsewhere, then tap Import model file."
                return
            }
            if (code != 200 && code != 206) {
                state = AiState.Missing
                message = "Download failed (server said " + code + ")."
                return
            }
            val resume = code == 206
            val startAt = if (resume) existing else 0L
            val total = if (conn.contentLengthLong > 0) startAt + conn.contentLengthLong else -1L
            conn.inputStream.use { input ->
                java.io.FileOutputStream(tmp, resume).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = startAt
                    while (true) {
                        val read = input.read(buf)
                        if (read < 0) break
                        out.write(buf, 0, read)
                        done += read
                        if (total > 0) progress = done.toFloat() / total
                    }
                }
            }
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) throw java.io.IOException("Couldn't finish saving the model.")
            try { engine?.close() } catch (t: Throwable) {}
            engine = null
            state = AiState.Installed
        } catch (e: Exception) {
            state = if (has(modelFile(ctx))) AiState.Installed else AiState.Missing
            message = "Download paused: " + (e.message ?: "connection lost") + ". Tap Download again to resume."
        }
    }

    private fun importFrom(ctx: Context, uri: Uri) {
        state = AiState.Downloading
        progress = 0f
        message = ""
        val target = File(modelsDir(ctx), targetName(ctx))
        val tmp = File(target.parentFile, target.name + ".part")
        try {
            ctx.contentResolver.openInputStream(uri).use { input ->
                if (input == null) throw java.io.IOException("Couldn't open that file.")
                tmp.outputStream().use { out -> input.copyTo(out) }
            }
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) throw java.io.IOException("Couldn't finish saving the model.")
            state = AiState.Installed
        } catch (e: Exception) {
            state = AiState.Missing
            message = "Import failed: " + (e.message ?: "unknown error")
        }
    }

    private suspend fun ensureEngine(ctx: Context): Engine = withContext(Dispatchers.Default) {
        engineLock.withLock {
            val existing = engine
            if (existing != null) return@withLock existing
            state = AiState.Loading
            val path = modelFile(ctx).absolutePath
            val cache = ctx.cacheDir.path
            try {
                val e = try {
                    Engine(
                        EngineConfig(
                            modelPath = path,
                            backend = Backend.GPU(),
                            visionBackend = Backend.GPU(),
                            cacheDir = cache
                        )
                    ).also { it.initialize() }
                } catch (gpu: Throwable) {
                    Engine(
                        EngineConfig(
                            modelPath = path,
                            backend = Backend.CPU(),
                            visionBackend = Backend.CPU(),
                            cacheDir = cache
                        )
                    ).also { it.initialize() }
                }
                engine = e
                state = AiState.Ready
                e
            } catch (t: Throwable) {
                state = AiState.Failed
                message = "The model couldn't start: " + (t.message ?: "unknown error")
                throw t
            }
        }
    }

    private val taskLock = Mutex()

    /** One streamed reply. Stateless: the caller puts any history into [system]. */
    suspend fun reply(ctx: Context, system: String, user: String, onPiece: (String) -> Unit) =
        withContext(Dispatchers.Default) {
            val e = ensureEngine(ctx)
            taskLock.withLock {
                e.createConversation(ConversationConfig(systemInstruction = Contents.of(system))).use { c ->
                    c.sendMessageAsync(user).collect { onPiece(it.toString()) }
                }
            }
        }

    suspend fun transcribe(ctx: Context, imagePath: String): String = withContext(Dispatchers.Default) {
        val e = ensureEngine(ctx)
        taskLock.withLock {
            val config = ConversationConfig(
                systemInstruction = Contents.of(
                    "You transcribe handwritten notes faithfully. Output only the transcribed text, with no commentary. Keep the original line breaks. If a word is unclear, give your best guess."
                )
            )
            e.createConversation(config).use { c ->
                c.sendMessage(
                    Contents.of(
                        Content.ImageFile(imagePath),
                        Content.Text("Transcribe this handwritten page exactly as written.")
                    )
                ).toString().trim()
            }
        }
    }

    var lastRaw = ""

    /** Reconciles the bible with a new note: adds new facts, edits corrected ones, removes dropped ones. */
    suspend fun extractBible(ctx: Context, note: String, existing: List<Entry>): List<Proposal> =
        withContext(Dispatchers.Default) {
            val e = ensureEngine(ctx)
            val window = existing.takeLast(60)
            val listing = if (window.isEmpty()) "(empty)"
                else window.mapIndexed { i, x -> "" + (i + 1) + ". [" + x.category + "] " + x.text }.joinToString("\n")
            val system = "You maintain the story bible for a fiction writer. You get the current bible as numbered lines, then a new note from the writer. " +
                "Update the bible so it matches the note. If the note corrects or changes something already in the bible, EDIT that numbered line instead of adding a duplicate. " +
                "If the note says something in the bible is wrong or dropped, REMOVE it. Only ADD things that are genuinely new. " +
                "Categories: Characters (people and their traits), Places (settings), Threads (plot events and story lines), Questions (open problems for the writer). " +
                "Output one line per change and nothing else, in exactly these forms:\n" +
                "ADD | Category | Name | one short detail\n" +
                "EDIT | number | Name | new one short detail\n" +
                "REMOVE | number\n" +
                "At most 8 lines. If nothing changes output NONE.\n\n" +
                "Example bible:\n1. [Characters] Maya: Retired lighthouse keeper hiding a letter\n2. [Places] Harbor town: Floods in chapter three\n" +
                "Example note: Actually Maya is a fisherwoman, not a keeper. The town never floods. Her son is named Tom.\n" +
                "Example output:\nEDIT | 1 | Maya | Fisherwoman hiding a letter\nREMOVE | 2\nADD | Characters | Tom | Maya's son"
            val raw = taskLock.withLock {
                e.createConversation(ConversationConfig(systemInstruction = Contents.of(system))).use { c ->
                    c.sendMessage("Current bible:\n" + listing + "\n\nNew note: " + note).toString()
                }
            }
            lastRaw = raw.trim()
            val stems = listOf("character" to "Characters", "place" to "Places", "thread" to "Threads", "question" to "Questions")
            fun catOf(s: String) = stems.firstOrNull { s.contains(it.first, ignoreCase = true) }?.second
            fun nameOf(t: String): String {
                val i = t.indexOf(": ")
                return (if (i in 1..40) t.substring(0, i) else t).trim().lowercase()
            }
            fun entryAt(s: String): Entry? {
                val n = s.filter { it.isDigit() }.toIntOrNull() ?: return null
                return window.getOrNull(n - 1)
            }
            fun compose(name: String, detail: String?) = if (detail.isNullOrBlank()) name else name + ": " + detail
            val touched = mutableSetOf<Long>()
            val out = mutableListOf<Proposal>()
            for (line in raw.lines()) {
                val cleaned = line.trim().replace("**", "")
                    .trimStart { it == '-' || it == '*' || it == '•' || it == ' ' }
                if (cleaned.isEmpty() || cleaned.startsWith("none", ignoreCase = true)) continue
                val parts = cleaned.split("|").map { it.trim().trim('*', ' ') }
                if (parts.size < 2) continue
                val verb = parts[0].lowercase()
                when {
                    verb.startsWith("remove") || verb.startsWith("delete") -> {
                        val t = entryAt(parts[1]) ?: continue
                        if (touched.add(t.id)) out.add(Proposal(Act.REMOVE, t.category, t.text, t.id, t.text))
                    }
                    verb.startsWith("edit") || verb.startsWith("update") || verb.startsWith("change") -> {
                        val t = entryAt(parts[1]) ?: continue
                        val oldName = if (t.text.contains(": ")) t.text.substringBefore(": ") else ""
                        val text = when {
                            parts.size >= 4 -> compose(parts[2], parts[3])
                            parts.size == 3 && parts[2].contains(": ") -> parts[2]
                            parts.size == 3 && oldName.isNotEmpty() -> oldName + ": " + parts[2]
                            parts.size == 3 -> parts[2]
                            else -> continue
                        }
                        if (text != t.text && touched.add(t.id)) out.add(Proposal(Act.EDIT, t.category, text, t.id, t.text))
                    }
                    else -> {
                        val rest = if (verb.startsWith("add")) parts.drop(1) else parts
                        if (rest.size < 2 || rest[1].isEmpty()) continue
                        val cat = catOf(rest[0]) ?: continue
                        val text = compose(rest[1], rest.getOrNull(2))
                        val same = existing.firstOrNull { it.category == cat && nameOf(it.text) == rest[1].lowercase() }
                        if (same != null) {
                            if (same.text != text && touched.add(same.id)) out.add(Proposal(Act.EDIT, cat, text, same.id, same.text))
                        } else if (out.none { it.act == Act.ADD && it.text.equals(text, ignoreCase = true) }) {
                            out.add(Proposal(Act.ADD, cat, text))
                        }
                    }
                }
            }
            out.take(8)
        }
}
