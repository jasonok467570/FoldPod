# GitHub 소스·APK 공개 준비

공개 방식은 MIT 소스 + 서명된 APK입니다. 공개 소스 저장소는 [jasonok467570/FoldPod](https://github.com/jasonok467570/FoldPod)입니다. 현재 소스를 먼저 공개하며, 배포 APK 생성·업로드는 release 인증서와 로그인 검증 후 진행합니다.

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

Google Android OAuth에는 `com.foldpod.app`와 **release 인증서의 SHA-1**에 대응하는 등록이 필요합니다. Debug 인증서에서 성공한 로그인은 release 로그인의 검증이 아닙니다. release 인증서 등록 및 Google OAuth 공개 설정·민감 범위 검토는 별도 확인 대상으로 남습니다. [Google 사용자 데이터 승인](https://developer.android.com/identity/authorization), [민감 범위 검증](https://developers.google.com/identity/protocols/oauth2/production-readiness/sensitive-scope-verification).

## 최종 확인 순서

1. Android Studio Run으로 재시작 후 두 서비스 연결 복구, 수동 Disconnect 유지, 곡 선택·Next·SEEK·즐겨찾기를 실제 휴대폰에서 확인
2. 다른 사용자가 자기 계정으로 로그인하는 테스트. Spotify는 개발 모드 허용 사용자, Google은 현재 OAuth 게시 상태에 맞는 사용자로 확인
3. release 인증서 OAuth 등록, 버전 확정, 공개 문서·민감 정보 검사
4. 이후 승인된 시점에 서명 APK 생성, release 설치·로그인·동작 확인, APK 인증서와 SHA-256 확인
5. 공개할 GitHub 저장소와 원격 주소 확정, 선택한 소스 commit/push, GitHub Release 작성 및 APK 첨부

Debug와 release의 서명이 다르면 기존 앱을 그대로 업데이트할 수 없습니다. 설치 충돌을 해결하려고 기존 앱이나 사용자 데이터를 자동 삭제하지 마세요. 릴리스 검증용 별도 기기·설치 계획을 먼저 정합니다.

현재 버전은 `versionCode=8`, `versionName=0.7.1`이며 APK 배포 직전에 기존 공개 버전과 비교해 결정합니다. 공개 기록에는 GitHub noreply 이메일을 사용합니다. 원래 개인 이메일이 들어 있는 로컬 `main`은 보존하되 업로드하지 않습니다. 공개용 로컬 `public-source` 브랜치를 GitHub의 `main`으로 업로드합니다. 이후에도 공개 브랜치만 push하고 개인 이메일이 들어 있는 로컬 기록을 병합하거나 `--all`로 push하지 마세요. Spotify 공개 접근 제한은 [SPOTIFY_RELEASE.md](SPOTIFY_RELEASE.md)를 따릅니다.
