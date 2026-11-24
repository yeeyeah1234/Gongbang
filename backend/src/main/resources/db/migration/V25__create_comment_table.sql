-- NET-01, INT-004, BRD-005: 모집글·커뮤니티 게시글 통합 댓글·대댓글(1단계) 테이블. 담당 김건우.
-- 이슈 #88 제안 반영: post_id(모집글)와 board_post_id(커뮤니티 게시글) 중 정확히 하나만 채운다.
-- 원격 WB-01 브랜치가 V24를 사용 중이므로 V25로 생성한다.
CREATE TABLE comment (
    id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    post_id       BIGINT UNSIGNED NULL,
    board_post_id BIGINT UNSIGNED NULL,
    author_id     BIGINT UNSIGNED NOT NULL,
    parent_id     BIGINT UNSIGNED NULL,
    content       VARCHAR(500)    NOT NULL,
    is_secret     BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_at    DATETIME        NULL,
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_comment_target CHECK (
        (post_id IS NOT NULL AND board_post_id IS NULL)
        OR (post_id IS NULL AND board_post_id IS NOT NULL)
    ),
    INDEX idx_comment_post_created (post_id, created_at),
    INDEX idx_comment_board_post_created (board_post_id, created_at),
    INDEX idx_comment_author_id (author_id),
    INDEX idx_comment_parent_id (parent_id),
    CONSTRAINT fk_comment_post FOREIGN KEY (post_id) REFERENCES recruit_post (id),
    CONSTRAINT fk_comment_board_post FOREIGN KEY (board_post_id) REFERENCES board_post (id),
    CONSTRAINT fk_comment_author FOREIGN KEY (author_id) REFERENCES member (id),
    CONSTRAINT fk_comment_parent FOREIGN KEY (parent_id) REFERENCES comment (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
