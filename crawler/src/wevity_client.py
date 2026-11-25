import re
import time
from urllib.parse import urljoin, urlsplit

import requests
from bs4 import BeautifulSoup

from config import WEVITY_BASE_URL, USER_AGENT, REQUEST_DELAY_SECONDS
from html_sanitizer import sanitize_description

_session = requests.Session()
_session.headers["User-Agent"] = USER_AGENT
_last_request_at = 0.0


def _throttled_get(url):
    global _last_request_at
    wait = REQUEST_DELAY_SECONDS - (time.monotonic() - _last_request_at)
    if wait > 0:
        time.sleep(wait)
    response = _session.get(url, timeout=10)
    _last_request_at = time.monotonic()
    response.raise_for_status()
    return response.text


def build_source_url(ix):
    # CRW-01 ⑥, DB 비고: 원본 URL은 gp(페이지 번호)를 빼고 ix(공모전 번호)만 남겨 저장.
    return f"{WEVITY_BASE_URL}?c=find&s=1&gub=1&gbn=view&ix={ix}"


def fetch_list_page(page_number, cidx=None):
    # cidx: 위비티 분야 필터 번호 (CRW-01 ⑩ 분야별 최소 수집). None이면 전체 목록.
    cidx_param = "" if cidx is None else cidx
    url = f"{WEVITY_BASE_URL}?c=find&s=1&gub=1&cidx={cidx_param}&gbn=list&gp={page_number}"
    return _throttled_get(url)


def fetch_detail_page(ix):
    url = f"{WEVITY_BASE_URL}?c=find&s=1&gub=1&gbn=view&gp=1&ix={ix}"
    return _throttled_get(url)


def parse_list_page(html):
    """목록 페이지에서 공모전 항목을 뽑는다. 마감(closed)된 항목은 뺀다."""
    soup = BeautifulSoup(html, "html.parser")
    items = []
    for li in soup.select("ul.list > li"):
        link = li.select_one("div.tit > a[href*='ix=']")
        if not link:
            continue  # li.top(헤더 행) 등

        href = link.get("href", "")
        match = re.search(r"ix=(\d+)", href)
        if not match:
            continue
        ix = match.group(1)

        dday_span = li.select_one("div.day span.dday")
        status_text = dday_span.get_text(strip=True) if dday_span else ""
        if status_text == "마감":
            continue  # 마감된 공모전은 수집하지 않는다 (CRW-01, MAIN-03).

        title_text = link.contents[0] if link.contents else ""
        title = str(title_text).strip()

        organ_div = li.select_one("div.organ")
        host = organ_div.get_text(strip=True) if organ_div else ""

        sub_tit = li.select_one("div.sub-tit")
        categories = _parse_category_labels(sub_tit.get_text(strip=True) if sub_tit else "")

        day_div = li.select_one("div.day")
        day_text = day_div.get_text(" ", strip=True) if day_div else ""
        dday_match = re.search(r"D-(\d+)", day_text)
        dday = int(dday_match.group(1)) if dday_match else None

        items.append({
            "ix": ix,
            "title": title,
            "host": host,
            "categories": categories,
            "dday": dday,
        })
    return items


def parse_detail_page(html):
    """상세 페이지에서 CRW-01 수집 항목을 뽑는다."""
    soup = BeautifulSoup(html, "html.parser")

    title_el = soup.select_one("div.tit-area h6.tit")
    title = title_el.get_text(strip=True) if title_el else ""

    og_image = soup.select_one("meta[property='og:image']")
    thumbnail_url = (
        urljoin(WEVITY_BASE_URL, og_image["content"])
        if og_image and og_image.get("content")
        else None
    )

    fields = {}
    homepage_url = None
    for li in soup.select("ul.cd-info-list > li"):
        label_el = li.select_one("span.tit")
        if not label_el:
            continue
        label = label_el.get_text(strip=True)
        if label == "홈페이지":
            homepage_url = _parse_homepage_url(li)
        value_text = li.get_text(" ", strip=True)
        # label과 span 텍스트를 뺀 나머지가 값이다.
        value = value_text[len(label):].strip() if value_text.startswith(label) else value_text
        fields[label] = value

    host = fields.get("주최/주관", "")
    categories = _parse_category_labels(fields.get("분야", ""))

    start_date, end_date = _parse_period(fields.get("접수기간", ""))

    description_el = soup.select_one("div.comm-desc#viewContents")
    description = description_el.decode_contents().strip() if description_el else ""
    # 프론트가 HTML로 그리므로 안전한 태그만 남기고, 상대 이미지 주소는 위비티 절대 주소로 바꾼다
    description = sanitize_description(description, WEVITY_BASE_URL)

    return {
        "title": title,
        "host": host,
        "categories": categories,
        "thumbnail_url": thumbnail_url,
        "start_date": start_date,
        "end_date": end_date,
        "description": description,
        "homepage_url": homepage_url,
    }


def _parse_homepage_url(li):
    # MAIN-02 ④ [바로가기]: 주최 측 원래 공모전 페이지. http(s) 주소만 받는다 (AGENTS.md 9장, javascript: 등 차단)
    link = li.select_one("a[href]")
    href = link["href"].strip() if link else li.get_text(" ", strip=True).replace("홈페이지", "", 1).strip()
    if len(href) <= 500 and _is_backend_acceptable_url(href):
        return href
    return None


# 백엔드 공통 UrlPolicy(#120)는 java.net.URI로 해석하고 호스트를 요구한다.
# 거부되는 주소가 한 건이라도 있으면 배치 전체가 400이므로, 그런 주소는 보내지 않는다.
_URI_ILLEGAL_CHARS = set(' "<>\\^`{|}[]')
_BAD_PERCENT_RE = re.compile(r"%(?![0-9A-Fa-f]{2})")
_HOSTNAME_RE = re.compile(
    r"(?:[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?\.)*[A-Za-z](?:[A-Za-z0-9-]*[A-Za-z0-9])?\.?"
)
_IPV4_RE = re.compile(r"\d{1,3}(?:\.\d{1,3}){3}")


def _is_backend_acceptable_url(url):
    if url.count("#") > 1 or _BAD_PERCENT_RE.search(url):
        return False
    if any(c in _URI_ILLEGAL_CHARS or c.isspace() or not c.isprintable() for c in url):
        return False
    try:
        parts = urlsplit(url)  # 전각 문자 등으로 netloc이 깨지면 ValueError
        parts.port  # 포트가 숫자가 아니면 ValueError
    except ValueError:
        return False
    if parts.scheme.lower() not in ("http", "https") or parts.netloc.count("@") > 1:
        return False
    host = parts.netloc.rpartition("@")[2].rsplit(":", 1)[0]
    if _IPV4_RE.fullmatch(host):
        return all(int(octet) <= 255 for octet in host.split("."))
    return bool(_HOSTNAME_RE.fullmatch(host))


def _parse_category_labels(text):
    # 입력 예: "분야 : 기획/아이디어, 광고/마케팅" 또는 "기획/아이디어, 광고/마케팅"
    text = text.split(":", 1)[-1] if ":" in text else text
    return [label.strip() for label in text.split(",") if label.strip()]


def _parse_period(text):
    # 입력 예: "2026-10-26 ~ 2026-12-06 D-25"
    match = re.search(r"(\d{4}-\d{2}-\d{2})\s*~\s*(\d{4}-\d{2}-\d{2})", text)
    if not match:
        return None, None
    return match.group(1), match.group(2)
