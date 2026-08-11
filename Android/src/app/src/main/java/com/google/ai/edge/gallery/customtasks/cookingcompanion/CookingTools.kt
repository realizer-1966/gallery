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
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet

private const val TAG = "AGCookingTools"

/** A command produced by [CookingTools] and consumed by the Cooking Companion UI. */
data class CookingCommand(
  // "create_recipe" or "scale_recipe"
  val action: String,
  // The structured recipe payload as a JSON string, or a note for scale actions.
  val payload: String,
  val ts: Long = System.currentTimeMillis(),
)

/**
 * Tools exposed to the Cooking Companion LLM.
 *
 * Reference: https://github.com/google-ai-edge/LiteRT-LM/blob/main/kotlin/README.md#6-defining-and-using-tools
 */
class CookingTools(val onFunctionCalled: (command: CookingCommand) -> Unit) : ToolSet {

  /**
   * Records the structured recipe that the model just composed. Called by the model after it
   * decides on a dish.
   */
  @Tool(description = "Register a structured recipe that was created for the user.")
  fun createRecipe(
    @ToolParam(description = "The name of the dish.") dishName: String,
    @ToolParam(description = "The main ingredients, comma separated.") ingredients: List<String>,
    @ToolParam(description = "Number of servings the recipe yields.") servings: Int,
  ): Map<String, Any> {
    Log.d(TAG, "createRecipe. dish=$dishName, servings=$servings, ingredients=$ingredients")

    val payload =
      """{"dishName": "$dishName", "ingredients": [${ingredients.joinToString(", ") { "\"$it\"" }}], "servings": $servings}"""

    onFunctionCalled(CookingCommand(action = "create_recipe", payload = payload))

    // Return a confirmation to the model so it can present the recipe to the user.
    return mapOf("result" to "success", "dishName" to dishName, "servings" to servings)
  }

  /**
   * Scales a recipe's ingredients to a target number of servings. Called when the user wants more
   * or fewer portions.
   */
  @Tool(description = "Scale an existing recipe to a different number of servings.")
  fun scaleRecipe(
    @ToolParam(description = "The name of the dish being scaled.") dishName: String,
    @ToolParam(description = "The target number of servings.") targetServings: Int,
  ): Map<String, Any> {
    Log.d(TAG, "scaleRecipe. dish=$dishName, targetServings=$targetServings")

    onFunctionCalled(
      CookingCommand(action = "scale_recipe", payload = """{"dishName": "$dishName", "servings": $targetServings}""")
    )

    return mapOf("result" to "success", "dishName" to dishName, "servings" to targetServings)
  }
}
