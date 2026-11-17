-- PST-005 ⑥: 같은 회원이 24시간 안에 다시 열면 조회수를 올리지 않기 위한 기록 (DB 명세서 post_view).
-- 비회원은 기록하지 않고 조회수도 올리지 않는다 (11/10 결정).
-- TODO(확인 필요): member 테이블(정진우)이 들어오면 새 번호 마이그레이션으로
-- member_id → member.id FK를 추가한다. 지금은 recruit_post FK만 둔다.
CREATE TABLE post_view (
    post_id        BIGINT UNSIGNED NOT NULL,
    member_id      BIGINT UNSIGNED NOT NULL,
    last_viewed_at DATETIME        NOT NULL,
    PRIMARY KEY (post_id, member_id),
    CONSTRAINT fk_post_view_post FOREIGN KEY (post_id) REFERENCES recruit_post (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
