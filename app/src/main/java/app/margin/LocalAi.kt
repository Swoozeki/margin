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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
    private const val MODEL_NAME = "gemma-4-E2B-it.litertlm"
    private const val MODEL_URL =
        "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"

    var state by mutableStateOf(AiState.Missing)
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    var message by mutableStateOf("")
        private set

    private var engine: Engine? = null
    private val engineLock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun modelFile(ctx: Context): File {
        val dir = File(ctx.filesDir, "models")
        dir.mkdirs()
        return File(dir, MODEL_NAME)
    }

    fun refresh(ctx: Context) {
        if (state == AiState.Downloading || state == AiState.Loading || state == AiState.Ready) return
        val f = modelFile(ctx)
        state = if (f.exists() && f.length() > 0) AiState.Installed else AiState.Missing
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
        val target = modelFile(ctx)
        val tmp = File(target.parentFile, MODEL_NAME + ".part")
        try {
            val existing = if (tmp.exists()) tmp.length() else 0L
            val conn = URL(MODEL_URL).openConnection() as HttpURLConnection
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
            state = AiState.Installed
        } catch (e: Exception) {
            state = AiState.Missing
            message = "Download paused: " + (e.message ?: "connection lost") + ". Tap Download again to resume."
        }
    }

    private fun importFrom(ctx: Context, uri: Uri) {
        state = AiState.Downloading
        progress = 0f
        message = ""
        val target = modelFile(ctx)
        val tmp = File(target.parentFile, MODEL_NAME + ".part")
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

    class Chat internal constructor(private val conversation: Conversation) {
        fun send(text: String): Flow<String> = conversation.sendMessageAsync(text).map { it.toString() }

        fun close() {
            try {
                conversation.close()
            } catch (t: Throwable) {
            }
        }
    }

    suspend fun startChat(ctx: Context, system: String): Chat = withContext(Dispatchers.Default) {
        val e = ensureEngine(ctx)
        Chat(e.createConversation(ConversationConfig(systemInstruction = Contents.of(system))))
    }

    suspend fun transcribe(ctx: Context, imagePath: String): String = withContext(Dispatchers.Default) {
        val e = ensureEngine(ctx)
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

    suspend fun suggestCategory(ctx: Context, text: String): String? = withContext(Dispatchers.Default) {
        val e = ensureEngine(ctx)
        val config = ConversationConfig(
            systemInstruction = Contents.of(
                "You sort story notes for a fiction writer. Reply with exactly one word: Characters, Places, Threads, or Questions. Characters = a person or their traits. Places = settings or locations. Threads = plot events or story lines. Questions = open questions the writer must answer."
            )
        )
        e.createConversation(config).use { c ->
            val reply = c.sendMessage(text).toString()
            BIBLE_CATEGORIES.firstOrNull { reply.contains(it, ignoreCase = true) }
        }
    }
}
