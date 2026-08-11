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

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.runtime.Composable
import com.google.ai.edge.gallery.customtasks.common.CustomTask
import com.google.ai.edge.gallery.customtasks.common.CustomTaskData
import com.google.ai.edge.gallery.data.Category
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import com.google.ai.edge.litertlm.Contents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A native unit converter custom task. Convert length, weight, temperature, and data-size
 * units with instant results and a conversion history.
 *
 * This task demonstrates a model-free custom task — it doesn't require any ML model.
 * It uses a dummy model entry to satisfy the CustomTask interface requirements,
 * exactly like the Dice Roller task.
 */
class UnitConverterTask @javax.inject.Inject constructor() : CustomTask {
  override val task: Task =
    Task(
      id = "unit_converter",
      label = "Unit Converter",
      category = Category.EXPERIMENTAL,
      icon = Icons.Outlined.Calculate,
      description =
        "Convert between common units of length, weight, temperature, and data size. " +
          "No model required — instant, offline, pure native.",
      models =
        mutableListOf(
          Model(
            name = "Unit Converter (No Model)",
            info = "This task doesn't require a model. Just convert!",
            bestForTaskIds = listOf("unit_converter"),
          )
        ),
    )

  override fun initializeModelFn(
    context: Context,
    coroutineScope: CoroutineScope,
    model: Model,
    systemInstruction: Contents?,
    onDone: (String) -> Unit,
  ) {
    coroutineScope.launch(Dispatchers.IO) {
      // No model to initialize — just simulate a brief loading delay for UX.
      model.instance = UnitConverterModelInstance()
      delay(300)
      onDone("")
    }
  }

  override fun cleanUpModelFn(
    context: Context,
    coroutineScope: CoroutineScope,
    model: Model,
    onDone: () -> Unit,
  ) {
    model.instance = null
    onDone()
  }

  @Composable
  override fun MainScreen(data: Any) {
    val myData = data as CustomTaskData
    val modelManagerViewModel: ModelManagerViewModel = myData.modelManagerViewModel

    UnitConverterScreen(modelManagerViewModel = modelManagerViewModel)
  }
}

/** A placeholder model instance for the unit converter (no actual model needed). */
data class UnitConverterModelInstance(val placeholder: String = "ready")
