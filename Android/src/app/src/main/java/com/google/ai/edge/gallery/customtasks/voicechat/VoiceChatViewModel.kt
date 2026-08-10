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

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.ai.edge.gallery.ui.llmchat.LlmChatModelHelper
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Orchestrates the Voice Chat pipeline:
 *
 *   browser mic --WebRTC--> on-device STT (Vosk) --text--> LiteRT LLM --text-->
 *   Android TTS --PCM over data channel--> browser speaker
 */
class VoiceChatViewModel(
  application: Application,
  private val modelManagerViewModel: ModelManagerViewModel,
) : AndroidViewModel(application) {

  data class ChatLine(val fromUser: Boolean, val text: String)

  data class UiState(
    val serverUrl: String = "",
    val room: String = "",
    val connected: Boolean = false,
    val status: String = "Enter the signaling server URL and a room code, then press Connect.",
    val language: VoiceChatStt.Language = VoiceChatStt.Language.ENGLISH,
    val sttModelReady: Boolean = false,
    val sttProgress: Float? = null,
    val transcript: List<ChatLine> = emptyList(),
    val busy: Boolean = false,
  )

  private val _uiState = MutableStateFlow(UiState())
  val uiState: StateFlow<UiState> = _uiState.asStateFlow()

  private val stt: VoiceChatStt = VoiceChatStt(application)
  private var tts: VoiceChatTts? = null
  private var webrtc: VoiceChatWebRtc? = null
  private val llmBusy = AtomicBoolean(false)
  private val llmBuffer = StringBuilder()

  // -------------------------------------------------------------------------
  // UI actions
  // -------------------------------------------------------------------------

  fun updateServerUrl(value: String) {
    _uiState.update { it.copy(serverUrl = value) }
  }

  fun updateRoom(value: String) {
    _uiState.update { it.copy(room = value) }
  }

  fun setLanguage(language: VoiceChatStt.Language) {
    _uiState.update { it.copy(language = language) }
  }

  /** Downloads/unpacks the Vosk model for the selected language if needed. */
  fun ensureSttModel() {
    val language = _uiState.value.language
    if (stt.isModelReady(language)) return
    viewModelScope.launch {
      _uiState.update { it.copy(sttProgress = 0f, status = "Downloading STT model (${language.displayName})…") }
      val ok =
        stt.ensureModel(language) { progress ->
          _uiState.update { it.copy(sttProgress = progress) }
        }
      _uiState.update {
        it.copy(
          sttProgress = null,
          sttModelReady = ok,
          status = if (ok) "STT model ready." else "STT model download failed.",
        )
      }
    }
  }

  fun connect() {
    val state = _uiState.value
    if (state.serverUrl.isBlank() || state.room.isBlank()) {
      setStatus("Enter both the server URL and a room code.")
      return
    }
    ensureSttModel()
    ensureTts()
    stt.onFinalResult = { text -> viewModelScope.launch { handleUserUtterance(text) } }
    webrtc =
      VoiceChatWebRtc(
        context = getApplication(),
        onStatus = { msg -> viewModelScope.launch { setStatus(msg) } },
        onRtcConnected = {
          viewModelScope.launch {
            _uiState.update { it.copy(connected = true, status = "Connected — speak!") }
          }
        },
        onRtcDisconnected = {
          viewModelScope.launch {
            _uiState.update { it.copy(connected = false, status = "Disconnected.") }
          }
        },
        onMicPcm = { pcm, sampleRate, channels -> stt.feed(pcm, sampleRate, channels) },
        onClearRequest = { viewModelScope.launch { clearConversation() } },
      )
    webrtc?.connect(state.serverUrl, state.room)
  }

  fun disconnect() {
    webrtc?.disconnect()
    _uiState.update { it.copy(connected = false, status = "Disconnected.") }
  }

  /** Clears the LLM conversation on the phone and the transcript in the browser. */
  fun clearConversation() {
    modelManagerViewModel.getSelectedModel()?.let { model ->
      try {
        LlmChatModelHelper.resetConversation(model)
      } catch (e: Exception) {
        Log.w(TAG, "resetConversation failed", e)
      }
    }
    webrtc?.sendControl(VoiceChatProtocol.CLEAR_MSG)
    _uiState.update { it.copy(transcript = emptyList()) }
  }

  // -------------------------------------------------------------------------
  // Pipeline
  // -------------------------------------------------------------------------

  private fun handleUserUtterance(text: String) {
    if (text.isBlank()) return
    if (llmBusy.get()) {
      Log.d(TAG, "LLM busy, dropping utterance: $text")
      return
    }
    webrtc?.sendControl(VoiceChatProtocol.sttMsg(text))
    appendLine(fromUser = true, text = text)

    val model = modelManagerViewModel.getSelectedModel()
    if (model == null) {
      setStatus("No model selected. Pick a model on the model screen first.")
      return
    }
    if (model.instance == null) {
      setStatus("LLM not initialized yet. Wait for the model to load.")
      return
    }

    llmBusy.set(true)
    llmBuffer.setLength(0)
    _uiState.update { it.copy(busy = true) }
    LlmChatModelHelper.runInference(
      model = model,
      input = text,
      resultListener = { partial, done, _ ->
        viewModelScope.launch {
          if (done) {
            val full = llmBuffer.toString().trim()
            llmBusy.set(false)
            _uiState.update { it.copy(busy = false) }
            if (full.isNotBlank()) {
              appendLine(fromUser = false, text = full)
              webrtc?.sendControl(VoiceChatProtocol.textMsg(full))
              tts?.speak(full, _uiState.value.language.locale)
            }
          } else if (partial.isNotEmpty()) {
            llmBuffer.append(partial)
          }
        }
      },
      cleanUpListener = {},
      onError = { message ->
        viewModelScope.launch {
          llmBusy.set(false)
          _uiState.update { it.copy(busy = false) }
          setStatus("LLM error: $message")
        }
      },
    )
  }

  private fun appendLine(fromUser: Boolean, text: String) {
    _uiState.update { it.copy(transcript = it.transcript + ChatLine(fromUser, text)) }
  }

  private fun setStatus(text: String) {
    _uiState.update { it.copy(status = text) }
  }

  private fun ensureTts() {
    if (tts != null) return
    val webrtcRef = webrtc
    tts =
      VoiceChatTts(
        context = getApplication(),
        onAudioStart = { sampleRate, channels -> webrtcRef?.sendPcmStart(sampleRate, channels) },
        onPcm = { chunk -> webrtcRef?.sendPcm(chunk) },
        onAudioEnd = { webrtcRef?.sendPcmEnd() },
      )
    tts?.init()
  }

  override fun onCleared() {
    super.onCleared()
    webrtc?.release()
    tts?.shutdown()
    stt.stop()
  }

  companion object {
    private const val TAG = "AGVoiceChatViewModel"

    fun provideFactory(modelManagerViewModel: ModelManagerViewModel): ViewModelProvider.Factory =
      viewModelFactory {
        initializer {
          val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
          VoiceChatViewModel(app, modelManagerViewModel)
        }
      }
  }
}
