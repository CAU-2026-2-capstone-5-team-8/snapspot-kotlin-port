# 프로토타입 2: 위젯 — 단계와 진행 추적

상위 계획: [prototype-overview.md](prototype-overview.md) · 다른 기능: [prototype-map.md](prototype-map.md)
코드 위치: 이 저장소의 `app/`
마지막 갱신: 2026-10-13 (시작 전)

## 목적

알림을 못 받거나 놓쳐도 홈 화면에서 지금 쓸 수 있는 쿠폰을 본다. 프로토타입이라 **동작하는 뼈대를 먼저** 만들고, 표시 내용은 나중에 구체화한다.

## 시작 조건

[prototype-map.md](prototype-map.md)의 게이트 G1(작성자가 지도 결과를 확인)을 통과한 뒤에 시작한다. 기술적으로는 지도 코드와 서로 의존하지 않는다. 위젯은 `CouponRepository`와 `WindowRepository`만 읽고, 지도 쪽 변경은 이 작업에 영향을 주지 않는다. 순서는 작성자가 정한 것이다.

## 이 기능의 원칙

- 새 로직은 새 파일에만 둔다 (`domain/WidgetContent.kt`, `ui/widget/` 아래 파일들). 기존 파일은 꼭 필요한 연결만 덧붙이고, 기존 함수의 동작·시그니처는 바꾸지 않는다.
- 알림, 지오펜스, 장소 탭, 쿠폰 화면, 지도의 동작은 바뀌면 안 된다.
- 기존 테스트는 수정하지 않고 통과해야 한다. 고쳐야 하는 상황이 생기면 멈추고 알린다.
- 거리 숫자 대신 **"근처 지점 있음"**으로 표시한다. 감시 창의 위치(`WindowState.here`)는 현재 위치가 아니라 계획을 세운 위치(갱신 구역의 중심)라서, 숫자로 보이면 최대 약 1km 어긋난다.
- 소형·중형 한 종류만 만든다. 위젯이 위치를 직접 갱신하지 않는다.
- 이번에 하지 않는 것: 알림을 거부한 사용자에게 "위젯과 지도로도 볼 수 있어요"를 안내하는 연결(7단계 권한 흐름), 장소 위젯, 지도 미니뷰.
- 커밋은 요청이 있을 때만 한다.

## 상태 값

`대기` / `진행` / `완료` / `막힘`. 완료는 "완료 기준"을 실제로 확인했을 때만 쓴다. 확인하지 못했으면 `완료(미확인)`으로 적는다.

## 단계

| ID | 할 일 | 완료 기준 | 상태 | 확인 결과 |
|---|---|---|---|---|
| P2-0 | 선행 확인: G1 통과 여부, 다른 작업과 겹치는 파일 확인 (`AndroidManifest.xml`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, `WindowSync`, `AppViewModel`) | 작성자에게 보고 | 대기 | |
| P2-1 | Glance 의존성 추가: compileSdk 37, AGP 9.4.1과 맞는 안정 버전을 고르고 근거 기록 | `assembleDebug` 성공, 기존 JVM 테스트 전부 무수정 통과 | 대기 | |
| P2-2 | `domain/WidgetContent.kt`: `widgetCoupons(coupons, win, now, limit = 3)`. 활성 쿠폰만, 만료 빠른 순(`splitCoupons`·`dDay`·`daysLeft` 재사용), 쿠폰마다 `nearBranch`(감시 창에 같은 브랜드 지점이 있는지) | JVM 테스트: 정렬 / 개수 제한 / 사용·만료 제외 / `nearBranch` / 쿠폰 0개 | 대기 | |
| P2-3 | 위젯 화면 `ui/widget/SnapSpotWidget.kt` (소형·중형, "근처 지점 있음", 빈 상태 "저장한 쿠폰이 없어요") + `SnapSpotWidgetReceiver` + `res/xml/snapspot_widget_info.xml` + 매니페스트(덧붙임만) + 문자열 | 컴파일, 매니페스트의 기존 항목에 diff 없음 | 대기 | |
| P2-4 | 데이터 연결: Hilt `@EntryPoint`(`data/geofence/Replan.kt`의 `ReplanWorker.Deps` 방식)로 `CouponRepository.getAll()`·`WindowRepository.load()` 읽기. 쿠폰 탭 → `snapspot://coupon/{id}`, 머리글 탭 → 앱 열기 | 에뮬레이터 홈에 추가해 쿠폰이 보이고 탭하면 해당 쿠폰이 열림 | 대기 | |
| P2-5 | 갱신 연결: `WindowSync.sync` 끝, 쿠폰 저장·사용 처리·삭제 뒤 `SnapSpotWidget().updateAll`, 안전망 `updatePeriodMillis` 30분. 기존 파일을 고치므로 장부에 파일·이유를 기록 | 쿠폰을 사용 처리하면 위젯이 바뀜. 기존 테스트 무수정 통과, 알림·지오펜스 동작 불변 | 대기 | |
| P2-6 | 눈으로 확인: 쿠폰 0·1·4장, 만료 임박 순서, 알림 권한을 끈 상태, 재부팅 후 표시. 그리고 `assembleRelease`로 빌드해 실행(R8이 Glance·카카오를 건드리는지) | 항목별 ○/× 표, release에서 앱이 시작되고 위젯이 표시됨 | 대기 | |
| P2-7 | `prototype-overview.md` 위젯 부분 갱신 + 보고 | 변경한 기존 파일, 테스트 결과, 미확인 항목을 보고 | 대기 | |

## 위험

- Glance 버전과 AGP·Kotlin 호환.
- release에서만 터지는 R8 규칙 누락 (지난번 카카오 지도 SDK와 같은 유형이라 P2-6에서 release를 꼭 실행한다).
- 위젯 프로세스에서 Hilt/Room에 접근하는 방식.
- 갱신 누락 (쿠폰이 바뀌었는데 위젯이 그대로).

## 기존 파일 변경 장부

아직 없음. 파일을 고칠 때마다 아래 표에 한 줄씩 적는다.

| 파일 | 덧붙인 것 | 동작이 안 바뀌는 근거 |
|---|---|---|
|  |  |  |

## 막힌 것 · 나중에 할 것

- 나중에: 알림 거부 사용자에게 위젯·지도 안내 연결, 위젯 크기 종류 확대, 위젯이 위치를 직접 갱신할지(배터리 영향), 장소 위젯, 거리 표시(위치가 정확해진 뒤).

## 게이트

없음 (이 문서가 마지막). 끝나면 P2-7 보고로 마무리한다.
