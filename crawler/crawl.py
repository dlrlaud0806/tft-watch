"""
롤체지지(lolchess.gg) 메타 덱 크롤러 -> docs/decks.json

집 PC에서 실행하세요. (롤체지지는 클라우드 IP를 막아서 GitHub Actions에서는 안 돌아요)

  pip install -r requirements.txt
  python -m playwright install chromium
  python crawl.py            # docs/decks.json 생성
  python crawl.py --dump     # 페이지 원본을 dump/ 에 저장 (구조가 바뀌었을 때 확인용)

동작 방식 (롤체지지는 Next.js라서 페이지의 __NEXT_DATA__ 안에 데이터가 다 들어 있어요)
  1) /meta 의 getGuideDecks 쿼리 = 추천 메타 덱 목록 (사이트에 보이는 순서 그대로)
     championRefs / itemRefs 쿼리 = 영문 키 -> 한글 이름, 코스트
  2) 상위 덱마다 /builder/guide/<key> 를 열어 레벨별 빌더 키(builder_lv5_uuid ...)를 얻고,
     그 빌더를 열어 초반 빌드업(레벨별 배치)을 가져와요.
  3) 덱을 3개 미만으로 찾으면 기존 decks.json은 건드리지 않고 dump/ 폴더를 남겨요.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from datetime import datetime, timezone, timedelta
from pathlib import Path

from playwright.sync_api import sync_playwright

BASE = "https://lolchess.gg"
META_URL = f"{BASE}/meta"
ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "docs" / "decks.json"
DUMP = Path(__file__).resolve().parent / "dump"
TOP_N = 5
EARLY_LEVELS = (5, 6, 7)          # 초반 빌드업으로 보여줄 레벨
SKIP_NAME = ("요약", "특성 사다리")  # 덱이 아닌 안내용 항목
KST = timezone(timedelta(hours=9))


def next_queries(page, url: str) -> tuple[dict[str, object], str]:
    """페이지를 열고 __NEXT_DATA__ 의 react-query 캐시를 {queryKey[0]: data} 로 돌려줘요."""
    page.goto(url, wait_until="domcontentloaded", timeout=60_000)
    html = page.content()
    m = re.search(r'<script id="__NEXT_DATA__"[^>]*>(.*?)</script>', html, re.S)
    if not m:
        return {}, html
    try:
        queries = json.loads(m.group(1))["props"]["pageProps"]["dehydratedState"]["queries"]
    except (json.JSONDecodeError, KeyError, TypeError):
        return {}, html
    out = {}
    for q in queries:
        key = q.get("queryKey") or [None]
        out[str(key[0])] = (q.get("state") or {}).get("data")
    return out, html


def team_builder(page, key: str) -> dict:
    q, _ = next_queries(page, f"{BASE}/builder/guide/{key}?type=guide")
    return ((q.get("teamBuilder") or {}).get("teamBuilder")) or {}


def crawl(dump: bool) -> list[dict]:
    with sync_playwright() as p:
        browser = p.chromium.launch()
        page = browser.new_page(locale="ko-KR")

        q, html = next_queries(page, META_URL)
        if dump:
            DUMP.mkdir(exist_ok=True)
            (DUMP / "page.html").write_text(html, encoding="utf-8")
            (DUMP / "queries.json").write_text(json.dumps(q, ensure_ascii=False), encoding="utf-8")
            print(f"[dump] {DUMP} 에 저장했어요")

        champs = {c["key"]: c for c in (q.get("championRefs") or {}).get("champions", [])}
        items = {i["key"]: i["name"] for i in (q.get("itemRefs") or {}).get("items", [])}
        guide_decks = (q.get("getGuideDecks") or {}).get("guideDecks", [])

        def champ_name(key: str) -> str:
            return champs.get(key, {}).get("name") or key

        def unit(slot: dict) -> dict:
            key = slot.get("champion") or "?"
            cost = champs.get(key, {}).get("cost")
            return {
                "name": champ_name(key),
                "cost": cost[0] if isinstance(cost, list) and cost else None,
                "star": slot.get("star"),
                "items": [items.get(i, i) for i in slot.get("items") or []][:3],
            }

        def unit_names(slots: list) -> list[str]:
            us = sorted((unit(s) for s in slots), key=lambda u: u["cost"] or 0)
            return [u["name"] for u in us]

        decks = []
        for g in guide_decks:
            name = (g.get("name") or "").strip()
            slots = (g.get("data") or {}).get("slots") or []
            if not name or len(slots) < 5 or any(s in name for s in SKIP_NAME):
                continue
            units = sorted((unit(s) for s in slots), key=lambda u: u["cost"] or 0)
            decks.append({
                "name": name,
                "tier": "HOT" if g.get("tag") == "hot" else "",
                "units": units,
                "early": [],
                "_key": g.get("teamBuilderKey"),
            })
            if len(decks) == TOP_N:
                break

        # 초반 빌드업: 실패해도 덱 자체는 살려요
        for d in decks:
            try:
                guide = team_builder(page, d["_key"]).get("guide") or {}
                for lv in EARLY_LEVELS:
                    key = guide.get(f"builder_lv{lv}_uuid")
                    if not key:
                        continue
                    slots = team_builder(page, key).get("slots") or []
                    if slots:
                        d["early"].append({"label": f"Lv{lv}", "units": unit_names(slots)})
            except Exception as e:  # 네트워크 오류 등
                print(f"[warn] '{d['name']}' 초반 빌드업을 못 가져왔어요: {e}")
            del d["_key"]

        if len(decks) < 3 and not dump:
            DUMP.mkdir(exist_ok=True)
            (DUMP / "page.html").write_text(html, encoding="utf-8")
            print(f"[dump] {DUMP} 에 저장했어요")
        browser.close()

    print(f"[info] 덱 {len(decks)}개 (전체 가이드 덱 {len(guide_decks)}개)")
    return decks


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dump", action="store_true", help="페이지 원본을 dump/ 폴더에 저장")
    args = ap.parse_args()

    decks = crawl(args.dump)
    if len(decks) < 3:
        print("[error] 덱을 찾지 못했어요. 기존 decks.json은 그대로 둡니다. dump/ 폴더를 확인해 주세요.")
        sys.exit(1)

    for i, d in enumerate(decks, 1):
        d["rank"] = i
        # 아이템을 든 유닛 = 캐리 (아이템 많은 순 -> 성급 -> 코스트, 최대 3명)
        with_items = [u for u in d["units"] if u["items"]]
        d["carries"] = sorted(
            with_items, key=lambda u: (-len(u["items"]), -(u["star"] or 0), -(u["cost"] or 0))
        )[:3]

    data = {
        "updatedAt": datetime.now(KST).strftime("%m/%d %H:%M"),
        "source": META_URL,
        "decks": decks,
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(data, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"[ok] {OUT} 저장 완료")
    for d in decks:
        early = ", ".join(e["label"] for e in d["early"]) or "없음"
        print(f"  {d['rank']}. [{d['tier'] or '-'}] {d['name']} - 캐리: "
              f"{', '.join(c['name'] for c in d['carries'])} / 초반: {early}")


if __name__ == "__main__":
    main()
