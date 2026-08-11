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

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.runtime.Composable
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.customtasks.common.CustomTask
import com.google.ai.edge.gallery.customtasks.common.CustomTaskData
import com.google.ai.edge.gallery.data.Category
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.ui.llmchat.LlmChatModelHelper
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.tool
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * The default system prompt for the Cooking Companion task.
 *
 * The model is instructed to use the `createRecipe` tool to build a structured recipe from the
 * user's available ingredients, and to always report the resulting recipe back to the user.
 */
private const val SYSTEM_PROMPT =
  """
  You are Cooking Companion, a helpful offline cooking assistant.

  Your job is to help the user turn ingredients they have into a delicious recipe.

  When the user tells you the ingredients they have (and optionally a dish type or cuisine),
  you MUST call the `createRecipe` tool to build a structured recipe, passing all relevant info.

  After the tool returns, present the full recipe to the user in a clear, friendly format using
  Markdown. Include:
  - Dish name
  - Ingredients with quantities
  - Step-by-step instructions
  - Any helpful tips (optional)

  Keep your responses encouraging and practical. If the user wants to scale the recipe for a
  different number of servings, use the `scaleRecipe` tool.
  """

/**
 * A custom task that demonstrates an LLM-powered cooking assistant.
 *
 * It uses the on-device LiteRT LLM to turn the user's ingredients into a structured recipe.
 * The model is loaded through [LlmChatModelHelper] and communicates with the UI through a
 * channel of [CookingCommand]s produced by [CookingTools].
 */
class CookingCompanionTask @Inject constructor(@ApplicationContext private val context: Context) :
  CustomTask {
  private val _updateChannel = Channel<CookingCommand>(Channel.BUFFERED)
  private val commandFlow = _updateChannel.receiveAsFlow()
  private val tools =
    listOf(
      tool(
        CookingTools(
          onFunctionCalled = { command -> _updateChannel.trySend(command) }
        )
      )
    )

  override val task: Task =
    Task(
      id = TASK_ID,
      label = context.getString(R.string.task_label_cooking_companion),
      description = context.getString(R.string.task_desc_cooking_companion),
      shortDescription = context.getString(R.string.task_short_desc_cooking_companion),
      docUrl = "https://github.com/google-ai-edge/LiteRT-LM/blob/main/kotlin/README.md",
      sourceCodeUrl =
        "https://github.com/realizer-1966/gallery/blob/main/Android/src/app/src/main/java/" +
          "com/google/ai/edge/gallery/customtasks/cookingcompanion",
      category = Category.LLM,
      icon = Icons.Outlined.Restaurant,
      models = mutableListOf(),
      // Connect allowlist models to this task. The model is chosen from model_allowlist.json.
      modelNames = listOf("Gemma3-1B-IT q4", "Gemma-3n-E2B-it-int4", "Gemma-3n-E4B-it-int4"),
      experimental = true,
      defaultSystemPrompt = SYSTEM_PROMPT,
    )

  override fun initializeModelFn(
    context: Context,
    coroutineScope: CoroutineScope,
    model: Model,
    systemInstruction: Contents?,
    onDone: (String) -> Unit,
  ) {
    clearQueue()
    LlmChatModelHelper.initialize(
      context = context,
      model = model,
      taskId = task.id,
      supportImage = false,
      supportAudio = false,
      onDone = onDone,
      systemInstruction = Contents.of(getCookingSystemPrompt()),
      tools = tools,
      enableConversationConstrainedDecoding = false,
    )
  }

  override fun cleanUpModelFn(
    context: Context,
    coroutineScope: CoroutineScope,
    model: Model,
    onDone: () -> Unit,
  ) {
    clearQueue()
    LlmChatModelHelper.cleanUp(model = model, onDone = onDone)
  }

  @Composable
  override fun MainScreen(data: Any) {
    val customTaskData = data as CustomTaskData
    CookingCompanionScreen(
      task = task,
      modelManagerViewModel = customTaskData.modelManagerViewModel,
      tools = tools,
      bottomPadding = customTaskData.bottomPadding,
      commandFlow = commandFlow,
      setAppBarControlsDisabled = customTaskData.setAppBarControlsDisabled,
      setTopBarVisible = customTaskData.setTopBarVisible,
    )
  }

  private fun clearQueue() {
    while (_updateChannel.tryReceive().isSuccess) {}
  }
}

/** The task id for the Cooking Companion task. */
const val TASK_ID = "llm_cooking_companion"

/** Returns the system prompt used to initialize the Cooking Companion model. */
fun getCookingSystemPrompt(): String = SYSTEM_PROMPT
