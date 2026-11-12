package app.margin

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.getOfflineTtsConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.net.URL

/** Natural on-device voice (Piper neural TTS via sherpa-onnx). Falls back to the system voice until ready. */
object SpeechOut {
    private const val NAME = "vits-piper-en_US-ryan-high"
    private const val URL_ = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/" + NAME + ".tar.bz2"

    var ready by mutableStateOf(false)
        private set

    private var tts: OfflineTts? = null
    private var track: AudioTrack? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<Pair<Int, String>>(Channel.UNLIMITED)
    @Volatile private var generation = 0
    private var started = false

    fun prepare(ctx: Context) {
        if (started) return
        started = true
        val app = ctx.applicationContext
        scope.launch {
            try {
                val root = File(app.filesDir, "voice")
                val dir = File(root, NAME)
                val done = File(root, NAME + ".done")
                if (!done.exists()) {
                    root.mkdirs()
                    download(root)
                    done.writeText("ok")
                }
                val cfg = getOfflineTtsConfig(
                    modelDir = dir.absolutePath,
                    modelName = "en_US-ryan-high.onnx",
                    dataDir = dir.absolutePath + "/espeak-ng-data",
                    numThreads = 3
                )
                val engine = OfflineTts(config = cfg)
                tts = engine
                ready = true
                loop(engine)
            } catch (t: Throwable) {
                ready = false
                started = false
            }
        }
    }

    private fun download(root: File) {
        val conn = URL(URL_).openConnection()
        conn.connectTimeout = 20000
        conn.readTimeout = 30000
        TarArchiveInputStream(BZip2CompressorInputStream(BufferedInputStream(conn.getInputStream()))).use { tar ->
            while (true) {
                val e = tar.nextEntry ?: break
                val out = File(root, e.name)
                if (!out.canonicalPath.startsWith(root.canonicalPath)) continue
                if (e.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { tar.copyTo(it) }
                }
            }
        }
    }

    private suspend fun loop(engine: OfflineTts) {
        val sr = engine.sampleRate()
        val minBuf = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sr)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuf, sr * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = t
        t.play()
        for ((gen, text) in queue) {
            if (gen != generation) continue
            try {
                engine.generateWithCallback(text = text, sid = 0, speed = 1.0f) { samples ->
                    if (gen != generation) {
                        0
                    } else {
                        t.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                        1
                    }
                }
            } catch (e: Throwable) {
            }
        }
    }

    fun speak(sentence: String) {
        queue.trySend(generation to sentence)
    }

    fun stop() {
        generation++
        try {
            track?.pause()
            track?.flush()
            track?.play()
        } catch (e: Throwable) {
        }
    }
}
