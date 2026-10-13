# 글꼴 라이선스

앱에 들어 있는 글꼴(`app/src/main/res/font/`)의 라이선스 정보예요. `res/font`에는 글꼴과 xml만 둘 수 있어서 라이선스 전문은 이 폴더에 따로 뒀어요.

| 글꼴 파일 | 글꼴 | 글꼴 파일 안의 저작권 문구 | 라이선스 | 전문 | 확인 상태 |
|---|---|---|---|---|---|
| `black_han_sans.ttf` | Black Han Sans | Copyright 2015 The Black Han Sans Project Authors (https://github.com/zesstype/Black-Han-Sans) | SIL OFL 1.1 | [OFL-Black-Han-Sans.txt](OFL-Black-Han-Sans.txt) | 글꼴 파일 안의 문구와 google/fonts의 `OFL.txt`·`METADATA.pb`의 저작권 줄이 **일치**함 |
| `plex_kr_regular.ttf`, `plex_kr_medium.ttf`, `plex_kr_bold.ttf` | IBM Plex Sans KR | Copyright 2018 IBM Corp. All rights reserved. | SIL OFL 1.1 | [OFL-IBM-Plex-Sans-KR.txt](OFL-IBM-Plex-Sans-KR.txt) | 글꼴 파일 안의 문구와 google/fonts의 `METADATA.pb`가 **일치**함 |

## 확인한 방법과 미확인 항목

- 글꼴 파일에 내장된 저작권·라이선스 문자열을 직접 읽었고, google/fonts 저장소(`ofl/blackhansans`, `ofl/ibmplexsanskr`)의 `OFL.txt`와 `METADATA.pb`와 비교했어요.
- **미확인: 이 저장소의 `.ttf` 파일이 google/fonts의 파일과 바이트 단위로 같은지.** 해시를 비교하니 네 파일 모두 달랐어요(버전이나 가공 여부는 알 수 없어요). 그래서 "어디서 받은 파일인지"는 확인하지 못했고, 위 표는 파일 안에 적힌 문구를 기준으로 했어요.
- **미확인: IBM Plex Sans KR의 `OFL.txt`는 첫 줄이 `Copyright © 2017 IBM Corp. with Reserved Font Name "Plex"`**라서, 글꼴 파일 안의 `2018`과 연도가 달라요. google/fonts의 `METADATA.pb`는 `2018`이에요. 어느 쪽이 맞는지는 확인하지 못했어요.
- `OFL.txt`의 전문은 google/fonts에서 그대로 가져왔어요.