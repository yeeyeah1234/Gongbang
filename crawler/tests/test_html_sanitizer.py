from html_sanitizer import sanitize_description

BASE = "https://www.wevity.com/"


def test_script와_style_태그는_내용까지_지운다():
    html = '<p>안내</p><script>alert(1)</script><style>p{color:red}</style>'
    assert sanitize_description(html, BASE) == "<p>안내</p>"


def test_이벤트_속성과_style_속성을_지운다():
    html = '<div style="color:red" onclick="alert(1)">내용</div>'
    assert sanitize_description(html, BASE) == "<div>내용</div>"


def test_javascript_주소는_지우고_http_주소는_남긴다():
    html = '<a href="javascript:alert(1)">나쁜 링크</a><a href="https://forms.gle/abc" target="_blank">신청</a>'
    assert sanitize_description(html, BASE) == '<a>나쁜 링크</a><a href="https://forms.gle/abc">신청</a>'


def test_상대_이미지_주소는_위비티_절대_주소로_바꾼다():
    html = '<img src="/upload/contest/a.jpg" align="center">'
    assert sanitize_description(html, BASE) == '<img src="https://www.wevity.com/upload/contest/a.jpg"/>'


def test_주소가_안전하지_않은_이미지는_지운다():
    html = '<p>앞<img src="data:image/png;base64,AAAA" onerror="alert(1)">뒤</p>'
    assert sanitize_description(html, BASE) == "<p>앞뒤</p>"


def test_허용하지_않은_태그는_벗기고_글자는_남긴다():
    html = '<font color="red">빨간 글자</font><iframe src="https://x"></iframe>'
    assert sanitize_description(html, BASE) == "빨간 글자"


def test_주석을_지운다():
    assert sanitize_description("<p>a<!-- <script>x</script> -->b</p>", BASE) == "<p>ab</p>"


def test_빈_값은_그대로_돌려준다():
    assert sanitize_description("", BASE) == ""
    assert sanitize_description(None, BASE) is None
