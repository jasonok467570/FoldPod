# Spotify 연결과 공개 범위

FoldPod의 OAuth 앱과 사용자의 음악 계정은 별개입니다. 앱에 포함된 Client ID는 공개 앱 식별자이고 각 사용자는 자기 Spotify 계정으로 로그인합니다. 개발자 개인 계정의 토큰·비밀번호·재생목록을 공유하거나 다른 사용자의 요청에 대신 사용하지 않습니다.

## 현재 설정

- Authorization Code + PKCE, Client Secret 없이 네이티브 앱에서 인증
- Redirect URI: `http://127.0.0.1:8888/callback`
- 범위: `playlist-read-private`, `playlist-read-collaborative`, `user-read-playback-state`, `user-modify-playback-state`
- 사용자의 `/v1/me/playlists` 조회, 사용자가 고른 목록·곡의 재생 요청
- 공식 재생 API에는 Premium이 필요하며 처음에는 사용할 휴대폰·태블릿 기기를 사용자가 확인
- 다른 음악 기기로 자동 전환하지 않으며 실제 곡·기기·목록이 확인돼야 재생 성공으로 처리

현재 Client ID는 `SpotifyLibraryRepository.kt`의 기본값입니다. 앱 내 Client ID 입력이나 Gradle property 주입은 구현되어 있지 않습니다. 독립적으로 fork하는 개발자는 자기 Spotify Developer 앱을 등록하고 해당 기본값·redirect 설정을 맞춰야 합니다. Client Secret이나 사용자 토큰을 소스에 넣지 마세요.

## 개발 모드와 공개 APK

2026-10-08 공식 문서 확인 기준, 개발 모드는 앱 소유자의 Premium과 최대 5명의 허용 사용자 등록을 요구합니다. 사용자의 이름·Spotify 이메일을 Developer Dashboard의 허용 목록에 등록하고 사용자가 직접 본인 계정으로 로그인합니다. 허용되지 않은 계정은 로그인 이후에도 API에서 403을 받을 수 있습니다. [Spotify quota modes](https://developer.spotify.com/documentation/web-api/concepts/quota-modes).

따라서 GitHub 소스·APK 공개만으로 Spotify의 사용자 제한이 해제되지 않습니다. 현재 문서의 extended quota 신규 신청은 조직을 대상으로 합니다. 일반 공개 사용자 모두에게 Spotify 연결이 된다고 안내하기 전에 해당 접근 승인이 필요합니다. [공식 안내](https://developer.spotify.com/documentation/web-api/concepts/quota-modes).

개발 모드에서 타인이 만든 일부 목록은 API 읽기가 거부될 수 있습니다. 링크 등록이나 계정 대체로 이를 우회하지 않습니다. 목록 조회 권한과 곡 선택 재생 권한은 별개이며 재생 가능 여부는 실제 API 결과로 확인합니다. [재생 API](https://developer.spotify.com/documentation/web-api/reference/start-a-users-playback).
