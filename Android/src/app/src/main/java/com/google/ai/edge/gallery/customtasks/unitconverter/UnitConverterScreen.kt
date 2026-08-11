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

package com.google.ai.edge.gallery.customtasks.unitconverter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel

/** The main screen of the unit converter custom task. */
@Composable
fun UnitConverterScreen(
  modelManagerViewModel: ModelManagerViewModel,
  viewModel: UnitConverterViewModel = hiltViewModel(),
) {
  val modelManagerUiState by modelManagerViewModel.uiState.collectAsState()
  val model = modelManagerUiState.selectedModel
  val uiState by viewModel.uiState.collectAsState()

  if (modelManagerUiState.isModelInitialized(model = model)) {
    Column(
      modifier = Modifier.fillMaxSize().padding(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      // ── Category selector ─────────────────────────────────────────────────
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        UnitDefs.categories.forEach { cat ->
          val isSelected = uiState.category == cat.id
          Box(
            modifier =
              Modifier
                .weight(1f)
                .height(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                  if (isSelected) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.surfaceVariant
                )
                .clickable { viewModel.setCategory(cat.id) },
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = cat.label,
              color =
                if (isSelected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
              fontSize = 12.sp,
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // ── Value + result display ────────────────────────────────────────────
      val units = UnitDefs.categories.firstOrNull { it.id == uiState.category }?.units.orEmpty()

      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          OutlinedTextField(
            value = uiState.value,
            onValueChange = { viewModel.setValue(it.filter { c -> c.isDigit() || c == '.' }) },
            label = { Text("값") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
          )

          Spacer(modifier = Modifier.height(12.dp))

          // From unit dropdown (horizontal chips)
          Text(
            text = "변환할 단위 (from)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(modifier = Modifier.height(4.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            units.take(8).forEach { (code, display) ->
              val isSelected = uiState.fromUnit == code
              Box(
                modifier =
                  Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                      if (isSelected) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { viewModel.setFromUnit(code) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
              ) {
                Text(
                  text = display,
                  color =
                    if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                  fontSize = 12.sp,
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(8.dp))

          // Swap button
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            IconButton(onClick = { viewModel.swap() }) {
              Icon(Icons.Outlined.SwapHoriz, contentDescription = "단위 맞바꾸기")
            }
            Text(
              text = "${uiState.value} ${uiState.fromUnit}",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          Spacer(modifier = Modifier.height(8.dp))

          // To unit dropdown
          Text(
            text = "결과 단위 (to)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(modifier = Modifier.height(4.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            units.take(8).forEach { (code, display) ->
              val isSelected = uiState.toUnit == code
              Box(
                modifier =
                  Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                      if (isSelected) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { viewModel.setToUnit(code) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
              ) {
                Text(
                  text = display,
                  color =
                    if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                  fontSize = 12.sp,
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(16.dp))

          // Result
          Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
              .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
              .padding(16.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = "${uiState.result} ${uiState.toUnit}",
              style = MaterialTheme.typography.headlineMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary,
              textAlign = TextAlign.Center,
            )
          }

          Spacer(modifier = Modifier.height(12.dp))

          Button(
            onClick = { viewModel.addToHistory() },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("기록에 추가")
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // ── History ───────────────────────────────────────────────────────────
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "변환 기록",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        if (uiState.history.isNotEmpty()) {
          IconButton(onClick = { viewModel.clearHistory() }) {
            Icon(Icons.Outlined.Delete, contentDescription = "기록 지우기")
          }
        }
      }
      Spacer(modifier = Modifier.height(4.dp))

      if (uiState.history.isEmpty()) {
        Text(
          text = "아직 기록이 없습니다.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
          items(uiState.history) { entry ->
            val formatted = UnitDefs.format(entry.result)
            Row(
              modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "${UnitDefs.format(entry.value)} ${entry.from} = ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = "$formatted ${entry.to}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
              )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
          }
        }
      }
    }
  }
}
