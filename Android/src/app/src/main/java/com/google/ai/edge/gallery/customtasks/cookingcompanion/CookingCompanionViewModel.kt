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

package com.google.ai.edge.gallery.customtasks.cookingcompanion

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.ui.common.chat.ChatMessage
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageText
import com.google.ai.edge.gallery.ui.common.chat.ChatSide
import com.google.ai.edge.gallery.ui.llmchat.LlmChatModelHelper
import com.google.ai.edge.gallery.ui.llmchat.LlmModelInstance
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "AGCookingViewModel"

/** The UI state of the Cooking Companion screen. */
data class CookingUiState(
  // Whether the app is processing the user input.
  val processing: Boolean = false,
  // The messages in the conversation history.
  val messages: List<ChatMessage> = listOf(),
  // The number of turns.
  val numTurns: Int = 0,
)

/** The ViewModel of the Cooking Companion screen. */
@HiltViewModel
class CookingCompanionViewModel @Inject constructor() : ViewModel() {
  protected val _uiState = MutableStateFlow(CookingUiState())
  val uiState = _uiState.asStateFlow()

  private val _isResettingConversation = MutableStateFlow(false)
  private val isResettingConversation = _isResettingConversation.asStateFlow()

  /**
   * Sends the user's request to the model and processes the response.
   *
   * The tools defined in [CookingTools] will be invoked during the process.
   */
  fun getCommand(
    model: Model,
    instructionText: String,
    onDone: (String) -> Unit,
    onError: (String) -> Unit,
  ) {
    if (model.instance == null) {
      setProcessing(processing = false)
      return
    }

    incrementNumTurns()
    Log.d(TAG, "Turn #: ${uiState.value.numTurns}")

    addMessage(message = ChatMessageText(content = instructionText, side = ChatSide.USER))

    viewModelScope.launch(Dispatchers.Default) {
      Log.d(TAG, "Start processing user request: '$instructionText'")
      setProcessing(processing = true)

      isResettingConversation.first { !it }
      Log.d(TAG, "Done waiting. Start inference.")

      val instance = model.instance as LlmModelInstance
      val conversation = instance.conversation
      val contents = mutableListOf<Content>()
      if (instructionText.trim().isNotEmpty()) {
        contents.add(Content.Text(instructionText))
      }

      try {
        val responseMessage = conversation.sendMessage(Contents.of(contents))
        val response = responseMessage.toString()
        Log.d(TAG, "Done processing. Response: $response")
        onDone(response)
      } catch (e: Exception) {
        Log.e(TAG, "Failed to run inference", e)
        onError(e.message ?: "Unknown error")
      } finally {
        setProcessing(processing = false)
      }
    }
  }

  fun addMessage(message: ChatMessage) {
    val newMessages = _uiState.value.messages.toMutableList()
    newMessages.add(message)
    _uiState.update { _uiState.value.copy(messages = newMessages) }
  }

  fun clearMessages() {
    _uiState.update { _uiState.value.copy(messages = listOf()) }
  }

  fun setProcessing(processing: Boolean) {
    _uiState.update { uiState.value.copy(processing = processing) }
  }

  fun incrementNumTurns() {
    _uiState.update { uiState.value.copy(numTurns = uiState.value.numTurns + 1) }
  }

  fun resetNumTurns() {
    _uiState.update { uiState.value.copy(numTurns = 0) }
  }
}
