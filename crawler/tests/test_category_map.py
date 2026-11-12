from category_map import map_categories_to_code


def test_매핑된_라벨이면_해당_코드를_반환한다():
    assert map_categories_to_code(["기획/아이디어"]) == "PLANNING"
    assert map_categories_to_code(["광고/마케팅"]) == "MARKETING"


def test_첫번째로_걸리는_라벨의_코드를_쓴다():
    assert map_categories_to_code(["대외활동/서포터즈", "기획/아이디어"]) == "ETC"
    assert map_categories_to_code(["기획/아이디어", "광고/마케팅"]) == "PLANNING"


def test_매핑되는_라벨이_없으면_ETC다():
    assert map_categories_to_code(["생소한분야"]) == "ETC"
    assert map_categories_to_code([]) == "ETC"
