-- MY-07 경력에 관련 링크(회사·동아리 페이지, 결과물 등) 1개 추가 (선택)
ALTER TABLE career
    ADD COLUMN link_url VARCHAR(500) NULL AFTER description;
