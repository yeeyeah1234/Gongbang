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

# CRW-01 ⑩: 우리 분야마다 모자라면 더 볼 위비티 분야 필터 목록 번호(cidx).
# 번호는 위비티 공모전 목록 페이지의 분야 링크(?c=find&s=1&gub=1&cidx=N)에서 확인했다 (11/17).
# 위비티 필터 이름과 목록에 붙는 라벨이 조금 다르다 (필터 「웹/모바일/플래시」 → 라벨 「웹/모바일/IT」).
CODE_TO_WEVITY_CIDX = {
    "IT_SW": [20, 21, 22],  # 웹/모바일/플래시, 게임/소프트웨어, 과학/공학
    "PLANNING": [1, 25, 3, 88],  # 기획/아이디어, 네이밍/슬로건, 논문/리포트, 취업/창업
    "DESIGN_ART": [19, 10, 26, 23, 24],  # 디자인/캐릭터/웹툰, 영상/UCC/사진, 예체능/미술/음악, 문학/글/시나리오, 건축/건설/인테리어
    "MARKETING": [2],  # 광고/마케팅
    "ETC": [27, 28, 29],  # 대외활동/서포터즈, 해외, 기타
}


def map_categories_to_code(wevity_labels):
    for label in wevity_labels:
        code = _LABEL_TO_CODE.get(label.strip())
        if code:
            return code
    return DEFAULT_CODE
