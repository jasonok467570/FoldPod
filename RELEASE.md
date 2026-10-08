# GitHub 소스·APK 배포

공개 방식은 MIT 소스 + 서명된 APK입니다. 공개 소스 저장소는 [jasonok467570/FoldPod](https://github.com/jasonok467570/FoldPod)이며 APK와 SHA-256 체크섬은 [0.7.1 Beta Release](https://github.com/jasonok467570/FoldPod/releases/tag/v0.7.1)에서 제공합니다. 0.7.1은 Beta(Pre-release)입니다. Spotify 개발 모드 허용 사용자와 Google OAuth 테스트 계정 제한을 유지하므로 모든 계정의 서비스 연결이 가능한 정식 출시로 해석하지 마세요.

## 공개할 자료

- 소스: 앱 코드·리소스·테스트, Gradle wrapper와 빌드 설정, README, LICENSE, PRIVACY 및 배포 안내
- APK: 최종 승인된 release 서명 빌드만 GitHub Release에 첨부
- 제외: `keystore.properties`, `signing/`, 서명용 개인 키, `.env*`, `local.properties`, `.idea/`, `.gradle/`, `build/`, 휴대폰 로그·스크린샷·앱 데이터
- Inter 폰트의 OFL 파일은 MIT와 함께 유지. 제3자 서비스·콘텐츠 권리는 MIT로 재허가하지 않음

공개 전 아래 검사를 실행합니다. 이것은 ZIP 생성이나 업로드를 하지 않습니다.

```sh
python3 scripts/export_source.py --check
python3 -m unittest discover -s scripts -p 'test_*.py'
git status --short
git check-ignore -- keystore.properties signing/foldpod-release.jks local.properties app/build
```

`export_source.py`는 명시한 소스 파일만 대상으로 하고 키·로컬 설정·빌드 결과를 제외합니다. 향후 소스 자체에 민감한 문자열을 넣었는지에 대한 검토를 대신하지 않습니다. 실제 공개 전에 선택한 파일의 diff와 전체 내용을 확인하세요. 프로젝트 전체 폴더 ZIP은 공유하지 마세요.

## 로컬 서명 준비

`app/build.gradle.kts`는 루트의 `keystore.properties`가 있을 때만 release 서명을 설정합니다. `keystore.properties.example`에는 예시만 있습니다. 서명 키는 `signing/foldpod-release.jks`에, 암호와 경로는 `keystore.properties`에 로컬로 보관하며 Git·소스 ZIP에서 제외합니다.

현재 로컬 키 준비 상태는 파일 존재와 `signing/release-certificate.txt`의 공개 인증서 지문으로 확인합니다. 개인 키와 암호는 화면·로그·문서·이슈에 출력하지 않습니다. 같은 앱을 업데이트할 때 동일한 키를 사용해야 하므로 두 파일의 안전한 별도 백업이 필요합니다. 키를 잃었다고 새 키를 자동 생성하거나 기존 파일을 덮어쓰지 마세요. [Android 앱 서명](https://developer.android.com/studio/publish/app-signing).

Google Android OAuth에는 `com.foldpod.app.release`와 **release 인증서의 SHA-1**에 대응하는 등록이 필요합니다. 2026-10-08 이 조합의 Android 클라이언트 등록을 완료했습니다. Debug 인증서에서 성공한 로그인은 release 로그인의 검증이 아닙니다. Google OAuth는 현재 테스트 모드입니다. 일반 사용자 공개 설정·민감 범위 검토는 별도 확인 대상으로 남습니다. [Google 사용자 데이터 승인](https://developer.android.com/identity/authorization), [민감 범위 검증](https://developers.google.com/identity/protocols/oauth2/production-readiness/sensitive-scope-verification).

## 0.7.1 검증 범위

2026-10-08 서명된 release 빌드에서 APK Signature Scheme v2, `com.foldpod.app.release` 패키지·FoldPod Release 표시 이름, non-debuggable 설정과 소스·APK의 개인 키·토큰 패턴 제외 검사를 통과했습니다. 실제 Samsung SM-F971N(Android API 37)에 기존 debug 앱을 보존한 채 별도로 설치했고, 설치된 APK와 배포 후보의 SHA-256 일치를 확인했습니다. 사용자는 Spotify·Google 로그인 완료와 앱 업데이트 후 Connect를 다시 누르지 않아도 두 서비스가 연결되어 있음을 확인했습니다. 이 로그인·복구 결과는 사용자 확인에 근거합니다. 다른 계정·기기의 연결과 release에서의 전체 재생 기능 회귀 검증은 미완료입니다.

최종 문서 commit으로 APK의 VCS metadata를 갱신할 때에는 검증한 빌드와 실행 코드·리소스가 동일한지 비교합니다. APK 자체의 SHA-256은 Release에 첨부한 `SHA256SUMS.txt`를 따르며, 아래 인증서 지문과는 다른 값입니다.

Release 인증서 공개 지문(개인 키가 아님):

- SHA-1: `A5:FB:A4:58:44:BD:A6:E1:48:C0:57:6F:48:80:96:4C:08:0E:DB:14`
- SHA-256: `1C:B1:7F:11:61:91:98:50:1A:A7:A4:1B:FE:F6:BE:12:5F:E5:6E:95:3A:EA:7C:51:F6:4F:44:21:1D:A4:F2:29`

후속 검증에는 허용된 다른 사용자의 본인 계정 로그인, 다른 기기, release에서 곡 선택·Next·SEEK·즐겨찾기·수동 Disconnect 유지 확인을 포함합니다. 자동화 검사 통과를 이 동작들의 실기기 검증으로 대신하지 않습니다.

공개 release는 `applicationIdSuffix = ".release"`를 사용해 `com.foldpod.app.release`로 설치하며 표시 이름은 **FoldPod Release**입니다. Android Studio debug 앱(`com.foldpod.app`, FoldPod)과 함께 설치되어 기존 앱의 로그인·설정·즐겨찾기를 보존합니다. 두 앱의 데이터는 분리되고 자동 복사하지 않으므로 release에서는 처음 한 번 알림 접근과 계정 연결을 다시 허용해야 합니다. 이후 release 업데이트는 같은 패키지와 서명 키를 유지합니다. 두 앱에서 Spotify Connect를 동시에 시작하면 동일한 loopback port(8888)가 충돌할 수 있으므로 순서대로 연결합니다. 기존 앱이나 사용자 데이터를 자동 삭제하지 마세요.

공개 버전은 `versionCode=8`, `versionName=0.7.1`입니다. 다음 APK 업데이트는 versionCode를 증가시키고 같은 release 패키지·키를 유지합니다. 공개 기록에는 GitHub noreply 이메일을 사용합니다. 원래 개인 이메일이 들어 있는 로컬 `main`은 보존하되 업로드하지 않습니다. 공개용 로컬 `public-source` 브랜치를 GitHub의 `main`으로 업로드합니다. 이후에도 공개 브랜치만 push하고 개인 이메일이 들어 있는 로컬 기록을 병합하거나 `--all`로 push하지 마세요. Spotify 공개 접근 제한은 [SPOTIFY_RELEASE.md](SPOTIFY_RELEASE.md)를 따릅니다.
