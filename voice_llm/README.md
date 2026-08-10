# Voice Chat — LiteRT LLM over WebRTC

갤러리(Gallery)의 커스텀 태스크로 추가된 **음성 대화(Voice Chat)** 기능입니다.
브라우저의 마이크 소리를 WebRTC로 폰으로 보내면, **폰에서 전부 on-device로 처리**합니다:

```
브라우저 마이크
   │  WebRTC 오디오 (Opus)
   ▼
┌────────────────────────── 폰 (Android) ──────────────────────────┐
│  Vosk (온디바이스 STT)  →  LiteRT LM (Gemma3-1B q4)  →  TTS      │
└──────────────────────────┬───────────────────────────────────────┘
                           │  TTS PCM (WebRTC DataChannel, DTLS 암호화)
                           ▼
                     브라우저 스피커
```

- **LLM**: LiteRT LM 런타임 (`com.google.ai.edge.litertlm`) — 갤러리 모델 매니저로
  HuggingFace에서 `Gemma3-1B-IT q4` (약 550MB)를 다운로드.
- **STT**: Vosk 온디바이스 음성 인식 (ko/en). 최초 사용 시 모델 자동 다운로드(~40MB).
- **TTS**: Android 시스템 TTS가 생성한 PCM을 WebRTC 데이터 채널로 브라우저에 스트리밍.
- **WebRTC**: `org.webrtc:google-webrtc` + 자체 WebSocket signaling 서버(Node.js).

## 아키텍처

```
voice_llm/
├── server/                  # signaling 서버 + 브라우저 클라이언트 (Node.js, ws)
│   ├── server.js            #   WebSocket relay (join/ready/sdp/ice) + 정적 호스팅
│   └── public/index.html    #   브라우저 피어 (getUserMedia → WebRTC → PCM 재생)
Android/.../customtasks/voicechat/
├── VoiceChatTask.kt         #   태스크 등록 (llm_voice_chat, Category.LLM) + Gemma3 모델 정의
├── VoiceChatViewModel.kt    #   파이프라인 오케스트레이션
├── VoiceChatWebRtc.kt       #   ktor WebSocket signaling + PeerConnection
├── VoiceChatStt.kt          #   Vosk STT (16kHz 모노 PCM 입력)
├── VoiceChatTts.kt          #   Android TTS → PCM 콜백
├── VoiceChatProtocol.kt     #   데이터채널 JSON 프로토콜
└── VoiceChatScreen.kt       #   Compose UI (연결/언어/대화내역)
```

## 사용법

### 1. signaling 서버 실행 (PC 또는 LAN의 아무 머신)

```bash
cd voice_llm/server
npm install
node server.js            # 기본 8080 포트 (PORT=XXXX 로 변경 가능)
```

### 2. 브라우저에서 열기

- 같은 LAN이면 `http://<서버IP>:8080` (폰에서도 같은 서버에 접근 가능해야 함).
- 인터넷 서버면 공개 URL.
- 방 코드(room)를 아무 문자열로 정해서 입력 → **Join** 클릭 → "Connected" 대기.

### 3. 폰에서 갤러리 실행

1. **Voice Chat** 태스크 선택.
2. 모델 화면에서 **Gemma 3 1B (voice)** 다운로드 (최초 1회, ~550MB).
3. 태스크 화면에서:
   - 서버 URL 입력: `ws://<서버IP>:8080` (폰과 PC가 같은 네트워크).
   - 방 코드: 브라우저와 동일하게 입력.
   - 언어 선택 (ko/en), STT 모델이 없으면 Download.
   - **Connect** → 브라우저와 WebRTC 연결 성공.
4. 브라우저에서 마이크 허용 후 말하기 → 폰이 인식·추론·답변하고,
   폰의 TTS 음성이 브라우저에서 재생됩니다.

> 팁: 폰과 브라우저가 같은 Wi-Fi면 가장 간단합니다. 다른 네트워크면
> signaling 서버를 공개 호스트에 두고, WebRTC ICE는 STUN(기본
> `stun:stun.l.google.com:19302`)을 사용합니다. TURN이 없어 일부 NAT에서는
> P2P가 안 될 수 있습니다.

## 커스텀 태스크 구조 (갤러리 패턴)

- `CustomTask` 인터페이스 구현 + Hilt `@IntoSet` 등록 (자동 탐색).
- `Task(models = [...])` 에 `Model` 정의 시 갤러리의 모델 다운로드 UI
  (허용 목록/프로필 저장/진행률)가 자동으로 붙습니다.
- 모델 파일은 `model.getPath(context)` 로 확인 가능하며,
  `LlmChatModelHelper.initialize/runInference` 가 LiteRT LM 엔진을 관리합니다.
