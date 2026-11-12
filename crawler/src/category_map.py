# CRW-01 ③: 위비티 분야 라벨을 우리 category.code로 매핑한다.
# 위비티가 적은 순서대로 보고, 아래 그룹 중 하나에 처음 걸리는 라벨을 쓴다.
# 하나도 안 걸리면 ETC.

_LABEL_TO_CODE = {
    "웹/모바일/IT": "IT_SW",
    "웹/모바일/IT(플래시)": "IT_SW",
    "게임/소프트웨어": "IT_SW",
    "과학/공학": "IT_SW",
    "기획/아이디어": "PLANNING",
    "네이밍/슬로건": "PLANNING",
    "논문/리포트": "PLANNING",
    "취업/창업": "PLANNING",
    "디자인/캐릭터/웹툰": "DESIGN_ART",
    "영상/UCC/사진": "DESIGN_ART",
    "예체능/미술/음악": "DESIGN_ART",
    "문학/글/시나리오": "DESIGN_ART",
    "건축/건설/인테리어": "DESIGN_ART",
    "광고/마케팅": "MARKETING",
    "대외활동/서포터즈": "ETC",
    "해외": "ETC",
    "기타": "ETC",
    "그 밖의 분야": "ETC",
}

DEFAULT_CODE = "ETC"


def map_categories_to_code(wevity_labels):
    for label in wevity_labels:
        code = _LABEL_TO_CODE.get(label.strip())
        if code:
            return code
    return DEFAULT_CODE
