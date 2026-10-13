# SnapSpot (Kotlin)

스크린샷에서 가 보고 싶은 장소와 프랜차이즈 쿠폰을 읽어 두었다가, 근처에 가면 알림을 주는 Android 앱이에요.
Kotlin + Jetpack Compose이고, 기존 Expo(React Native) 앱을 옮긴 것이에요. 이 저장소는 비교용으로 공유하는 이식본이에요.

## 처음 빌드하기

1. **경로**: 프로젝트를 한글이 없는 경로에 두세요(예: `C:\dev\snapspot`). 한글 경로는 Gradle이 막아요.
2. **JDK 17** 이상과 Android SDK(compileSdk 37)가 필요해요. Android Studio로 열면 알아서 받아요.
3. 프로젝트 루트에 `local.properties`를 만들어요. 이 파일은 Git에 올리지 않아요.

```properties
sdk.dir=C:/Users/<이름>/AppData/Local/Android/Sdk   # 본인 PC의 Android SDK 위치
GEMINI_API_KEY=...        # 스크린샷 읽기 (Google AI Studio)
KAKAO_REST_KEY=...        # 장소·지점 검색 (카카오 디벨로퍼스 REST API 키)
KAKAO_NATIVE_KEY=...      # 지도 표시 (카카오 디벨로퍼스 네이티브 앱 키)
# 선택: release 서명. 없으면 debug 키로 서명해서 테스트 폰에 설치할 수 있어요.
# RELEASE_STORE_FILE=../snapspot-release.jks
# RELEASE_STORE_PASSWORD=...
# RELEASE_KEY_ALIAS=...
# RELEASE_KEY_PASSWORD=...
```

`sdk.dir`는 슬래시(`/`)로 써요. 역슬래시는 이스케이프로 읽혀서 깨져요.

키가 비어 있어도 빌드와 단위 테스트는 돼요. 다만 스크린샷 읽기와 지도는 동작하지 않아요.

## 카카오 지도 키 해시

지도를 보려면 카카오 콘솔에서 **[앱] → [플랫폼 키] → [네이티브 앱 키]** 에 패키지명 `com.capstone.snapspot`과 **내 PC의 debug 키 해시**를 등록해야 해요. 컴퓨터마다 해시가 달라서 팀원마다 각자 등록해야 해요.

```
keytool -exportcert -alias androiddebugkey -keystore %USERPROFILE%\.android\debug.keystore -storepass android | openssl sha1 -binary | openssl base64
```

등록하기 전에는 지도 대신 안내 문구만 나와요. 패키지명을 바꾸면(예: `applicationIdSuffix`) 인증이 깨지므로 바꾸지 않아요.

## 실행과 테스트

```
./gradlew installDebug                  # 연결된 기기/에뮬레이터에 설치
./gradlew testDebugUnitTest             # JVM 단위 테스트
./gradlew connectedDebugAndroidTest     # 기기 테스트 (에뮬레이터 필요)
./gradlew lintDebug                     # 정적 검사
./gradlew assembleRelease               # R8로 줄인 release APK
```

- 지도 SDK는 **ARM 전용**이에요. x86 에뮬레이터에서는 앱은 켜지지만 지도만 꺼져요(지도를 보려면 ARM 번역이 되는 이미지나 실기기를 쓰세요).
- 기기 테스트는 설치가 실패해도 `BUILD SUCCESSFUL`로 끝날 수 있어요. `app/build/outputs/androidTest-results`의 테스트 수가 0이 아닌지 꼭 확인하세요.
- 에뮬레이터 위치는 `adb emu geo fix <경도> <위도>`로 바꿔요(경도가 먼저). 앱이 고정밀 위치를 요청 중일 때만 반영돼요.

## 설계·기록 문서

- `docs/`: 프로토타입(지도 매핑, 위젯) 계획과 진행 기록. `docs/prototype-overview.md`부터 읽으세요. (PR ①에서 들어와요.)
- `licenses/`: 글꼴 라이선스.
- 팀 저장소 `snapspot`의 코틀린 앱과는 **따로 만든 이식본**이고 패키지명이 같아서 한 기기에 같이 설치할 수 없어요.
## 한계 (프로토타입)

- **API 키가 APK 안에 들어가요.** `local.properties`의 키가 `BuildConfig`로 빌드에 포함되므로 APK를 받은 사람이 키를 꺼낼 수 있어요. 배포용으로는 서버를 거쳐야 해요.
- release는 R8(코드 줄이기)을 써요. 카카오 지도 SDK는 `proguard-rules.pro`의 규칙이 없으면 시작할 때 죽어요. SDK를 올리면 release 빌드를 꼭 실행해 보세요.
- 에뮬레이터에서만 확인했어요. 실기기(제조사별 "항상 허용" 화면, 배터리 정책)는 미확인이에요.

## CI

`.github/workflows/android.yml`: PR과 push에서 단위 테스트, lint, debug/release 빌드를 해요. 기기 테스트는 수동 실행(`workflow_dispatch`)에서만 돌아요. 이 파일은 아직 GitHub에서 실행해 보지 않았어요.
