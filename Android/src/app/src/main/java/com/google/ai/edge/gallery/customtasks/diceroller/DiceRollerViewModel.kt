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

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** A single roll entry in the history. */
data class RollEntry(
  val diceCount: Int,
  val results: List<Int>,
  val total: Int,
  val timestamp: Long = System.currentTimeMillis(),
)

/** UI state for the dice roller screen. */
data class DiceRollerUiState(
  val diceCount: Int = 2,
  val results: List<Int> = listOf(2, 5),
  val isRolling: Boolean = false,
  val history: List<RollEntry> = emptyList(),
  val total: Int = 7,
)

/** ViewModel for the dice roller. Manages dice state, rolling animation, and history. */
@HiltViewModel
class DiceRollerViewModel @Inject constructor() : ViewModel() {
  private val _uiState = MutableStateFlow(DiceRollerUiState())
  val uiState = _uiState.asStateFlow()

  fun setDiceCount(count: Int) {
    if (_uiState.value.isRolling) return
    _uiState.update { it.copy(diceCount = count.coerceIn(1, 6)) }
  }

  fun roll() {
    if (_uiState.value.isRolling) return

    val count = _uiState.value.diceCount

    // Start rolling animation
    _uiState.update { it.copy(isRolling = true) }

    // Simulate rapid random changes during animation
    val animationFrames = 10
    var frame = 0

    val animate = object : Runnable {
      override fun run() {
        if (frame < animationFrames) {
          val randomResults = List(count) { (1..6).random() }
          _uiState.update {
            it.copy(
              results = randomResults,
              total = randomResults.sum(),
            )
          }
          frame++
          // Schedule next frame with increasing delay for deceleration effect
          val delay = (50 + frame * 30).toLong()
          android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this, delay)
        } else {
          // Final result
          val finalResults = List(count) { (1..6).random() }
          val entry = RollEntry(
            diceCount = count,
            results = finalResults,
            total = finalResults.sum(),
          )
          _uiState.update {
            it.copy(
              results = finalResults,
              total = finalResults.sum(),
              isRolling = false,
              history = listOf(entry) + it.history.take(19), // Keep last 20 rolls
            )
          }
        }
      }
    }

    android.os.Handler(android.os.Looper.getMainLooper()).post(animate)
  }

  fun clearHistory() {
    _uiState.update { it.copy(history = emptyList()) }
  }
}
