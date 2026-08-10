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

import java.io.File
import java.io.RandomAccessFile

/**
 * Wire protocol between the phone (Gallery Voice Chat task) and the browser peer.
 *
 * JSON control messages + raw 16-bit little-endian PCM are exchanged over the WebRTC data
 * channel ("tts"). The browser plays the PCM with the Web Audio API.
 *
 * Phone -> Browser:
 *   {"type":"status","text":...}        phone status line
 *   {"type":"stt","text":...}           what the phone heard (user speech)
 *   {"type":"text","text":...}          assistant answer text
 *   {"type":"audio_start","sampleRate":N,"channels":N}  followed by PCM chunks
 *   <binary PCM 16-bit LE>
 *   {"type":"audio_end"}
 *
 * Browser -> Phone:
 *   {"type":"clear"}                    reset the LLM conversation
 */
object VoiceChatProtocol {
  const val TAG = "AGVoiceChat"

  fun statusMsg(text: String): String = """{"type":"status","text":${json(text)}}"""
  fun sttMsg(text: String): String = """{"type":"stt","text":${json(text)}}"""
  fun textMsg(text: String): String = """{"type":"text","text":${json(text)}}"""
  fun audioStartMsg(sampleRate: Int, channels: Int): String =
    """{"type":"audio_start","sampleRate":$sampleRate,"channels":$channels}"""
  const val AUDIO_END_MSG = """{"type":"audio_end"}"""
  const val CLEAR_MSG = """{"type":"clear"}"""

  private fun json(s: String): String {
    val sb = StringBuilder("\"")
    for (c in s) {
      when (c) {
        '"' -> sb.append("\\\"")
        '\\' -> sb.append("\\\\")
        '\n' -> sb.append("\\n")
        '\r' -> sb.append("\\r")
        '\t' -> sb.append("\\t")
        else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
      }
    }
    return sb.append("\"").toString()
  }
}

/** Parsed WAV header (16-bit PCM, produced by Android TTS). */
data class WavInfo(
  val sampleRate: Int,
  val channels: Int,
  val bitsPerSample: Int,
  val dataOffset: Long,
  val dataSize: Long,
)

/**
 * Minimal RIFF/WAV header parser. Android's TextToSpeech#synthesizeToFile emits standard
 * WAV files (fmt + data chunks); some encoders add extra chunks (LIST etc.) so we walk
 * the chunk list instead of assuming a 44-byte header.
 */
fun parseWav(file: File): WavInfo {
  RandomAccessFile(file, "r").use { raf ->
    require(raf.readInt() == 0x52494646) { "Not a RIFF file: ${file.name}" } // "RIFF"
    raf.readInt() // chunk size
    require(raf.readInt() == 0x57415645) { "Not a WAVE file: ${file.name}" } // "WAVE"

    while (raf.filePointer < raf.length()) {
      val chunkId = raf.readInt()
      val chunkSize = readUInt32LE(raf)
      when (chunkId) {
        0x666D7420 -> { // "fmt "
          val audioFormat = raf.readShort().toInt() and 0xFFFF
          require(audioFormat == 1 || audioFormat == 0xFFFE) { "Unsupported WAV format $audioFormat" }
          val channels = raf.readShort().toInt() and 0xFFFF
          val sampleRate = raf.readInt()
          raf.skipBytes(6) // byte rate + block align
          val bitsPerSample = raf.readShort().toInt() and 0xFFFF
          val fmtEnd = raf.filePointer
          if (chunkSize > 16) raf.seek(fmtEnd + (chunkSize - 16))
        }

        0x64617461 -> { // "data"
          val dataOffset = raf.filePointer
          return WavInfo(
            sampleRate = sampleRate,
            channels = channels,
            bitsPerSample = bitsPerSample,
            dataOffset = dataOffset,
            dataSize = chunkSize,
          )
        }

        else -> raf.seek(raf.filePointer + chunkSize)
      }
    }
  }
  error("No data chunk in ${file.name}")
}

private fun readUInt32LE(raf: RandomAccessFile): Long {
  val b0 = raf.read() and 0xFF
  val b1 = raf.read() and 0xFF
  val b2 = raf.read() and 0xFF
  val b3 = raf.read() and 0xFF
  return (b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)).toLong() and 0xFFFFFFFFL
}

/** Splits raw PCM bytes into fixed-size chunks for data-channel delivery. */
fun chunkPcm(pcm: ByteArray, chunkFrames: Int, bytesPerFrame: Int): List<ByteArray> {
  val chunkBytes = chunkFrames * bytesPerFrame
  val chunks = mutableListOf<ByteArray>()
  var offset = 0
  while (offset < pcm.size) {
    val size = minOf(chunkBytes, pcm.size - offset)
    chunks.add(pcm.copyOfRange(offset, offset + size))
    offset += size
  }
  return chunks
}
