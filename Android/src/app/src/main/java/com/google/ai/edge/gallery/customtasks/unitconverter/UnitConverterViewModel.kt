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

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** A single conversion entry in the history. */
data class ConversionEntry(
  val value: Double,
  val from: String,
  val to: String,
  val result: Double,
  val timestamp: Long = System.currentTimeMillis(),
)

/** UI state for the unit converter screen. */
data class UnitConverterUiState(
  val category: String = "length", // length | weight | temperature | data
  val value: String = "1",
  val fromUnit: String = "m",
  val toUnit: String = "km",
  val result: String = "0.001",
  val history: List<ConversionEntry> = emptyList(),
)

/** Conversion category descriptors. */
data class UnitCategory(
  val id: String,
  val label: String,
  val units: List<Pair<String, String>>, // (code, displayName)
)

/** Available categories and their units. */
object UnitDefs {
  val categories: List<UnitCategory> =
    listOf(
      UnitCategory(
        "length",
        "길이",
        listOf(
          "mm" to "mm", "cm" to "cm", "m" to "m", "km" to "km",
          "in" to "in", "ft" to "ft", "yd" to "yd", "mi" to "mi",
        ),
      ),
      UnitCategory(
        "weight",
        "무게",
        listOf(
          "mg" to "mg", "g" to "g", "kg" to "kg", "t" to "t",
          "oz" to "oz", "lb" to "lb",
        ),
      ),
      UnitCategory(
        "temperature",
        "온도",
        listOf(
          "c" to "°C", "f" to "°F", "k" to "K",
        ),
      ),
      UnitCategory(
        "data",
        "데이터 크기",
        listOf(
          "b" to "B", "kb" to "KB", "mb" to "MB", "gb" to "GB", "tb" to "TB",
        ),
      ),
    )

  /** Factor tables mapping each unit to a base multiplier. */
  private val length: Map<String, Double> =
    mapOf(
      "mm" to 0.001, "cm" to 0.01, "m" to 1.0, "km" to 1000.0,
      "in" to 0.0254, "ft" to 0.3048, "yd" to 0.9144, "mi" to 1609.344,
    )
  private val weight: Map<String, Double> =
    mapOf(
      "mg" to 0.001, "g" to 1.0, "kg" to 1000.0, "t" to 1_000_000.0,
      "oz" to 28.349523125, "lb" to 453.59237,
    )
  private val data: Map<String, Double> =
    mapOf(
      "b" to 1.0, "kb" to 1024.0, "mb" to 1024.0 * 1024, "gb" to 1024.0 * 1024 * 1024,
      "tb" to 1024.0 * 1024 * 1024 * 1024,
    )

  fun convert(value: Double, from: String, to: String, category: String): Double? {
    // Temperature special handling
    if (category == "temperature") {
      val celsius =
        when (from) {
          "c" -> value
          "f" -> (value - 32) * 5 / 9
          "k" -> value - 273.15
          else -> return null
        }
      return when (to) {
        "c" -> celsius
        "f" -> celsius * 9 / 5 + 32
        "k" -> celsius + 273.15
        else -> null
      }
    }
    val table =
      when (category) {
        "length" -> length
        "weight" -> weight
        "data" -> data
        else -> return null
      }
    val fromFactor = table[from] ?: return null
    val toFactor = table[to] ?: return null
    return value * fromFactor / toFactor
  }

  fun format(value: Double): String {
    if (!value.isFinite()) return "Invalid"
    if (value == 0.0) return "0"
    val abs = Math.abs(value)
    return when {
      abs >= 1e9 || abs < 1e-6 -> String.format("%.6e", value)
      value == Math.floor(value) && !value.isInfinite() -> value.toLong().toString()
      else -> {
        // Round to 6 significant-ish digits, strip trailing zeros
        val rounded = Math.round(value * 1e6) / 1e6
        rounded.toString()
      }
    }
  }
}

/** ViewModel for the unit converter. Manages category, value, units, result, and history. */
@HiltViewModel
class UnitConverterViewModel @Inject constructor() : ViewModel() {
  private val _uiState = MutableStateFlow(UnitConverterUiState())
  val uiState = _uiState.asStateFlow()

  private val unitDefs = UnitDefs

  fun setCategory(category: String) {
    // Reset to the first unit pair of the new category
    val cat = unitDefs.categories.firstOrNull { it.id == category } ?: return
    _uiState.update {
      it.copy(
        category = category,
        fromUnit = cat.units.first().first,
        toUnit = cat.units[1].first,
      )
    }
    convert()
  }

  fun setValue(value: String) {
    _uiState.update { it.copy(value = value) }
    convert()
  }

  fun setFromUnit(unit: String) {
    _uiState.update { it.copy(fromUnit = unit) }
    convert()
  }

  fun setToUnit(unit: String) {
    _uiState.update { it.copy(toUnit = unit) }
    convert()
  }

  /** Swap the from/to units and values. */
  fun swap() {
    _uiState.update {
      it.copy(
        fromUnit = it.toUnit,
        toUnit = it.fromUnit,
        value = it.result.ifBlank { it.value },
      )
    }
    convert()
  }

  fun convert() {
    val s = _uiState.value
    val value = s.value.toDoubleOrNull() ?: run {
      _uiState.update { it.copy(result = "숫자를 입력하세요") }
      return
    }
    val result = unitDefs.convert(value, s.fromUnit, s.toUnit, s.category)
    if (result == null) {
      _uiState.update { it.copy(result = "변환 불가") }
      return
    }
    val formatted = unitDefs.format(result)
    _uiState.update { it.copy(result = formatted) }
  }

  /** Commit the current conversion to history. */
  fun addToHistory() {
    val s = _uiState.value
    val value = s.value.toDoubleOrNull() ?: return
    val result = unitDefs.convert(value, s.fromUnit, s.toUnit, s.category) ?: return
    val entry =
      ConversionEntry(
        value = value,
        from = s.fromUnit,
        to = s.toUnit,
        result = result,
      )
    _uiState.update {
      it.copy(history = listOf(entry) + it.history.take(19)) // Keep last 20
    }
  }

  fun clearHistory() {
    _uiState.update { it.copy(history = emptyList()) }
  }
}
