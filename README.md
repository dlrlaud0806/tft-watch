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
- **주 출처: MetaTFT (플래티넘+, 최근 3일 통계).** 순위·티어(S/A…)는 metatft.com/comps 화면에서, 유닛·캐리 아이템·초반 보드(Lv4~7)는 공개 API에서 가져와요. 타일에 나오는 5개가 이거예요.
- **보조 출처: 롤체지지 메타 통계 (마스터+만 제공).** 앱 목록의 두 번째 섹션에 나와요. 실패해도 MetaTFT 결과는 저장돼요.
- 챔피언·아이템 아이콘 주소는 `icons`(한글 이름 → 주소)에 같이 저장돼요.
- **실패하거나 결과가 이상하면** (사이트 구조가 바뀐 경우) `python crawl.py --dump` 를 실행하고
  `crawler/dump/raw.json` 을 Claude에게 주세요.
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
  "title": "MetaTFT 플래티넘+",
  "icons": {"챔피언 또는 아이템 이름": "https://…png"},
  "extra": [{"title": "롤체지지 마스터+", "decks": ["…decks와 같은 형식"]}],
  "decks": [
    {
      "rank": 1, "name": "덱 이름", "tier": "S", "stat": "평균 4.08등 · TOP4 56.1% · 픽률 0.18",
      "units":   [{"name": "챔피언", "cost": 4, "items": ["아이템1", "아이템2", "아이템3"]}],
      "carries": [{"name": "챔피언", "cost": 4, "items": ["..."]}],
      "early":   [{"label": "Lv4", "units": ["챔피언", "..."]}]
    }
  ]
}
```
