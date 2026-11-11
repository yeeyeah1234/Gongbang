import datetime

from main import _estimate_deadline, build_contest_payload


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
    }

    payload = build_contest_payload(item, detail, "https://www.wevity.com/?ix=1")

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
    }

    payload = build_contest_payload(item, detail, "https://www.wevity.com/?ix=1")

    assert payload["title"] == "목록 제목"
    assert payload["host"] == "목록 주최"
    assert payload["categoryCode"] == "ETC"
