-- BRD 커뮤니티 게시판 (2차, 이슈 #88, DB 명세서 board_post·board_post_image·board_post_view·board_post_like).
-- 팀원 모집글(recruit_post)과 별개다. 삭제는 소프트 삭제(deleted_at).
-- TODO(확인 필요): member 테이블(정진우)에 맞춰 author_id·member_id → member.id FK를 함께 둘지 정한다.
--                 지금은 다른 테이블(recruit_post 등)과 같이 인덱스만 둔다.
CREATE TABLE board_post (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    author_id   BIGINT UNSIGNED NOT NULL,
    category    VARCHAR(10)     NOT NULL,
    contest_id  BIGINT UNSIGNED NULL,
    title       VARCHAR(50)     NOT NULL,
    content     VARCHAR(2000)   NOT NULL,
    link_url    VARCHAR(500)    NULL,
    view_count  INT             NOT NULL DEFAULT 0,
    like_count  INT             NOT NULL DEFAULT 0,
    deleted_at  DATETIME        NULL,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_board_post_contest FOREIGN KEY (contest_id) REFERENCES contest (id),
    INDEX idx_board_post_author_id (author_id),
    INDEX idx_board_post_deleted_created (deleted_at, created_at),
    INDEX idx_board_post_category_deleted_created (category, deleted_at, created_at),
    INDEX idx_board_post_contest_deleted_created (contest_id, deleted_at, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- BRD-006 결과물 자랑 사진 (최대 3장, 소프트 삭제). recruit_post_image와 같은 모양
CREATE TABLE board_post_image (
    id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    board_post_id BIGINT UNSIGNED NOT NULL,
    image_url     VARCHAR(500)    NOT NULL,
    sort_order    TINYINT         NOT NULL,
    deleted_at    DATETIME        NULL,
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_board_post_image_post FOREIGN KEY (board_post_id) REFERENCES board_post (id),
    INDEX idx_board_post_image_post_deleted_sort (board_post_id, deleted_at, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- BRD-004 ⑤ 조회 기록 (같은 회원 24시간 안 재조회 제외). post_view와 같은 모양
CREATE TABLE board_post_view (
    board_post_id  BIGINT UNSIGNED NOT NULL,
    member_id      BIGINT UNSIGNED NOT NULL,
    last_viewed_at DATETIME        NOT NULL,
    PRIMARY KEY (board_post_id, member_id),
    CONSTRAINT fk_board_post_view_post FOREIGN KEY (board_post_id) REFERENCES board_post (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- BRD-007 좋아요 (한 회원은 한 글에 한 번, 취소는 행 삭제). 추가·삭제 때 board_post.like_count를 함께 바꾼다
CREATE TABLE board_post_like (
    board_post_id BIGINT UNSIGNED NOT NULL,
    member_id     BIGINT UNSIGNED NOT NULL,
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (board_post_id, member_id),
    CONSTRAINT fk_board_post_like_post FOREIGN KEY (board_post_id) REFERENCES board_post (id),
    INDEX idx_board_post_like_member_id (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
