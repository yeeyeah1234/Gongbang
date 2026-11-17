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


# --- CRW-01 ⑩ 분야별 최소 수집 ---

LABEL_OF = {
    "IT_SW": "웹/모바일/IT",
    "PLANNING": "기획/아이디어",
    "DESIGN_ART": "디자인/캐릭터/웹툰",
    "MARKETING": "광고/마케팅",
    "ETC": "기타",
}


def _items(code, count, start):
    return [
        {"ix": str(start + i), "title": f"{code}{i}", "host": "", "categories": [LABEL_OF[code]], "dday": 5}
        for i in range(count)
    ]


def _enough_except(*short_codes):
    """short_codes를 뺀 분야는 MIN_PER_CATEGORY개씩 있는 기본 목록"""
    items = []
    for n, code in enumerate(LABEL_OF):
        if code not in short_codes:
            items += _items(code, main.MIN_PER_CATEGORY, start=1000 * (n + 1))
    return items


def _patch_filter_pages(monkeypatch, pages):
    """pages: {(cidx, page): [목록 항목]}. 가져간 (page, cidx)를 기록한다."""
    fetched = []

    def fake_fetch(page, cidx=None):
        fetched.append((cidx, page))
        return (cidx, page)

    monkeypatch.setattr(main.wevity_client, "fetch_list_page", fake_fetch)
    monkeypatch.setattr(main.wevity_client, "parse_list_page", lambda key: pages.get(key, []))
    return fetched


def test_모든_분야가_충분하면_분야_필터_목록을_가져오지_않는다(monkeypatch):
    fetched = _patch_filter_pages(monkeypatch, {})
    base = _enough_except()

    assert main.fill_category_minimum(base) == base
    assert fetched == []


def test_모자란_분야만_분야_필터_목록에서_4개까지_채운다(monkeypatch):
    fetched = _patch_filter_pages(monkeypatch, {(20, 1): _items("IT_SW", 10, start=1)})
    base = _enough_except("IT_SW")

    result = main.fill_category_minimum(base)

    it_items = [i for i in result if i["categories"] == ["웹/모바일/IT"]]
    assert len(it_items) == main.MIN_PER_CATEGORY
    assert fetched == [(20, 1)]  # 첫 페이지에서 채웠으니 더 가져오지 않는다


def test_분야_필터_목록의_다른_분야_공모전과_이미_있는_공모전은_넣지_않는다(monkeypatch):
    already = _items("IT_SW", 1, start=1)  # ix=1은 기본 목록에 이미 있다
    filter_page = (
        _items("IT_SW", 1, start=1)  # 같은 ix → 건너뜀
        + _items("PLANNING", 3, start=500)  # IT 필터에 섞인 기획 공모전 → 건너뜀
        + _items("IT_SW", 5, start=10)
    )
    _patch_filter_pages(monkeypatch, {(20, 1): filter_page})
    base = _enough_except("IT_SW") + already

    result = main.fill_category_minimum(base)

    ixs = [i["ix"] for i in result]
    assert len(ixs) == len(set(ixs))
    assert not {"500", "501", "502"} & set(ixs)  # 섞여 있던 기획 공모전은 들어가지 않았다
    it_items = [i for i in result if i["categories"] == ["웹/모바일/IT"]]
    assert len(it_items) == main.MIN_PER_CATEGORY


def test_첫_필터에서_모자라면_다음_페이지와_다음_필터로_넘어간다(monkeypatch):
    fetched = _patch_filter_pages(
        monkeypatch,
        {(20, 1): _items("IT_SW", 1, start=1), (21, 1): _items("IT_SW", 5, start=100)},
    )
    base = _enough_except("IT_SW")

    result = main.fill_category_minimum(base)

    assert len([i for i in result if i["categories"] == ["웹/모바일/IT"]]) == main.MIN_PER_CATEGORY
    assert fetched == [(20, 1), (20, 2), (21, 1)]  # 20번 2페이지는 비어 있어 21번으로 넘어감


def test_위비티에_모자라면_있는_만큼만_모은다(monkeypatch):
    fetched = _patch_filter_pages(monkeypatch, {(2, 1): _items("MARKETING", 2, start=1)})
    base = _enough_except("MARKETING")

    result = main.fill_category_minimum(base)

    assert len([i for i in result if i["categories"] == ["광고/마케팅"]]) == 2
    assert fetched == [(2, 1), (2, 2)]  # 마케팅 필터는 하나뿐, 최대 페이지까지만 본다
