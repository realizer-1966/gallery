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

import android.content.Context
import androidx.compose.runtime.Composable
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.customtasks.common.CustomTask
import com.google.ai.edge.gallery.customtasks.common.CustomTaskData
import com.google.ai.edge.gallery.data.Accelerator
import com.google.ai.edge.gallery.data.Category
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.RuntimeType
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.ui.llmchat.LlmChatModelHelper
import com.google.ai.edge.litertlm.Contents
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

/**
 * Voice Chat — talk to an on-device LiteRT LLM from a browser over WebRTC.
 *
 * Pipeline (all on the phone):
 *   1. The browser sends its microphone over WebRTC.
 *   2. Vosk (on-device STT) transcribes the incoming audio.
 *   3. The text goes to the LiteRT LM engine (downloaded via the gallery model manager).
 *   4. Android TTS renders the answer and streams it back to the browser as PCM
 *      over an encrypted WebRTC data channel.
 *
 * The Gemini 3 1B q4 model (~550 MB) is downloaded from HuggingFace the first time the
 * user taps download in the model screen, exactly like any other gallery model.
 */
class VoiceChatTask
@Inject
constructor(@ApplicationContext private val context: Context) : CustomTask {

  override val task: Task by lazy {
    Task(
      id = TASK_ID,
      label = context.getString(R.string.task_label_voice_chat),
      category = Category.LLM,
      iconVectorResourceId = R.drawable.live,
      description = context.getString(R.string.task_desc_voice_chat),
      shortDescription = context.getString(R.string.task_short_desc_voice_chat),
      sourceCodeUrl =
        "https://github.com/google-ai-edge/gallery/blob/main/Android/src/app/src/main/java/" +
          "com/google/ai/edge/gallery/customtasks/voicechat/",
      experimental = true,
      newFeature = true,
      models =
        mutableListOf(
          Model(
            name = "Gemma3-1B-IT q4",
            displayName = "Gemma 3 1B (voice)",
            info =
              "On-device Gemma 3 1B (4-bit) used for voice chat. First download the model " +
                "here, then set up the WebRTC connection in the task screen.",
            bestForTaskIds = listOf(TASK_ID),
            url =
              "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/" +
                "Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task",
            sizeInBytes = 554_661_246L,
            downloadFileName = "Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task",
            version = "20250514",
            isLlm = true,
            runtimeType = RuntimeType.LITERT_LM,
            llmMaxToken = 1024,
            accelerators = listOf(Accelerator.CPU, Accelerator.GPU),
          ),
        ),
      defaultSystemPrompt =
        "You are a voice assistant. Answer the user's question concisely in one to three " +
          "short sentences, as if speaking aloud. Do not use markdown or bullet lists.",
    )
  }

  override fun initializeModelFn(
    context: Context,
    coroutineScope: CoroutineScope,
    model: Model,
    systemInstruction: Contents?,
    onDone: (String) -> Unit,
  ) {
    LlmChatModelHelper.initialize(
      context = context,
      model = model,
      taskId = task.id,
      supportImage = false,
      supportAudio = false,
      onDone = onDone,
      systemInstruction = systemInstruction,
    )
  }

  override fun cleanUpModelFn(
    context: Context,
    coroutineScope: CoroutineScope,
    model: Model,
    onDone: () -> Unit,
  ) {
    LlmChatModelHelper.cleanUp(model = model, onDone = onDone)
  }

  @Composable
  override fun MainScreen(data: Any) {
    val myData = data as CustomTaskData
    VoiceChatScreen(data = myData)
  }

  private companion object {
    const val TASK_ID = "llm_voice_chat"
  }
}

@Module
@InstallIn(SingletonComponent::class)
internal object VoiceChatTaskModule {
  @Provides
  @IntoSet
  fun provideTask(@ApplicationContext context: Context): CustomTask = VoiceChatTask(context)
}
