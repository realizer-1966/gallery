/*
 * Copyright 2025 Google LLC
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

package com.google.ai.edge.gallery.customtasks.diceroller

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel

/** Unicode dice faces for values 1-6. */
private val DICE_FACES = mapOf(
  1 to "⚀",
  2 to "⚁",
  3 to "⚂",
  4 to "⚃",
  5 to "⚄",
  6 to "⚅",
)

/** Colors for each dice face value. */
private val DICE_COLORS = listOf(
  Color(0xFFE53935), // 1 - Red
  Color(0xFFFB8C00), // 2 - Orange
  Color(0xFFFDD835), // 3 - Yellow
  Color(0xFF43A047), // 4 - Green
  Color(0xFF1E88E5), // 5 - Blue
  Color(0xFF8E24AA), // 6 - Purple
)

/** The main screen of the dice roller custom task. */
@Composable
fun DiceRollerScreen(
  modelManagerViewModel: ModelManagerViewModel,
  viewModel: DiceRollerViewModel = hiltViewModel(),
) {
  val modelManagerUiState by modelManagerViewModel.uiState.collectAsState()
  val model = modelManagerUiState.selectedModel
  val uiState by viewModel.uiState.collectAsState()

  // Scale animation for dice during rolling
  val scale by animateFloatAsState(
    targetValue = if (uiState.isRolling) 1.15f else 1.0f,
    animationSpec = tween(durationMillis = 100),
    label = "dice_scale",
  )

  if (modelManagerUiState.isModelInitialized(model = model)) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      // ── Dice count selector ───────────────────────────────────────────────
      Text(
        text = "Number of Dice",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(8.dp))

      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        (1..6).forEach { count ->
          val isSelected = uiState.diceCount == count
          Box(
            modifier = Modifier
              .size(40.dp)
              .clip(CircleShape)
              .background(
                if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
              )
              .clickable(enabled = !uiState.isRolling) { viewModel.setDiceCount(count) },
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = "$count",
              color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                      else MaterialTheme.colorScheme.onSurfaceVariant,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(24.dp))

      // ── Dice display ──────────────────────────────────────────────────────
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
      ) {
        Column(
          modifier = Modifier.padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          // Dice row
          Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.scale(scale),
          ) {
            uiState.results.forEachIndexed { index, value ->
              DiceFace(value = value, index = index)
            }
          }

          Spacer(modifier = Modifier.height(16.dp))

          // Total
          Text(
            text = "Total: ${uiState.total}",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // ── Roll button ───────────────────────────────────────────────────────
      Button(
        onClick = { viewModel.roll() },
        enabled = !uiState.isRolling,
        modifier = Modifier
          .fillMaxWidth()
          .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.primary,
        ),
      ) {
        Icon(
          imageVector = Icons.Outlined.Casino,
          contentDescription = null,
          modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = if (uiState.isRolling) "Rolling..." else "Roll the Dice!",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
      }

      Spacer(modifier = Modifier.height(24.dp))

      // ── History ───────────────────────────────────────────────────────────
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Roll History",
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (uiState.history.isNotEmpty()) {
          IconButton(onClick = { viewModel.clearHistory() }) {
            Icon(
              imageVector = Icons.Outlined.Delete,
              contentDescription = "Clear history",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

      if (uiState.history.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = "No rolls yet. Tap the button to roll! 🎲",
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
          )
        }
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(uiState.history) { entry ->
            HistoryEntry(entry = entry)
          }
        }
      }
    }
  }
}

/** A single dice face with color and value. */
@Composable
private fun DiceFace(value: Int, index: Int) {
  val face = DICE_FACES[value] ?: "⚀"
  val color = DICE_COLORS.getOrElse(value - 1) { Color.Gray }

  Box(
    modifier = Modifier
      .size(64.dp)
      .clip(RoundedCornerShape(12.dp))
      .background(color.copy(alpha = 0.15f))
      .border(2.dp, color.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = face,
      fontSize = 40.sp,
      color = color,
    )
  }
}

/** A single history entry showing dice results and total. */
@Composable
private fun HistoryEntry(entry: RollEntry) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
    ),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // Dice results
      Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        entry.results.forEach { value ->
          Text(
            text = DICE_FACES[value] ?: "$value",
            fontSize = 24.sp,
            color = DICE_COLORS.getOrElse(value - 1) { Color.Gray },
          )
        }
      }

      // Total
      Text(
        text = "= ${entry.total}",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
      )
    }
  }
}
