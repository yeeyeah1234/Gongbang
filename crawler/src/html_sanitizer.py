"""위비티 상세 본문(HTML)에서 화면에 넣어도 안전한 태그·속성만 남긴다 (AGENTS.md 9장).

상세 본문은 프론트가 HTML로 그리므로(MAIN-02 ②), script·이벤트 속성(onclick 등)·javascript: 주소가
들어오면 XSS가 된다. 새 라이브러리 없이 BeautifulSoup으로 허용 목록 방식으로 거른다.
"""

from urllib.parse import urljoin

from bs4 import BeautifulSoup, Comment

# 내용까지 통째로 지우는 태그
_DROP_TAGS = {
    "script", "style", "iframe", "frame", "frameset", "object", "embed", "applet",
    "form", "input", "button", "select", "textarea", "link", "meta", "base", "svg", "math",
    "noscript", "template",
}

# 태그와 허용 속성. 여기 없는 태그는 태그만 벗기고 안의 글자는 남긴다.
_ALLOWED = {
    "p": set(), "div": set(), "span": set(), "br": set(), "hr": set(),
    "b": set(), "strong": set(), "i": set(), "em": set(), "u": set(), "s": set(), "strike": set(),
    "sub": set(), "sup": set(), "small": set(), "blockquote": set(), "pre": set(), "code": set(),
    "h1": set(), "h2": set(), "h3": set(), "h4": set(), "h5": set(), "h6": set(),
    "ul": set(), "ol": set(), "li": set(),
    "table": set(), "thead": set(), "tbody": set(), "tfoot": set(), "tr": set(),
    "th": {"colspan", "rowspan"}, "td": {"colspan", "rowspan"},
    "a": {"href"},
    "img": {"src", "alt"},
}

# 주소를 담는 속성. 상대 주소(위비티 이미지 /upload/...)는 원본 사이트 기준 절대 주소로 바꾼다.
_URL_ATTRS = {"href", "src"}


def _safe_url(value, base_url):
    url = urljoin(base_url, value.strip())
    # http(s)만 받는다 (javascript:, data: 등 차단)
    return url if url.startswith(("http://", "https://")) else None


def sanitize_description(html, base_url):
    if not html:
        return html
    soup = BeautifulSoup(html, "html.parser")

    for comment in soup.find_all(string=lambda text: isinstance(text, Comment)):
        comment.extract()

    for tag in soup.find_all(True):
        if tag.decomposed:
            continue  # 앞에서 부모와 함께 지워진 태그
        name = tag.name.lower()
        if name in _DROP_TAGS:
            tag.decompose()
            continue
        if name not in _ALLOWED:
            tag.unwrap()
            continue

        allowed_attrs = _ALLOWED[name]
        for attr in list(tag.attrs):
            if attr.lower() not in allowed_attrs:
                del tag[attr]
                continue
            if attr.lower() in _URL_ATTRS:
                safe = _safe_url(tag[attr], base_url)
                if safe is None:
                    del tag[attr]
                else:
                    tag[attr] = safe

        if name == "img" and not tag.get("src"):
            tag.decompose()

    return str(soup).strip()
