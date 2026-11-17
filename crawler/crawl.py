"""
TFT 메타 덱 크롤러 -> docs/decks.json

집 PC에서 실행하세요. (롤체지지는 클라우드 IP를 막아서 GitHub Actions에서는 안 돌아요)

  pip install -r requirements.txt
  python -m playwright install chromium
  python crawl.py            # docs/decks.json 생성
  python crawl.py --dump     # 받은 원본을 dump/ 에 저장 (사이트 구조가 바뀌었을 때 확인용)

출처
  1) MetaTFT (주 출처, 플래티넘+ 최근 3일 통계)  -> decks
     - 화면(metatft.com/comps)에서 순위·티어(S/A..)·덱 이름을 읽고,
     - 공개 API(comps_stats / comps_data / comp_details)에서 유닛·캐리 아이템·초반 보드를 가져와요.
     - 한글 이름은 MetaTFT의 ko_kr 조회표를 써요.
  2) 롤체지지 메타 통계 (보조, 마스터+ 만 제공)  -> extra[0].decks
     - lolchess.gg/decks 의 __NEXT_DATA__ 를 읽어요. 실패해도 MetaTFT 결과는 저장해요.

MetaTFT 덱을 3개 미만으로 찾으면 기존 decks.json은 건드리지 않아요.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
import urllib.request
from datetime import datetime, timezone, timedelta
from pathlib import Path

from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "docs" / "decks.json"
DUMP = Path(__file__).resolve().parent / "dump"
TOP_N = 5
EARLY_LEVELS = (4, 5, 6, 7)
KST = timezone(timedelta(hours=9))

MT_PAGE = "https://www.metatft.com/comps"
MT_API = "https://api-hc.metatft.com/tft-comps-api"
MT_RANKS = "CHALLENGER,DIAMOND,EMERALD,GRANDMASTER,MASTER,PLATINUM"  # 플래티넘 이상
MT_DAYS = 3
MT_MIN_PICK = 0.10  # 화면을 못 읽었을 때만 쓰는 최소 픽률 (표본이 적은 덱 제외)

LC_PAGE = "https://lolchess.gg/decks"

MT_IMG = "https://cdn.metatft.com/cdn-cgi/image/width=64,height=64,format=auto/https://cdn.metatft.com/file/metatft"

_dump: dict[str, object] = {}
_icons: dict[str, str] = {}  # 한글 이름(챔피언·아이템) -> 아이콘 주소. 결과에 나온 이름만 담아요.


def get_json(url: str):
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0", "Accept": "application/json"})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.loads(r.read().decode("utf-8"))


def flat_text(page) -> str:
    """화면 텍스트를 ' | ' 로 이어 붙인 한 줄로 (빈 칸은 합쳐요)"""
    parts = [s.strip() for s in page.inner_text("body").split("\n")]
    return "| " + " | ".join(s for s in parts if s) + " |"


# ---------------------------------------------------------------- MetaTFT
LEVELLING_KO = {"standard": "표준 운영"}


def levelling_ko(s: str | None) -> str:
    if not s:
        return ""
    m = re.fullmatch(r"fast (\d+)", s.strip(), re.I)
    if m:
        return f"빠른 {m.group(1)}레벨"
    m = re.fullmatch(r"lvl (\d+)", s.strip(), re.I)
    if m:
        return f"{m.group(1)}레벨 리롤"
    return LEVELLING_KO.get(s.strip().lower(), s)


def metatft(page) -> list[dict]:
    stats = get_json(f"{MT_API}/comps_stats?queue=1100&patch=current&days={MT_DAYS}"
                     f"&rank={MT_RANKS}&permit_filter_adjustment=true")
    clusters = get_json(f"{MT_API}/comps_data?queue=1100")["results"]["data"]["cluster_details"]
    tft_set = stats.get("tft_set") or "TFTSet18"
    ko = get_json(f"https://data.metatft.com/lookups/{tft_set}_latest_ko_kr.json")
    _dump.update(mt_stats=stats, mt_clusters=clusters)

    units: dict[str, dict] = {}
    for u in ko.get("units", []):
        for key in (u.get("assetNames") or []) + [u.get("apiName"), u.get("characterName")]:
            if key:
                units[key] = u
    items = {i["apiName"]: i["name"] for i in ko.get("items", [])}
    traits = {t["apiName"]: t for t in ko.get("traits", [])}
    en2ko = {x["en_name"].lower(): x["name"]
             for x in ko.get("units", []) + ko.get("traits", []) if x.get("en_name") and x.get("name")}

    def uname(key: str) -> str:
        u = units.get(key, {})
        name = u.get("name") or key
        if u.get("apiName"):
            _icons.setdefault(name, f"{MT_IMG}/champions/{u['apiName'].lower()}.png")
        return name

    def iname(key: str) -> str:
        name = items.get(key, key)
        _icons.setdefault(name, f"{MT_IMG}/items/{key.lower()}.png")
        return name

    def ucost(key: str):
        c = units.get(key, {}).get("cost")
        return c if isinstance(c, int) and 1 <= c <= 6 else None

    def names_by_cost(keys: list[str]) -> list[str]:
        return [uname(k) for k in sorted(keys, key=lambda k: ucost(k) or 0)]

    def ko_name(en: str) -> str:
        """'Vanguard Master Yi' -> '선봉대 마스터 이' (가장 긴 영문 이름부터 맞춰요)"""
        words, out, i = en.split(), [], 0
        while i < len(words):
            for n in range(len(words) - i, 0, -1):
                hit = en2ko.get(" ".join(words[i:i + n]).lower())
                if hit:
                    out.append(hit)
                    i += n
                    break
            else:
                out.append(words[i])
                i += 1
        return " ".join(out)

    # 클러스터별 통계 (사이트와 같은 계산: 평균 등수, 게임당 픽률)
    total = next((r["places"][0] for r in stats["results"] if r.get("cluster") == ""), 0)
    st: dict[str, dict] = {}
    for r in stats["results"]:
        cid, n, p = r.get("cluster"), r.get("count"), r.get("places") or []
        if cid not in clusters or not n or len(p) < 8 or not total:
            continue
        st[cid] = {
            "avg": sum((i + 1) * p[i] for i in range(8)) / n,
            "pick": n / total * 8,
            "top4": sum(p[:4]) / n * 100,
            "win": p[0] / n * 100,
        }

    # 화면에서 순위·티어·이름 읽기 (사이트가 숨기는 덱을 그대로 따르기 위해)
    ranked: list[tuple[str, str, str]] = []  # (cluster id, tier, 영문 이름)
    try:
        page.goto(MT_PAGE, wait_until="domcontentloaded", timeout=60_000)
        page.wait_for_function("document.body.innerText.includes('Avg Place')", timeout=40_000)
        page.wait_for_timeout(2500)
        text = flat_text(page)
        _dump["mt_text"] = text
        rows = re.findall(
            r"\| ([SABCDX]) \| ([^|]+?) \| [^|]+? \| (?:Easy|Medium|Hard) \|(.*?)"
            r"Avg Place \| ([\d.]+) \| Pick Rate \| ([\d.]+) \|", text)
        used: set[str] = set()
        for tier, name, mid, avg, pick in rows:
            avg, pick = float(avg), float(pick)
            best, best_score = None, -1.0
            for cid, s in st.items():
                if cid in used or abs(s["avg"] - avg) > 0.03 or abs(s["pick"] - pick) > 0.03:
                    continue
                ens = [units.get(k, {}).get("en_name") or "" for k in clusters[cid]["units_string"].split(", ")]
                score = sum(1 for e in ens if e and f"| {e} |" in mid + "|")
                if score > best_score:
                    best, best_score = cid, score
            if best:
                used.add(best)
                ranked.append((best, tier, name.strip()))
    except Exception as e:
        print(f"[warn] MetaTFT 화면을 못 읽었어요 (API 순위로 대체): {e}")

    if len(ranked) < 3:
        print("[warn] MetaTFT 화면 순위를 못 찾아서 API 통계로 직접 정렬해요 (티어 표시 없음)")
        order = sorted((c for c in st if st[c]["pick"] >= MT_MIN_PICK), key=lambda c: st[c]["avg"])
        ranked = [(c, "", "") for c in order]

    decks = []
    for cid, tier, en_name in ranked[:TOP_N]:
        c, s = clusters[cid], st[cid]
        builds = sorted(c.get("builds") or [], key=lambda b: -(b.get("score") or 0))
        carry_items: dict[str, list[str]] = {}
        for b in builds:
            if b.get("unit") and b["unit"] not in carry_items and b.get("buildName"):
                carry_items[b["unit"]] = [iname(i) for i in b["buildName"]][:3]
        keys = sorted(c["units_string"].split(", "), key=lambda k: ucost(k) or 0)

        if en_name:
            name = ko_name(en_name)
        else:  # API 이름(특성 + 유닛)으로 조합
            parts = []
            for n in c.get("name") or []:
                key = n.get("name", "")
                parts.append(traits.get(key, {}).get("name") or uname(key))
            name = " ".join(parts) or cid

        early = []
        try:
            det = get_json(f"{MT_API}/comp_details?comp={cid}&cluster_id={cid[:-3]}")["results"]
            for lv in EARLY_LEVELS:
                opts = (det.get("early_options") or {}).get(str(lv)) or []
                if opts and opts[0].get("unit_list"):
                    early.append({"label": f"Lv{lv}", "units": names_by_cost(opts[0]["unit_list"].split("&"))})
        except Exception as e:
            print(f"[warn] '{name}' 초반 보드를 못 가져왔어요: {e}")

        plan = levelling_ko(c.get("levelling"))
        decks.append({
            "name": name,
            "tier": tier,
            "stat": f"평균 {s['avg']:.2f}등 · TOP4 {s['top4']:.1f}% · 픽률 {s['pick']:.2f}"
                    + (f"\n운영: {plan}" if plan else ""),
            "units": [{"name": uname(k), "cost": ucost(k), "items": carry_items.get(k, [])} for k in keys],
            "carries": [{"name": uname(k), "cost": ucost(k), "items": v}
                        for k, v in list(carry_items.items())[:3]],
            "early": early,
        })
    return decks


# ---------------------------------------------------------------- 롤체지지 (마스터+ 통계)
def lolchess(page) -> list[dict]:
    page.goto(LC_PAGE, wait_until="domcontentloaded", timeout=60_000)
    html = page.content()
    m = re.search(r'<script id="__NEXT_DATA__"[^>]*>(.*?)</script>', html, re.S)
    queries = json.loads(m.group(1))["props"]["pageProps"]["dehydratedState"]["queries"]
    data = next(q["state"]["data"] for q in queries if (q.get("queryKey") or [None])[0] == "meta-decks")
    _dump["lc_meta_decks"] = data

    champs = {c["key"]: c for c in data["refs"]["champions"]}
    items = {i["key"]: i for i in data["refs"]["items"]}

    def iname(key: str) -> str:
        ref = items.get(key, {})
        name = ref.get("name") or key
        if ref.get("imageUrl"):
            _icons.setdefault(name, ref["imageUrl"])
        return name

    def unit(c: dict) -> dict:
        ref = champs.get(c["key"], {})
        cost = ref.get("cost")
        name = ref.get("name") or c["key"]
        if ref.get("imageUrl"):
            _icons.setdefault(name, ref["imageUrl"])
        return {"name": name,
                "cost": cost if isinstance(cost, int) else None,
                "items": [iname(i) for i in c.get("items") or []][:3]}

    # 티어(S/A..)는 화면에만 있어요
    tiers: dict[str, str] = {}
    try:
        page.wait_for_function("document.body.innerText.includes('평균 등수')", timeout=20_000)
        text = flat_text(page)
        for tier, name in re.findall(r"팀 코드 복사 \| (OP|[SABCD]) \| ([^|]+?) \|", text):
            tiers.setdefault(name.strip(), tier)
    except Exception as e:
        print(f"[warn] 롤체지지 티어 표시를 못 읽었어요: {e}")

    decks = []
    meta = sorted(data["metaDeckList"]["metaDecks"], key=lambda d: d.get("avgPlacement") or 9)
    for d in meta[:TOP_N]:
        cs = d["deck"]["champions"]
        ordered = sorted(cs, key=lambda c: champs.get(c["key"], {}).get("cost") or 0)
        core = sorted((c for c in cs if c.get("items")), key=lambda c: c.get("coreRank") or 99)
        name = (d.get("deckNameKo") or d.get("deckNameEn") or "?").strip()
        decks.append({
            "name": name,
            "tier": tiers.get(name, ""),
            "stat": f"평균 {d['avgPlacement']:.2f}등 · TOP4 {d['topRate']:.1f}% · 픽률 {d['pickRate']:.2f}",
            "units": [unit(c) for c in ordered],
            "carries": [unit(c) for c in core[:3]],
            "early": [],
        })
    return decks


# ---------------------------------------------------------------- 메인
def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dump", action="store_true", help="받은 원본을 dump/ 폴더에 저장")
    args = ap.parse_args()

    main_decks: list[dict] = []
    lc_decks: list[dict] = []
    with sync_playwright() as p:
        browser = p.chromium.launch()
        page = browser.new_page(locale="en-US", viewport={"width": 1400, "height": 1400})
        try:
            main_decks = metatft(page)
        except Exception as e:
            print(f"[error] MetaTFT 수집 실패: {e}")
        try:
            # 롤체지지는 브라우저 언어에 따라 이름이 바뀌어서 한국어 페이지로 따로 열어요
            lc_decks = lolchess(browser.new_page(locale="ko-KR", viewport={"width": 1400, "height": 1400}))
        except Exception as e:
            print(f"[warn] 롤체지지 수집 실패 (MetaTFT 결과만 저장): {e}")
        browser.close()

    if args.dump or len(main_decks) < 3:
        DUMP.mkdir(exist_ok=True)
        (DUMP / "raw.json").write_text(json.dumps(_dump, ensure_ascii=False), encoding="utf-8")
        print(f"[dump] {DUMP / 'raw.json'} 에 저장했어요")

    if len(main_decks) < 3:
        print("[error] MetaTFT 덱을 찾지 못했어요. 기존 decks.json은 그대로 둡니다.")
        sys.exit(1)

    for decks in (main_decks, lc_decks):
        for i, d in enumerate(decks, 1):
            d["rank"] = i

    data = {
        "updatedAt": datetime.now(KST).strftime("%m/%d %H:%M"),
        "title": "MetaTFT 플래티넘+",
        "source": MT_PAGE,
        "decks": main_decks,
        "extra": [{"title": "롤체지지 마스터+", "source": LC_PAGE, "decks": lc_decks}] if lc_decks else [],
        "icons": dict(sorted(_icons.items())),
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(data, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"[ok] {OUT} 저장 완료")
    for title, decks in [(data["title"], main_decks)] + [(e["title"], e["decks"]) for e in data["extra"]]:
        print(f"== {title}")
        for d in decks:
            early = ",".join(e["label"] for e in d["early"]) or "없음"
            print(f"  {d['rank']}. [{d['tier'] or '-'}] {d['name']} | {d['stat'].splitlines()[0]} | "
                  f"캐리: {', '.join(c['name'] for c in d['carries'])} | 초반: {early}")


if __name__ == "__main__":
    main()
