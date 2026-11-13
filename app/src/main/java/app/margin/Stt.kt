package app.margin

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.ConnectivityManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.net.URL

/** Shared tar.bz2 downloader with progress. */
internal fun fetchTarBz2(url: String, root: File, onProgress: (Float) -> Unit) {
    val conn = URL(url).openConnection()
    conn.connectTimeout = 20000
    conn.readTimeout = 30000
    val total = conn.contentLengthLong
    var read = 0L
    val counting = object : FilterInputStream(conn.getInputStream()) {
        override fun read(): Int {
            val b = super.read()
            if (b >= 0) read++
            return b
        }

        override fun read(buf: ByteArray, off: Int, len: Int): Int {
            val n = super.read(buf, off, len)
            if (n > 0) {
                read += n
                if (total > 0) onProgress(read.toFloat() / total)
            }
            return n
        }
    }
    TarArchiveInputStream(BZip2CompressorInputStream(BufferedInputStream(counting as InputStream))).use { tar ->
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

internal fun onUnmetered(ctx: Context): Boolean {
    val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    return !cm.isActiveNetworkMetered
}

/** High-accuracy on-device speech recognition: NVIDIA Parakeet TDT 0.6B v3 via sherpa-onnx. */
object SpeechIn {
    private const val NAME = "sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8"
    private const val URL_ = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/" + NAME + ".tar.bz2"

    var ready by mutableStateOf(false)
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    var downloading by mutableStateOf(false)
        private set

    private var recognizer: OfflineRecognizer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false

    fun prepare(ctx: Context, force: Boolean = false) {
        if (started) return
        val app = ctx.applicationContext
        val root = File(app.filesDir, "stt")
        val done = File(root, NAME + ".done")
        if (!done.exists() && !force && !AppSettings.mobileData && !onUnmetered(app)) return
        started = true
        scope.launch {
            try {
                if (!done.exists()) {
                    root.mkdirs()
                    downloading = true
                    fetchTarBz2(URL_, root) { progress = it }
                    done.writeText("ok")
                    downloading = false
                }
                val dir = File(root, NAME).absolutePath
                val cfg = OfflineRecognizerConfig(
                    featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
                    modelConfig = OfflineModelConfig(
                        transducer = OfflineTransducerModelConfig(
                            encoder = "$dir/encoder.int8.onnx",
                            decoder = "$dir/decoder.int8.onnx",
                            joiner = "$dir/joiner.int8.onnx"
                        ),
                        tokens = "$dir/tokens.txt",
                        modelType = "nemo_transducer",
                        numThreads = 4
                    )
                )
                recognizer = OfflineRecognizer(config = cfg)
                ready = true
            } catch (t: Throwable) {
                downloading = false
                ready = false
                started = false
            }
        }
    }

    suspend fun transcribe(samples: FloatArray): String = withContext(Dispatchers.Default) {
        val r = recognizer ?: return@withContext ""
        if (samples.size < 4800) return@withContext ""
        val stream = r.createStream()
        try {
            stream.acceptWaveform(samples, 16000)
            r.decode(stream)
            r.getResult(stream).text.trim()
        } finally {
            stream.release()
        }
    }
}

/** Records 16 kHz mono audio from the mic until stop(). */
class PcmRecorder {
    private var record: AudioRecord? = null
    private var thread: Thread? = null
    @Volatile private var running = false
    private val chunks = ArrayList<ShortArray>()

    @SuppressLint("MissingPermission")
    fun start() {
        val min = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, 16000) * 2
        )
        record = rec
        running = true
        rec.startRecording()
        thread = Thread {
            val buf = ShortArray(3200)
            var total = 0
            while (running && total < 16000 * 300) {
                val n = rec.read(buf, 0, buf.size)
                if (n > 0) {
                    chunks.add(buf.copyOf(n))
                    total += n
                }
            }
        }.also { it.start() }
    }

    fun stop(): FloatArray {
        running = false
        try { thread?.join(1500) } catch (e: Throwable) {}
        try {
            record?.stop()
            record?.release()
        } catch (e: Throwable) {}
        record = null
        val total = chunks.sumOf { it.size }
        val out = FloatArray(total)
        var i = 0
        for (c in chunks) for (s in c) out[i++] = s / 32768f
        return out
    }
}

/** Hold-to-talk listener: Parakeet when it's ready, Android's recognizer until then. */
class Listener(
    context: Context,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onStatus: (String) -> Unit,
    onError: (String) -> Unit
) {
    private var buf = ""
    private var part = ""
    private var usingModel = false
    private var rec: PcmRecorder? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val fallback = VoiceCapture(
        context = context,
        onPartial = {
            part = it
            onPartial((buf + " " + part).trim())
        },
        onSegment = {
            buf = (buf + " " + it).trim()
            part = ""
            onPartial(buf)
        },
        onDone = {
            val t = (buf + " " + part).trim()
            buf = ""
            part = ""
            onFinal(t)
        },
        onError = onError
    )

    fun start() {
        buf = ""
        part = ""
        if (SpeechIn.ready) {
            usingModel = true
            rec = PcmRecorder().also { it.start() }
            onPartial("")
        } else {
            usingModel = false
            fallback.start()
        }
    }

    fun stop() {
        if (usingModel) {
            val r = rec
            rec = null
            onStatus("Transcribing")
            scope.launch {
                val samples = withContext(Dispatchers.IO) { r?.stop() ?: FloatArray(0) }
                val text = try { SpeechIn.transcribe(samples) } catch (t: Throwable) { "" }
                onStatus("")
                onFinal(text)
            }
        } else {
            fallback.stop()
        }
    }

    fun release() {
        fallback.release()
        try { rec?.stop() } catch (e: Throwable) {}
        scope.cancel()
    }
}
