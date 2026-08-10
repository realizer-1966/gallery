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
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.DefaultClientWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.AudioTrack
import org.webrtc.AudioTrackSink
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.AudioTrack

/**
 * WebRTC phone-side controller for the Voice Chat task.
 *
 *  - Connects to the signaling server (WebSocket) and joins a room as the "phone" peer.
 *  - Answers the browser's SDP offer (the browser sends its microphone; the phone never
 *    sends an audio track — the TTS answer is streamed over the data channel instead).
 *  - Feeds the browser's microphone PCM to [onMicPcm] for on-device STT.
 *  - Streams TTS PCM to the browser over the "tts" data channel.
 */
class VoiceChatWebRtc(
  private val context: Context,
  private val onStatus: (String) -> Unit,
  private val onRtcConnected: () -> Unit,
  private val onRtcDisconnected: () -> Unit,
  private val onMicPcm: (pcm: ByteArray, sampleRate: Int, channels: Int) -> Unit,
  private val onClearRequest: () -> Unit,
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val mainHandler = Handler(Looper.getMainLooper())
  private val connected = AtomicBoolean(false)

  private var client: HttpClient? = null
  private var wsJob: Job? = null
  private var wsSession: DefaultClientWebSocketSession? = null
  private var pc: PeerConnection? = null
  private var dataChannel: DataChannel? = null
  private var remoteAudioTrack: org.webrtc.AudioTrack? = null
  private var sttStarted = false
  private var pendingSdp: JSONObject? = null
  private var pendingIce = mutableListOf<JSONObject>()

  init {
    PeerConnectionFactory.initialize(
      PeerConnectionFactory.InitializationOptions.builder(context)
        .setEnableInternalTracer(false)
        .createInitializationOptions(),
    )
  }

  // -------------------------------------------------------------------------
  // Public API
  // -------------------------------------------------------------------------

  fun connect(serverUrl: String, room: String) {
    disconnect()
    Log.i(TAG, "Connecting to $serverUrl room=$room")
    onStatus("Connecting to signaling…")
    val url = if (serverUrl.startsWith("ws://") || serverUrl.startsWith("wss://")) serverUrl
    else "ws://$serverUrl"
    client =
      HttpClient(Android) {
        install(WebSockets) {
          pingIntervalMillis = 20_000
        }
      }
    wsJob =
      scope.launch {
        try {
          client!!.webSocket(urlString = "$url/ws") {
            wsSession = this
            send(Frame.Text("""{"type":"join","room":${VoiceChatProtocol.json(room)},"role":"phone"}"""))
            onStatus("Joined room '$room'. Waiting for the browser…")
            for (frame in incoming) {
              when (frame) {
                is Frame.Text -> handleSignaling(frame.readText())
                is Frame.Close -> break
                else -> {}
              }
            }
          }
        } catch (e: Exception) {
          Log.e(TAG, "Signaling connection failed", e)
          onStatus("Signaling error: ${e.message}")
        } finally {
          wsSession = null
          mainHandler.post {
            if (connected.getAndSet(false)) onRtcDisconnected()
          }
        }
      }
  }

  fun disconnect() {
    connected.set(false)
    wsJob?.cancel()
    wsJob = null
    closePeerConnection()
    client?.close()
    client = null
  }

  fun release() {
    disconnect()
    scope.cancel()
  }

  fun isRtcConnected(): Boolean = connected.get()

  // --- Data channel senders (phone -> browser) ------------------------------

  fun sendControl(json: String) {
    dataChannel?.send(DataChannel.Buffer(ByteBuffer.wrap(json.toByteArray()), false))
  }

  fun sendPcmStart(sampleRate: Int, channels: Int) {
    sendControl(VoiceChatProtocol.audioStartMsg(sampleRate, channels))
  }

  fun sendPcm(chunk: ByteArray) {
    dataChannel?.send(DataChannel.Buffer(ByteBuffer.wrap(chunk), true))
  }

  fun sendPcmEnd() {
    sendControl(VoiceChatProtocol.AUDIO_END_MSG)
  }

  // -------------------------------------------------------------------------
  // Signaling
  // -------------------------------------------------------------------------

  private fun handleSignaling(json: String) {
    val msg = JSONObject(json)
    when (msg.optString("type")) {
      "ready" -> {
        Log.i(TAG, "Both peers are in the room. Waiting for offer…")
        onStatus("Browser connected. Starting WebRTC…")
        ensurePeerConnection()
      }

      "sdp" -> {
        val sdp = msg.getJSONObject("sdp")
        if (sdp.optString("type") == "offer") {
          ensurePeerConnection()
          handleOffer(sdp)
        }
      }

      "ice" -> {
        val candidate = msg.getJSONObject("candidate")
        if (pc != null) addIceCandidate(candidate) else pendingIce.add(candidate)
      }

      "peer-left" -> {
        Log.i(TAG, "Browser left the room")
        onStatus("Browser left the room.")
        connected.set(false)
        onRtcDisconnected()
        closePeerConnection()
      }

      "clear" -> onClearRequest()
    }
  }

  private fun handleOffer(sdp: JSONObject) {
    if (pc == null) {
      pendingSdp = sdp
      return
    }
    val description =
      SessionDescription(SessionDescription.Type.OFFER, sdp.optString("sdp", ""))
    pc!!.setRemoteDescription(
      object : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription?) {}
        override fun onCreateFailure(error: String?) {}
        override fun onSetFailure(error: String?) {
          Log.e(TAG, "setRemoteDescription failed: $error")
          onStatus("SDP error: $error")
        }

        override fun onSetSuccess() {
          Log.i(TAG, "Remote description applied, creating answer…")
          pc!!.createAnswer(
            object : SdpObserver {
              override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc == null) return
                pc!!.setLocalDescription(
                  object : SdpObserver {
                    override fun onCreateSuccess(desc: SessionDescription?) {}
                    override fun onCreateFailure(error: String?) {}
                    override fun onSetFailure(error: String?) {
                      Log.e(TAG, "setLocalDescription failed: $error")
                    }

                    override fun onSetSuccess() {
                      Log.i(TAG, "Answer set locally, sending to browser")
                      sendToSignaling(
                        JSONObject()
                          .put("type", "sdp")
                          .put("sdp", JSONObject().put("type", "answer").put("sdp", desc.sdp)),
                      )
                    }
                  },
                  desc,
                )
              }

              override fun onCreateFailure(error: String?) {
                Log.e(TAG, "createAnswer failed: $error")
                onStatus("Answer failed: $error")
              }

              override fun onSetFailure(error: String?) {}
              override fun onSetSuccess() {}
            },
            answerConstraints(),
          )
        }
      },
      description,
    )
  }

  private fun addIceCandidate(candidate: JSONObject) {
    pc?.addIceCandidate(
      IceCandidate(
        candidate.optString("candidate", ""),
        candidate.optString("sdpMid", ""),
        candidate.optInt("sdpMLineIndex", 0),
      ),
    )
  }

  private fun sendToSignaling(json: JSONObject) {
    scope.launch {
      try {
        wsSession?.send(Frame.Text(json.toString()))
      } catch (e: Exception) {
        Log.w(TAG, "Failed to send signaling message", e)
      }
    }
  }

  // -------------------------------------------------------------------------
  // Peer connection
  // -------------------------------------------------------------------------

  private fun ensurePeerConnection() {
    if (pc != null) return
    Log.i(TAG, "Creating PeerConnection")
    val rtcConfig =
      PeerConnection.RTCConfiguration(
        listOf(
          PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
          PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
        ),
      )
    rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
    val factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
    val newPc =
      factory.createPeerConnection(rtcConfig, createObserver())
    if (newPc == null) {
      onStatus("Failed to create PeerConnection")
      return
    }
    pc = newPc
    // The TTS channel is created by the phone; the browser receives it via ondatachannel.
    val init = DataChannel.Init().apply { ordered = true }
    dataChannel = newPc.createDataChannel("tts", init)
    dataChannel?.registerObserver(
      object : DataChannel.Observer {
        override fun onBufferedAmountChange(previousAmount: Long) {}
        override fun onStateChange() {
          Log.d(TAG, "DataChannel state: ${dataChannel?.state()}")
        }

        override fun onMessage(buffer: DataChannel.Buffer) {
          if (buffer.binary) return
          val bytes = ByteArray(buffer.data.remaining())
          buffer.data.get(bytes)
          val text = String(bytes)
          if (text.contains("\"clear\"")) onClearRequest()
        }
      },
    )
    // Flush any signaling that arrived before the PC was ready.
    pendingIce.forEach { addIceCandidate(it) }
    pendingIce.clear()
    pendingSdp?.let { handleOffer(it) }
    pendingSdp = null
  }

  private fun closePeerConnection() {
    remoteAudioTrack?.setEnabled(false)
    remoteAudioTrack?.removeSink(micSink)
    remoteAudioTrack = null
    dataChannel?.close()
    dataChannel = null
    pc?.close()
    pc = null
    sttStarted = false
    pendingIce.clear()
    pendingSdp = null
  }

  private fun createObserver() =
    object : PeerConnection.Observer {
      override fun onSignalingChange(signalingState: PeerConnection.SignalingState?) {}

      override fun onIceConnectionChange(iceConnectionState: PeerConnection.IceConnectionState?) {
        Log.i(TAG, "ICE state: $iceConnectionState")
        mainHandler.post {
          when (iceConnectionState) {
            PeerConnection.IceConnectionState.CONNECTED,
            PeerConnection.IceConnectionState.COMPLETED -> {
              if (connected.compareAndSet(false, true)) {
                onStatus("🔊 Connected — speak into the browser mic!")
                onRtcConnected()
              }
            }

            PeerConnection.IceConnectionState.DISCONNECTED,
            PeerConnection.IceConnectionState.FAILED,
            PeerConnection.IceConnectionState.CLOSED -> {
              if (connected.compareAndSet(true, false)) onRtcDisconnected()
            }

            else -> {}
          }
        }
      }

      override fun onIceConnectionReceivingChange(receiving: Boolean) {}

      override fun onIceGatheringChange(iceGatheringState: PeerConnection.IceGatheringState?) {}

      override fun onIceCandidate(candidate: IceCandidate?) {
        if (candidate == null) return
        sendToSignaling(
          JSONObject()
            .put("type", "ice")
            .put(
              "candidate",
              JSONObject()
                .put("candidate", candidate.sdp)
                .put("sdpMid", candidate.sdpMid ?: "")
                .put("sdpMLineIndex", candidate.sdpMLineIndex),
            ),
        )
      }

      override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}

      override fun onAddStream(stream: org.webrtc.MediaStream?) {}

      override fun onRemoveStream(stream: org.webrtc.MediaStream?) {}

      override fun onDataChannel(channel: DataChannel?) {}

      override fun onRenegotiationNeeded() {}

      override fun onAddTrack(
        receiver: org.webrtc.RtpReceiver?,
        mediaStreams: Array<out org.webrtc.MediaStream>?,
      ) {
        val track = receiver?.track()
        if (track is AudioTrack) {
          Log.i(TAG, "Received browser audio track")
          remoteAudioTrack = track
          track.setEnabled(true)
          track.addSink(micSink)
        }
      }

      override fun onRemoveTrack(receiver: org.webrtc.RtpReceiver?) {}
    }

  private val micSink =
    AudioTrackSink { audioData, bitsPerSample, sampleRate, channelCount, _, _ ->
      if (bitsPerSample != 16) return@AudioTrackSink
      if (!sttStarted) {
        sttStarted = true
        Log.i(TAG, "Audio flowing: $sampleRate Hz, $channelCount ch")
      }
      val pcm = ByteArray(audioData.remaining())
      audioData.get(pcm)
      onMicPcm(pcm, sampleRate, channelCount)
    }

  private fun answerConstraints(): MediaConstraints =
    MediaConstraints().apply {
      mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
      mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
    }

  private companion object {
    const val TAG = "AGVoiceChatWebRtc"
  }
}
