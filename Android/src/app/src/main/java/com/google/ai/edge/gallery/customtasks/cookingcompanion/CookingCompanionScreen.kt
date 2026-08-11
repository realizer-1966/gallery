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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.data.ModelDownloadStatusType
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.ui.common.chat.ChatMessage
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageText
import com.google.ai.edge.gallery.ui.common.chat.ChatSide
import com.google.ai.edge.gallery.ui.common.chat.MessageBodyText
import com.google.ai.edge.gallery.ui.common.textandvoiceinput.HoldToDictateViewModel
import com.google.ai.edge.gallery.ui.common.textandvoiceinput.TextAndVoiceInput
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import com.google.ai.edge.litertlm.ToolProvider
import kotlinx.coroutines.flow.Flow

private const val TAG = "AGCookingScreen"

/** The main screen for the Cooking Companion task. */
@Composable
fun CookingCompanionScreen(
  task: Task,
  modelManagerViewModel: ModelManagerViewModel,
  tools: List<ToolProvider>,
  bottomPadding: Dp,
  setAppBarControlsDisabled: (Boolean) -> Unit,
  setTopBarVisible: (Boolean) -> Unit,
  commandFlow: Flow<CookingCommand>,
  viewModel: CookingCompanionViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiState.collectAsState()
  val modelManagerUiState by modelManagerViewModel.uiState.collectAsState()
  val model = modelManagerUiState.selectedModel
  val listState = rememberLazyListState()
  val holdToDictateViewModel: HoldToDictateViewModel = hiltViewModel()
  var clearTextTrigger by remember { mutableLongStateOf(0L) }

  val curDownloadStatus = modelManagerUiState.modelDownloadStatus[model.name]?.status
  setAppBarControlsDisabled(
    curDownloadStatus == ModelDownloadStatusType.SUCCEEDED &&
      (!modelManagerUiState.isModelInitialized(model = model) || uiState.processing)
  )

  // Render CookingCommands emitted by the model's tools into the chat history.
  LaunchedEffect(commandFlow) {
    commandFlow.collect { command ->
      Log.d(TAG, "Received command: ${command.action} payload=${command.payload}")
      val label = stringResource(R.string.cooking_command_recorded)
      viewModel.addMessage(
        message = ChatMessageText(content = "$label\n\n${command.payload}", side = ChatSide.AGENT)
      )
    }
  }

  // Auto-scroll to the latest message.
  LaunchedEffect(uiState.messages.size) {
    if (uiState.messages.isNotEmpty()) {
      listState.animateScrollToItem(uiState.messages.size - 1)
    }
  }

  fun processInstructionText(text: String) {
    clearTextTrigger = System.currentTimeMillis()
    if (text.trim().isNotEmpty()) {
      viewModel.getCommand(
        model = model,
        instructionText = text,
        onDone = { response ->
          viewModel.addMessage(message = ChatMessageText(content = response, side = ChatSide.AGENT))
        },
        onError = { error ->
          viewModel.addMessage(
            message = ChatMessageText(content = error, side = ChatSide.SYSTEM)
          )
        },
      )
    }
  }

  Column(
    modifier = Modifier.fillMaxSize().padding(bottom = bottomPadding).imePadding()
  ) {
    if (!modelManagerUiState.isModelInitialized(model = model)) {
      // Loading indicator while the model is initializing.
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
          trackColor = MaterialTheme.colorScheme.surfaceVariant,
          strokeWidth = 3.dp,
          modifier = Modifier.size(24.dp),
        )
      }
    } else {
      LazyColumn(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        items(uiState.messages, key = { System.identityHashCode(it) }) { message ->
          MessageRow(message = message)
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        TextAndVoiceInput(
          task = task,
          processing = uiState.processing,
          holdToDictateViewModel = holdToDictateViewModel,
          modifier = Modifier.padding(start = 16.dp).weight(1f),
          onDone = { text -> processInstructionText(text = text) },
          onAmplitudeChanged = {},
          clearTextTrigger = clearTextTrigger,
          defaultTextInputMode = true,
        )
        Box(
          modifier = Modifier.size(48.dp).padding(end = 8.dp),
          contentAlignment = Alignment.Center,
        ) {
          if (uiState.processing) {
            CircularProgressIndicator(
              trackColor = MaterialTheme.colorScheme.surfaceVariant,
              strokeWidth = 3.dp,
              modifier = Modifier.size(24.dp),
            )
          }
        }
      }
    }
  }
}

/** Renders a single chat message using the shared message body components. */
@Composable
private fun MessageRow(message: ChatMessage) {
  when (message) {
    is ChatMessageText -> MessageBodyText(message = message, inProgress = false)
    else -> {
      // Other message types (warning, info, etc.) fall back to a plain text rendering.
      Text(
        text = message.toString(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
