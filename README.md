# TFT 덱 순위 - 갤럭시 워치 타일

```
[집 PC] crawl.py ──(git push)──> [GitHub Pages] docs/decks.json ──(인터넷)──> [갤럭시 워치 타일/앱]
```

- 타일: 상위 5개 덱 (순위 · 티어 · 덱 이름). 덱을 누르면 상세 화면이 열려요.
- 상세: 캐리 챔피언별 아이템, 초반 빌드업(레벨별 배치), 최종 덱 구성
- 롤체지지가 클라우드 IP(GitHub Actions 포함)를 막아서 **크롤링은 집 PC**에서 해요.

## 1. GitHub 저장소 + Pages (5분)

1. GitHub에 `tft-watch` 저장소(Public)를 만들고 이 폴더 전체를 push해요.
2. 저장소 **Settings → Pages → Branch: `main` / 폴더: `/docs`** 로 저장해요.
3. 1~2분 뒤 `https://<깃허브ID>.github.io/tft-watch/decks.json` 이 열리면 성공이에요.
   (처음에는 `예시 덱` 샘플 데이터가 들어 있어요)

## 2. PC 크롤러

```bat
cd crawler
pip install -r requirements.txt
python -m playwright install chromium
python crawl.py
```

- 성공하면 `docs/decks.json`이 갱신되고 상위 5개 덱이 출력돼요.
- 롤체지지 `/meta` 페이지의 `__NEXT_DATA__`(추천 메타 덱 목록)를 읽고, 덱마다 상세 페이지에서 Lv5~7 배치를 가져와요.
- 롤체지지 추천 덱에는 S/A 티어가 없어서 `tier`는 `HOT` 태그가 있으면 `"HOT"`, 없으면 빈 값이에요. 순위는 사이트에 나오는 순서예요.
- **실패하거나 결과가 이상하면** (사이트 구조가 바뀐 경우) `python crawl.py --dump` 를 실행하고
  `crawler/dump/` 폴더(page.html, queries.json)를 Claude에게 주세요.
- `crawler/.venv` 가상환경이 있으면 `run_and_push.bat` 이 그걸 먼저 써요.

### 자동 실행 (작업 스케줄러)

`run_and_push.bat` = 크롤링 → 변경 있으면 commit & push

```bat
schtasks /create /tn "TFT Deck Crawl" /tr "C:\경로\tft-watch\crawler\run_and_push.bat" /sc hourly /mo 3
```

## 3. 워치 앱 빌드 & 설치

1. `wear/gradle.properties` 의 `decksUrl` 을 1번에서 확인한 주소로 바꿔요.
2. Android Studio로 `wear` 폴더를 열고 빌드해요.
3. 워치 설치 (무선 디버깅):
   - 워치: 설정 → 디바이스 정보 → 소프트웨어 정보 → **소프트웨어 버전 7번 탭** → 개발자 옵션 켜기
   - 개발자 옵션 → **ADB 디버깅**, **무선 디버깅** 켜기 → "새 기기 페어링"에 나오는 IP:포트, 코드 확인
   - PC (워치와 같은 와이파이):
     ```
     adb pair <IP>:<페어링포트>      (코드 입력)
     adb connect <IP>:<연결포트>
     adb install -r app\build\outputs\apk\debug\app-debug.apk
     ```
     Android Studio에서 바로 ▶ Run 해도 돼요.
4. 워치에서 시계 화면을 길게 누르거나 타일 편집 → **"TFT 덱 순위"** 타일 추가.

타일은 1시간마다 새로 받아오고, 앱을 열면 즉시 새로고침 + 타일도 갱신돼요. 인터넷이 안 되면 마지막으로 받은 데이터를 보여줘요.

## decks.json 형식

```json
{
  "updatedAt": "11/17 21:00",
  "decks": [
    {
      "rank": 1, "name": "덱 이름", "tier": "S",
      "units":   [{"name": "챔피언", "cost": 4, "items": ["아이템1", "아이템2", "아이템3"]}],
      "carries": [{"name": "챔피언", "cost": 4, "items": ["..."]}],
      "early":   [{"label": "Lv4", "units": ["챔피언", "..."]}]
    }
  ]
}
```
