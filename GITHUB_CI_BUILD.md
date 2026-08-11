# PC 없는 APK 빌드 파이프라인 (GitHub Actions)

> 이 저장소는 `.github/workflows/build_android.yaml` 워크플로로 **GitHub 서버(ubuntu)
> 에서 APK를 빌드**합니다. PC(Android Studio) 없이, 이 proot 환경에서도 GitHub에
> 푸시만 하면 APK를 받아 폰에 설치할 수 있습니다.

## 핵심 개념

```
코드 push (main, Android/** 변경)
        │
        ▼
GitHub Actions 실행 (ubuntu-latest, JDK 21 + SDK 37)
        │  ./gradlew assembleRelease
        ▼
APK 아티팩트 업로드 (actions/upload-artifact)
        │
        ▼
gh run download 로 APK 받기 → 폰 설치
```

## 1. 워크플로 트리거 조건

`build_android.yaml` 은 다음 때 실행됩니다:
- **`push`** to `main` — 단, `Android/**` 파일이 변경됐을 때만
- **`workflow_dispatch`** — 수동 실행 (아래 3번)
- **`pull_request`** to `main` — `Android/**` 변경 시

> ⚠️ `.github/workflows` 나 `skills/`, `docs/` 등 **Android 밖** 파일만 바꾸면
> 이 워크플로는 실행되지 않습니다.

## 2. APK 빌드 후 받기 (gh CLI)

```bash
# 토큰 로드 (realizer-1966 계정)
grep 'GITHUB_TOKEN' ~/.bashrc > /tmp/loadtoken.sh && source /tmp/loadtoken.sh

# 최근 빌드 실행 목록
gh run list --repo realizer-1966/gallery --workflow "Build Android APK" --limit 5

# 특정 커밋/실행의 APK 다운로드
gh run download <run_id> --repo realizer-1966/gallery --dir /tmp/apk
# → /tmp/apk/app-release/app-release.apk

# 최신 성공 빌드만 자동으로 받으려면:
LATEST=$(gh run list --repo realizer-1966/gallery --workflow "Build Android APK" --status success --limit 1 --json databaseId --jq '.[0].databaseId')
gh run download "$LATEST" --repo realizer-1966/gallery --dir /tmp/apk
```

## 3. 수동 실행 (워크플로 디스패치)

코드 변경 없이 지금 빌드만 돌리고 싶으면:
```bash
gh workflow run "Build Android APK" --repo realizer-1966/gallery --ref main
gh run watch --repo realizer-1966/gallery   # 완료까지 대기
```

## 4. 폰 설치

- `/tmp/apk/app-release/app-release.apk` 를 폰으로 전송
- 폰에서 APK 탭 → "출처를 알 수 없는 앱 허용" 후 설치
- 또는 `adb install app-release.apk` (USB 디버깅)

---

## ⚠️ 현재 상태 (2026-08-11): 빌드 실패 중

**`main` 의 최근 빌드는 실패 상태입니다.** 원인은 **Voice Chat 커스텀 태스크의
Kotlin 컴파일 오류**입니다 (커밋 `7cc4309` 이후). GitHub Actions 로그에서
확인된 오류:

| 파일 | 오류 |
|---|---|
| `VoiceChatWebRtc.kt:39,48` | `AudioTrack` 중복 import (ambiguous) |
| `VoiceChatWebRtc.kt:27,73` | ktor `DefaultClientWebSocketSession` API 불일치 |
| `VoiceChatProtocol.kt:104-106` | `sampleRate/channels/bitsPerSample` 변수 스코프 버그 (`fmt` 분기에서 선언, `data` 분기에서 참조) |
| `VoiceChatWebRtc.kt:110` | `VoiceChatProtocol.json()` 이 private 인데 외부 접근 |
| `VoiceChatWebRtc.kt:267-276` | 타입 불일치, `send` 미해결 |
| `VoiceChatStt.kt:122` | 함수 후보 불일치 |

→ **Voice Chat 코드는 아직 한 번도 성공적으로 컴파일된 적이 없습니다.**

### 이 상태로 파이프라인을 쓰려면 (2가지 방법)

**방법 A — Voice Chat을 빌드에서 잠시 비활성화 (빠름)**
- `DiceRollerTaskModule.kt` 처럼 `VoiceChatTaskModule` 의
  `@Provides @IntoSet` 등록을 주석 처리하면 Voice Chat이 홈 목록에서 빠지고
  **컴파일 대상에서도 제외**됩니다 → 빌드 성공 가능.
- 단점: Voice Chat 기능이 앱에서 안 보임 (보류 시 일치).

**방법 B — Voice Chat Kotlin 오류 수정 (기능 유지)**
- 위 6개 오류를 수정 → push → CI가 빌드 성공 여부를 검증.
- 네이티브 Voice Chat을 실제 쓰려면 이 경로가 필요.

---

## 파일 위치

| 파일 | 역할 |
|---|---|
| `.github/workflows/build_android.yaml` | APK 빌드 워크플로 |
| `Android/src/gradle/libs.versions.toml` | 버전 카탈로그 |
| `Android/src/app/build.gradle.kts` | 앱 모듈 설정 |
