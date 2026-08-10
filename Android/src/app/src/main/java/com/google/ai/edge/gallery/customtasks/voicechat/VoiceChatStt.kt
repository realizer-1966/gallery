/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery.customtasks.voicechat

import android.content.Context
import android.util.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

/**
 * On-device speech recognition with Vosk.
 *
 * The browser peer's microphone audio arrives over WebRTC as 48 kHz stereo PCM; Vosk needs
 * 16 kHz mono, so [feed] mixes the channels down and decimates with a small box filter
 * before handing the samples to the recognizer.
 *
 * The Vosk model (~40 MB) is downloaded from alphacephei.com on first use and unpacked
 * under the app's files directory.
 */
class VoiceChatStt(private val context: Context) {

  enum class Language(
    val modelId: String,
    val modelUrl: String,
    val locale: Locale,
    val displayName: String,
  ) {
    ENGLISH(
      modelId = "vosk-model-small-en-us-0.15",
      modelUrl = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
      locale = Locale.US,
      displayName = "English",
    ),
    KOREAN(
      modelId = "vosk-model-small-ko-0.22",
      modelUrl = "https://alphacephei.com/vosk/models/vosk-model-small-ko-0.22.zip",
      locale = Locale.KOREAN,
      displayName = "Korean",
    ),
  }

  /** Invoked on the recognizer thread whenever a final phrase is recognized. */
  var onFinalResult: ((String) -> Unit)? = null

  private var model: Model? = null
  private var recognizer: Recognizer? = null
  private var activeLanguage: Language? = null
  private var downsampler = Downsampler()

  fun modelDir(language: Language): File =
    File(context.filesDir, "vosk_models/${language.modelId}")

  fun isModelReady(language: Language): Boolean =
    model != null && activeLanguage == language && recognizer != null

  /**
   * Downloads (if needed) and unpacks the model, then creates the recognizer.
   * @param onProgress progress 0..1 while downloading, -1 while unzipping.
   */
  suspend fun ensureModel(
    language: Language,
    onProgress: (Float) -> Unit,
  ): Boolean =
    withContext(Dispatchers.IO) {
      val dir = modelDir(language)
      if (!File(dir, "am/final.mdl").exists()) {
        val zip = File(context.cacheDir, "${language.modelId}.zip")
        download(language.modelUrl, zip, onProgress)
        onProgress(-1f)
        unzip(zip, dir)
        zip.delete()
      }
      try {
        if (activeLanguage != language) {
          recognizer?.close()
          model?.close()
          recognizer = null
          model = null
        }
        if (model == null) {
          model = Model(dir.absolutePath)
          activeLanguage = language
        }
        recognizer = Recognizer(model, 16000.0f)
        recognizer!!.reset()
        Log.i(TAG, "Vosk model '$language' ready: $dir")
        true
      } catch (e: Exception) {
        Log.e(TAG, "Failed to init Vosk model", e)
        false
      }
    }

  /** Feed raw PCM from the WebRTC audio sink (48 kHz stereo typically). */
  fun feed(pcm: ByteArray, sampleRate: Int, channels: Int) {
    val rec = recognizer ?: return
    try {
      val mono16k = downsampler.process(pcm, sampleRate, channels)
      if (mono16k.isEmpty()) return
      if (rec.acceptWaveForm(mono16k)) {
        val text = parseText(rec.result)
        if (text.isNotBlank()) {
          Log.d(TAG, "STT: $text")
          onFinalResult?.invoke(text)
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Vosk feed failed", e)
    }
  }

  fun stop() {
    recognizer?.close()
    recognizer = null
    model?.close()
    model = null
    activeLanguage = null
    downsampler = Downsampler()
  }

  private fun parseText(json: String): String {
    return try {
      JSONObject(json).optString("text", "").trim()
    } catch (e: Exception) {
      ""
    }
  }

  private fun download(urlString: String, target: File, onProgress: (Float) -> Unit) {
    Log.i(TAG, "Downloading $urlString -> $target")
    val connection = URL(urlString).openConnection() as HttpURLConnection
    connection.connectTimeout = 15000
    connection.readTimeout = 30000
    connection.instanceFollowRedirects = true
    try {
      connection.connect()
      check(connection.responseCode == HttpURLConnection.HTTP_OK) {
        "HTTP ${connection.responseCode} downloading ${connection.url}"
      }
      val total = connection.contentLengthLong.coerceAtLeast(1L)
      var received = 0L
      target.parentFile?.mkdirs()
      connection.inputStream.use { input ->
        target.outputStream().use { output ->
          val buffer = ByteArray(64 * 1024)
          while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            received += read
            onProgress((received.toDouble() / total).toFloat())
          }
        }
      }
    } finally {
      connection.disconnect()
    }
  }

  private fun unzip(zipFile: File, destDir: File) {
    destDir.mkdirs()
    ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
      var entry = zis.nextEntry
      while (entry != null) {
        val target = File(destDir, entry.name)
        if (entry.isDirectory) {
          target.mkdirs()
        } else {
          target.parentFile?.mkdirs()
          target.outputStream().use { out -> zis.copyTo(out) }
        }
        zis.closeEntry()
        entry = zis.nextEntry
      }
    }
  }

  /**
   * Converts multi-channel PCM at an arbitrary sample rate into 16 kHz mono.
   * Channels are averaged; the rate is reduced with an N-tap box filter.
   */
  private class Downsampler {
    fun process(pcm: ByteArray, sampleRate: Int, channels: Int): ByteArray {
      if (sampleRate < 16000 || channels < 1) return ByteArray(0)
      val ratio = sampleRate / 16000
      if (ratio <= 1 && channels == 1) return pcm
      val frameCount = pcm.size / (2 * channels)
      val outFrames = frameCount / ratio
      if (outFrames <= 0) return ByteArray(0)
      val out = ShortArray(outFrames)
      var oi = 0
      var i = 0
      while (i + ratio <= frameCount) {
        var acc = 0L
        for (j in 0 until ratio) {
          val base = ((i + j) * channels) * 2
          var chSum = 0
          for (c in 0 until channels) {
            val idx = base + c * 2
            chSum += ((pcm[idx].toInt() and 0xFF) or (pcm[idx + 1].toInt() shl 8))
          }
          acc += chSum / channels
        }
        out[oi++] = (acc / ratio).toShort()
        i += ratio
      }
      val bytes = ByteArray(outFrames * 2)
      var b = 0
      for (v in out) {
        bytes[b++] = (v.toInt() and 0xFF).toByte()
        bytes[b++] = ((v.toInt() shr 8) and 0xFF).toByte()
      }
      return bytes
    }
  }

  private companion object {
    const val TAG = "AGVoiceChatStt"
  }
}
