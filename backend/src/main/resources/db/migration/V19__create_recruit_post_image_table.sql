-- PST-007: 외부 공모전 모집글에 올리는 공모전 사진 (DB 명세서 recruit_post_image, 이슈 #87).
-- 처음에는 V18로 올렸으나 같은 날 병합된 V18__create_chat_tables(#99)와 번호가 겹쳐 V19로 옮겼다.
-- 테스트 서버에는 V18 채팅 테이블만 적용됐고, 이 파일은 V18이 겹쳐 배포가 실패해 적용되지 않았다 (2026-11-23).
-- 삭제되지 않은 사진은 글당 최대 3장이고, 개수는 모집글 행 잠금(SELECT … FOR UPDATE) 안에서 센다.
-- 삭제는 소프트 삭제(deleted_at)이고 파일은 지우지 않는다. 모집글이 삭제돼도 그대로 둔다 (PST-004 ④).
CREATE TABLE recruit_post_image (
    id         BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    post_id    BIGINT UNSIGNED NOT NULL,
    image_url  VARCHAR(500)    NOT NULL,
    sort_order TINYINT         NOT NULL,
    deleted_at DATETIME        NULL,
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_recruit_post_image_post FOREIGN KEY (post_id) REFERENCES recruit_post (id),
    INDEX idx_recruit_post_image_post_deleted_sort (post_id, deleted_at, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
