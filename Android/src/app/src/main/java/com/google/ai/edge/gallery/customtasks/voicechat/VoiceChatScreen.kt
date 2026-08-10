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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.ai.edge.gallery.customtasks.common.CustomTaskData
import java.net.Inet4Address
import java.net.NetworkInterface

/** Main screen of the Voice Chat custom task. */
@Composable
fun VoiceChatScreen(data: CustomTaskData) {
  val viewModel: VoiceChatViewModel =
    viewModel(factory = VoiceChatViewModel.provideFactory(data.modelManagerViewModel))
  val state by viewModel.uiState.collectAsState()

  val listState = rememberLazyListState()
  LaunchedEffect(state.transcript.size) {
    if (state.transcript.isNotEmpty()) {
      listState.animateScrollToItem(state.transcript.size - 1)
    }
  }

  Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

    // --- Connection card -----------------------------------------------------
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("WebRTC connection", style = MaterialTheme.typography.titleMedium)
      OutlinedTextField(
        value = state.serverUrl,
        onValueChange = viewModel::updateServerUrl,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("Signaling server") },
        placeholder = { Text("ws://192.168.0.5:8080 or host:port") },
      )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = state.room,
          onValueChange = viewModel::updateRoom,
          modifier = Modifier.weight(1f),
          singleLine = true,
          label = { Text("Room code") },
          placeholder = { Text("my-room-123") },
        )
        if (state.connected) {
          OutlinedButton(onClick = viewModel::disconnect, modifier = Modifier.height(56.dp)) {
            Text("Disconnect")
          }
        } else {
          Button(onClick = viewModel::connect, modifier = Modifier.height(56.dp)) {
            Text("Connect")
          }
        }
      }
      Text(state.status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
      if (!state.connected) {
        val ip = remember { findLocalIpv4Address() }
        if (ip != null) {
          Text(
            "Run the server on your PC/LAN and open http://$ip:8080 in a browser (or use a public host).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    HorizontalDivider()

    // --- STT model card ------------------------------------------------------
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("On-device speech recognition (Vosk)", style = MaterialTheme.typography.titleMedium)
      Row(verticalAlignment = Alignment.CenterVertically) {
        LanguageSelector(
          selected = state.language,
          onSelect = viewModel::setLanguage,
        )
        Spacer(Modifier.width(12.dp))
        if (state.sttProgress != null) {
          if (state.sttProgress == -1f) {
            Text("Unpacking…", style = MaterialTheme.typography.bodySmall)
          } else {
            Column(modifier = Modifier.weight(1f)) {
              LinearProgressIndicator(
                progress = { state.sttProgress ?: 0f },
                modifier = Modifier.fillMaxWidth(),
              )
              Text(
                "Downloading ${((state.sttProgress ?: 0f) * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
              )
            }
          }
        } else if (state.sttModelReady) {
          Text("✓ Ready", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        } else {
          TextButton(onClick = viewModel::ensureSttModel) { Text("Download (~40 MB)") }
        }
      }
    }

    HorizontalDivider()

    // --- Transcript ----------------------------------------------------------
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text("Conversation", style = MaterialTheme.typography.titleMedium)
      TextButton(onClick = viewModel::clearConversation) { Text("Clear") }
    }

    Surface(
      modifier = Modifier.fillMaxSize(),
      shape = MaterialTheme.shapes.medium,
      tonalElevation = 1.dp,
    ) {
      if (state.transcript.isEmpty()) {
        BoxCentered(
          text =
            if (state.busy) "Listening…"
            else "Open the same room code in a browser, then just talk.\nThe phone will answer with voice.",
        )
      } else {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
          items(state.transcript) { line ->
            ChatBubble(line)
          }
        }
      }
    }

    if (state.busy) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(8.dp))
        Text("LLM is thinking…", style = MaterialTheme.typography.bodySmall)
      }
    }
  }
}

@Composable
private fun BoxCentered(text: String) {
  androidx.compose.foundation.layout.Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text,
      textAlign = TextAlign.Center,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(24.dp),
    )
  }
}

@Composable
private fun ChatBubble(line: VoiceChatViewModel.ChatLine) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
    horizontalAlignment = if (line.fromUser) Alignment.End else Alignment.Start,
  ) {
    Surface(
      shape = MaterialTheme.shapes.medium,
      color =
        if (line.fromUser) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.secondaryContainer,
    ) {
      Text(
        line.text,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodyMedium,
      )
    }
  }
}

@Composable
private fun LanguageSelector(
  selected: VoiceChatStt.Language,
  onSelect: (VoiceChatStt.Language) -> Unit,
) {
  var expanded by remember { mutableStateOf(false) }
  Column {
    OutlinedButton(onClick = { expanded = true }) {
      Text(selected.displayName)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      VoiceChatStt.Language.entries.forEach { lang ->
        DropdownMenuItem(text = { Text(lang.displayName) }, onClick = {
          onSelect(lang)
          expanded = false
        })
      }
    }
  }
}

private fun findLocalIpv4Address(): String? {
  return try {
    NetworkInterface.getNetworkInterfaces()
      ?.toList()
      ?.filter { it.isUp && !it.isLoopback }
      ?.flatMap { it.inetAddresses.toList() }
      ?.filterIsInstance<Inet4Address>()
      ?.map { it.hostAddress }
      ?.firstOrNull { !it.startsWith("127.") }
  } catch (e: Exception) {
    null
  }
}
