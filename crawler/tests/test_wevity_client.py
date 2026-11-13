from wevity_client import (
    _parse_category_labels,
    _parse_period,
    build_source_url,
    parse_detail_page,
    parse_list_page,
)

LIST_HTML = """
<ul class="list">
  <li class="top"><div class="tit">제목</div></li>
  <li>
    <div class="tit"><a href="?c=find&amp;gbn=view&amp;ix=12345">진행중인 공모전</a></div>
    <div class="organ">문화체육관광부</div>
    <div class="sub-tit">분야 : 기획/아이디어, 광고/마케팅</div>
    <div class="day">접수 <span class="dday">D-10</span></div>
  </li>
  <li>
    <div class="tit"><a href="?c=find&amp;gbn=view&amp;ix=99999">마감된 공모전</a></div>
    <div class="organ">한국디자인진흥원</div>
    <div class="sub-tit">분야 : 디자인/캐릭터/웹툰</div>
    <div class="day">접수 <span class="dday">마감</span></div>
  </li>
</ul>
"""

DETAIL_HTML = """
<meta property="og:image" content="/upload/thumb.jpg">
<div class="tit-area"><h6 class="tit">상세 공모전 제목</h6></div>
<ul class="cd-info-list">
  <li><span class="tit">주최/주관</span> 문화체육관광부</li>
  <li><span class="tit">분야</span> 기획/아이디어, 광고/마케팅</li>
  <li><span class="tit">접수기간</span> 2026-10-26 ~ 2026-12-06 D-25</li>
  <li><span class="tit">홈페이지</span> <a href="https://example.org/contest" target="_blank">https://example.org/contest</a></li>
</ul>
<div class="comm-desc" id="viewContents"><p>상세 설명 내용</p></div>
"""


def test_목록에서_마감된_항목은_제외한다():
    items = parse_list_page(LIST_HTML)
    assert [item["ix"] for item in items] == ["12345"]


def test_목록_항목의_필드를_파싱한다():
    item = parse_list_page(LIST_HTML)[0]
    assert item["title"] == "진행중인 공모전"
    assert item["host"] == "문화체육관광부"
    assert item["categories"] == ["기획/아이디어", "광고/마케팅"]
    assert item["dday"] == 10


def test_상세_페이지를_파싱한다():
    detail = parse_detail_page(DETAIL_HTML)
    assert detail["title"] == "상세 공모전 제목"
    assert detail["host"] == "문화체육관광부"
    assert detail["categories"] == ["기획/아이디어", "광고/마케팅"]
    assert detail["thumbnail_url"] == "https://www.wevity.com/upload/thumb.jpg"
    assert detail["start_date"] == "2026-10-26"
    assert detail["end_date"] == "2026-12-06"
    assert "상세 설명 내용" in detail["description"]
    assert detail["homepage_url"] == "https://example.org/contest"


def test_홈페이지가_없거나_http가_아니면_None():
    assert parse_detail_page(LIST_HTML)["homepage_url"] is None
    for href in ["javascript:alert(1)", "www.example.org", ""]:
        html = f'<ul class="cd-info-list"><li><span class="tit">홈페이지</span> <a href="{href}">x</a></li></ul>'
        assert parse_detail_page(html)["homepage_url"] is None


def test_카테고리_라벨_파싱():
    assert _parse_category_labels("분야 : 기획/아이디어, 광고/마케팅") == [
        "기획/아이디어",
        "광고/마케팅",
    ]
    assert _parse_category_labels("기획/아이디어") == ["기획/아이디어"]
    assert _parse_category_labels("") == []


def test_접수기간_파싱():
    assert _parse_period("2026-10-26 ~ 2026-12-06 D-25") == (
        "2026-10-26",
        "2026-12-06",
    )
    assert _parse_period("기간 미정") == (None, None)


def test_원본_url_생성():
    url = build_source_url("12345")
    assert "ix=12345" in url
    assert "gp=" not in url


def test_og_image가_없으면_썸네일은_None():
    detail = parse_detail_page("<div class=\"tit-area\"><h6 class=\"tit\">제목</h6></div>")
    assert detail["thumbnail_url"] is None
