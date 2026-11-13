import datetime

import main
from main import _estimate_deadline, build_contest_payload


def test_목록은_MAX_PAGES까지만_가져온다(monkeypatch):
    fetched_pages = []

    def fake_fetch(page):
        fetched_pages.append(page)
        return "<html>"

    monkeypatch.setattr(main.wevity_client, "fetch_list_page", fake_fetch)
    # 목록이 끝나지 않고 계속 나오는 경우에도 MAX_PAGES에서 멈춰야 한다
    monkeypatch.setattr(main.wevity_client, "parse_list_page", lambda html: [{"ix": "1"}] * 15)

    items = main.collect_list_items()

    assert fetched_pages == list(range(1, main.MAX_PAGES + 1))
    assert len(items) == 15 * main.MAX_PAGES


def test_dday로_마감일을_추정한다():
    today = datetime.date.today()
    assert _estimate_deadline(10) == (today + datetime.timedelta(days=10)).isoformat()
    assert _estimate_deadline(None) is None


def test_상세_정보가_있으면_상세_값을_우선한다():
    item = {"title": "목록 제목", "host": "목록 주최", "categories": ["기타"]}
    detail = {
        "title": "상세 제목",
        "host": "상세 주최",
        "categories": ["기획/아이디어"],
        "thumbnail_url": "https://example.com/t.jpg",
        "start_date": "2026-10-26",
        "end_date": "2026-12-06",
        "description": "<p>내용</p>",
        "homepage_url": "https://example.org/contest",
    }

    payload = build_contest_payload(item, detail, "https://www.wevity.com/?ix=1")

    assert payload["homepageUrl"] == "https://example.org/contest"
    assert payload["title"] == "상세 제목"
    assert payload["host"] == "상세 주최"
    assert payload["categoryCode"] == "PLANNING"
    assert payload["deadline"] == "2026-12-06"
    assert payload["sourceSite"] == "WEVITY"


def test_상세_값이_비어있으면_목록_값으로_보완한다():
    item = {"title": "목록 제목", "host": "목록 주최", "categories": ["기타"]}
    detail = {
        "title": "",
        "host": "",
        "categories": [],
        "thumbnail_url": None,
        "start_date": None,
        "end_date": "2026-12-06",
        "description": "",
        "homepage_url": None,
    }

    payload = build_contest_payload(item, detail, "https://www.wevity.com/?ix=1")

    assert payload["title"] == "목록 제목"
    assert payload["host"] == "목록 주최"
    assert payload["categoryCode"] == "ETC"
