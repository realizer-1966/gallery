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

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Casino
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
 * A fun dice roller custom task. Roll one or more dice with animation and history tracking.
 *
 * This task demonstrates a model-free custom task — it doesn't require any ML model.
 * It uses a dummy model entry to satisfy the CustomTask interface requirements.
 */
class DiceRollerTask @javax.inject.Inject constructor() : CustomTask {
  override val task: Task =
    Task(
      id = "dice_roller",
      label = "Dice Roller",
      category = Category.EXPERIMENTAL,
      icon = Icons.Outlined.Casino,
      description =
        "Roll virtual dice with fun animations! Choose 1-6 dice, roll them, and track your roll history. No model required — just pure fun.",
      models =
        mutableListOf(
          Model(
            name = "Dice Roller (No Model)",
            info = "This task doesn't require a model. Just tap to roll!",
            bestForTaskIds = listOf("dice_roller"),
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
      model.instance = DiceRollerModelInstance()
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

    DiceRollerScreen(modelManagerViewModel = modelManagerViewModel)
  }
}

/** A placeholder model instance for the dice roller (no actual model needed). */
data class DiceRollerModelInstance(val placeholder: String = "ready")
