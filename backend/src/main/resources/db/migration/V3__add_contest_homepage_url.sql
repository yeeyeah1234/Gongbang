-- MAIN-02 ④ [바로가기]: 위비티 대신 주최 측 원래 공모전 페이지로 보내기 위한 주소 (DB 명세서 contest.homepage_url, 11/13 추가).
-- 위비티 상세의 「홈페이지」 칸을 수집한다 (CRW-01 ②). 없으면 NULL.
ALTER TABLE contest
    ADD COLUMN homepage_url VARCHAR(500) NULL AFTER source_site;
