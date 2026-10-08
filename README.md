# FoldPod

Galaxy Z Fold 커버 화면을 위한 Click Wheel 음악 컨트롤러입니다. 현재 앱 버전은 0.7.1이며 음악은 Spotify·YouTube Music 등의 재생 앱에서 재생합니다.

공개 소스: [jasonok467570/FoldPod](https://github.com/jasonok467570/FoldPod). MIT 라이선스를 적용합니다. 서명된 배포 APK는 실제 휴대폰 로그인·재시작 검증 후 [GitHub Releases](https://github.com/jasonok467570/FoldPod/releases)에 제공합니다. 0.7.1은 서비스 계정 접근 제한이 있는 Beta로 준비하고 있습니다.

## 사용 방법

1. GitHub Releases의 `FoldPod-v0.7.1.apk`를 설치하고 **FoldPod Release**를 엽니다. 최소 Android 버전은 8.1(API 27)입니다.
2. 첫 실행에서 FoldPod Release의 알림 접근을 허용합니다. 음악 앱의 MediaSession 조회·제어에 사용합니다.
3. 아래 서비스 제한을 확인하고 `Settings → Connections`에서 **사용자 본인의 계정**으로 Spotify 또는 YouTube를 연결합니다. 개발자 계정의 비밀번호나 토큰을 제공받을 필요가 없습니다.
4. `Playlists → Spotify / YouTube Music → 재생목록`에서 곡을 선택합니다. Spotify 재생에는 Premium과 사용자가 확인한 재생 기기가 필요합니다.

소스를 개발하려면 Android Studio에서 SDK 37 및 JDK 17을 준비하고, USB 디버깅을 허용한 휴대폰을 선택해 `app` 구성을 **Run**합니다. Debug 앱 **FoldPod**와 배포 앱 **FoldPod Release**는 함께 설치할 수 있으며 계정·설정·즐겨찾기 데이터가 분리됩니다.

소스를 직접 빌드한다면 빌드에 사용한 인증서 SHA-1과 패키지(debug: `com.foldpod.app`, release: `com.foldpod.app.release`)에 맞는 Google Android OAuth 등록이 필요합니다. 기존 개발자의 debug 인증서 등록은 다른 컴퓨터에서 만든 debug 빌드에 적용되지 않습니다. 독립적인 서비스 설정과 현재 접근 제한은 [RELEASE.md](RELEASE.md) 및 [SPOTIFY_RELEASE.md](SPOTIFY_RELEASE.md)를 확인하세요.

## 기능

- iPod classic에서 영감을 받은 LCD·Click Wheel UI, 앨범 표지와 긴 제목 스크롤, 플레이어 아이콘·배터리 표시
- MENU로 상위 이동, 휠로 선택, 가운데 버튼으로 실행, 이전·다음·재생/일시정지
- Now Playing 가운데 버튼: NORMAL → SEEK → NORMAL. SEEK 단위는 Settings → Seek Step에서 1~60초(기본 5초)로 설정
- SEEK 중 재생 유지. 휠 입력 종료 약 400ms 후 현재 위치에서 이동하며, 진입·무입력 종료만으로는 탐색 명령을 보내지 않음
- 음악 앱이 제공하는 현재 대기열(다음 트랙) 탐색과 곡 선택. 직접 선택 지원·확인 가능 여부는 재생 앱에 따라 다름
- 서비스별 계정 재생목록, 즐겨찾기 상단 고정, YouTube 공개·일부 공개 재생목록 링크 추가
- 휠 민감도, 햅틱, Idle 화면, Sleep Timer

## 연결과 재시작

Spotify는 각 사용자의 OAuth PKCE 로그인으로 발급받은 토큰을 Android Keystore로 암호화해 해당 휴대폰에 저장하고 재시작 시 갱신합니다. Google은 기존 승인으로 조용히 재인증하여 YouTube 목록을 복구합니다. 추가 동의가 필요한 경우 Connections에서 다시 연결해야 합니다. 직접 Disconnect한 서비스는 자동 연결하지 않습니다.

계정 연결과 음악 앱의 활성 MediaSession은 별개입니다. Player에는 설치된 Spotify·YouTube Music도 표시하지만 해당 음악 앱이 세션을 제공하지 않으면 `Not ready`로 표시합니다. 음악 앱에서 먼저 재생하면 제어할 수 있습니다. 시작 시 임의로 음악을 재생하지 않습니다.

## 서비스 제한

Spotify 개발 모드는 허용 사용자 등록이 필요하므로 APK를 설치한 모든 사람이 바로 Web API를 사용할 수 있는 상태는 아닙니다. 각 사용자는 자신의 Spotify 계정으로 로그인합니다. 타인이 만든 목록의 읽기 제한은 앱의 접근 모드·서비스 권한에 따라 달라지며 링크로 우회하지 않습니다. 자세한 내용은 [SPOTIFY_RELEASE.md](SPOTIFY_RELEASE.md)에 있습니다.

Google OAuth는 현재 테스트 모드이므로 등록된 테스트 계정만 연결할 수 있습니다. APK 공개와 Google OAuth의 일반 사용자 공개 승인은 별개입니다.

YouTube 목록은 YouTube Data API로 조회합니다. YouTube Music의 모든 자동 생성 목록·믹스를 동일하게 제공한다고 보장하지 않습니다. 공개·일부 공개 일반 재생목록은 Connections → YouTube Add Playlist로 링크를 추가할 수 있습니다.

## 개발 및 공개 준비

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:testDebugUnitTest --offline --console=plain
python3 -m unittest discover -s scripts -p 'test_*.py'
python3 scripts/export_source.py --check
```

소스 공개는 MIT 라이선스를 적용합니다. Inter 폰트는 별도의 OFL 고지를 유지합니다. [LICENSE](LICENSE), [폰트 라이선스](app/src/main/assets/licenses/inter_ofl.txt), [PRIVACY.md](PRIVACY.md), [RELEASE.md](RELEASE.md)를 확인하세요.

프로젝트 전체 폴더를 ZIP으로 공유하지 마세요. 공개 대상만 선택하는 `scripts/export_source.py`를 사용하고 소스에 직접 작성된 민감 정보도 별도로 검토하세요. 서명 키, `keystore.properties`, 로컬 설정, 빌드 결과 및 기기 로그는 공개 소스에서 제외합니다.

FoldPod는 Apple, Spotify 또는 Google의 공식 앱이 아닙니다. MIT 라이선스는 제3자 상표·서비스·음원·앨범 이미지의 권리를 부여하지 않습니다.
