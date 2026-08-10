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
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * On-device text-to-speech using the Android TTS engine.
 *
 * The synthesized answer is rendered to a WAV file, parsed, and streamed to the browser
 * peer as raw 16-bit PCM chunks over the WebRTC data channel.
 *
 * Callbacks:
 *  - [onAudioStart]: called before the first PCM chunk.
 *  - [onPcm]: called with raw 16-bit little-endian PCM.
 *  - [onAudioEnd]: called after the last chunk.
 */
class VoiceChatTts(
  private val context: Context,
  private val onAudioStart: (sampleRate: Int, channels: Int) -> Unit,
  private val onPcm: (ByteArray) -> Unit,
  private val onAudioEnd: () -> Unit,
) {
  private val scope = CoroutineScope(Dispatchers.IO)
  private var tts: TextToSpeech? = null
  private var ready = false

  /** Total frames per data-channel PCM chunk (~93 ms at 22.05 kHz). */
  private val chunkFrames = 2048

  fun init() {
    if (tts != null) return
    tts =
      TextToSpeech(context) { status ->
        if (status == TextToSpeech.SUCCESS) {
          ready = true
          Log.i(TAG, "TTS engine ready")
          tts?.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
              override fun onStart(utteranceId: String?) {}
              override fun onDone(utteranceId: String?) = sendWav(utteranceId)
              override fun onError(utteranceId: String?) {
                Log.e(TAG, "TTS error for $utteranceId")
                onAudioEnd()
              }
            },
          )
        } else {
          Log.e(TAG, "TTS init failed: $status")
        }
      }
  }

  /** Speaks [text]; the audio is delivered through the [onPcm] callback. */
  fun speak(text: String, locale: Locale) {
    if (!ready) {
      Log.w(TAG, "TTS not ready, dropping: $text")
      return
    }
    val t = tts ?: return
    t.language = locale
    val utteranceId = UUID.randomUUID().toString()
    val file = File(context.cacheDir, "tts_$utteranceId.wav")
    val result =
      t.synthesizeToFile(
        text,
        Bundle().apply { putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId) },
        file,
        utteranceId,
      )
    if (result != TextToSpeech.SUCCESS) {
      Log.e(TAG, "synthesizeToFile failed: $result")
      file.delete()
      onAudioEnd()
    }
  }

  fun shutdown() {
    tts?.stop()
    tts?.shutdown()
    tts = null
    ready = false
  }

  private fun sendWav(utteranceId: String?) {
    val file = File(context.cacheDir, "tts_$utteranceId.wav")
    if (!file.exists()) {
      onAudioEnd()
      return
    }
    scope.launch {
      try {
        val info = parseWav(file)
        val channels = info.channels.coerceAtLeast(1)
        onAudioStart(info.sampleRate, channels)
        val pcm = ByteArray(info.dataSize.toInt())
        file.inputStream().use { input ->
          input.skip(info.dataOffset)
          var read = 0
          while (read < pcm.size) {
            val n = input.read(pcm, read, pcm.size - read)
            if (n < 0) break
            read += n
          }
        }
        for (chunk in chunkPcm(pcm, chunkFrames, bytesPerFrame = 2)) {
          onPcm(chunk)
        }
        onAudioEnd()
        Log.d(TAG, "TTS sent ${pcm.size} bytes @ ${info.sampleRate} Hz")
      } catch (e: Exception) {
        Log.e(TAG, "Failed to stream TTS audio", e)
        onAudioEnd()
      } finally {
        file.delete()
      }
    }
  }

  private companion object {
    const val TAG = "AGVoiceChatTts"
  }
}
