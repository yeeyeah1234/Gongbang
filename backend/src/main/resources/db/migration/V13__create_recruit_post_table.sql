-- PST-002: 팀원 모집글 (DB 명세서 recruit_post).
-- ID는 기존 contest·category와 같이 BIGINT UNSIGNED로 맞춘다 (FK는 양쪽 타입이 같아야 함).
-- TODO(확인 필요): member 테이블(정진우)이 들어오면 새 번호 마이그레이션으로
-- author_id → member.id FK를 추가한다. 지금은 인덱스만 둔다.
CREATE TABLE recruit_post (
    id                    BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    author_id             BIGINT UNSIGNED NOT NULL,
    contest_id            BIGINT UNSIGNED NULL,
    ext_contest_title     VARCHAR(50)     NULL,
    ext_category_id       BIGINT UNSIGNED NULL,
    ext_deadline          DATE            NULL,
    ext_contest_url       VARCHAR(500)    NULL,
    ext_contest_image_url VARCHAR(500)    NULL,
    title                 VARCHAR(50)     NOT NULL,
    content               VARCHAR(2000)   NOT NULL,
    target_count          TINYINT         NOT NULL,
    current_count         TINYINT         NOT NULL DEFAULT 1,
    recruit_start_date    DATE            NOT NULL,
    recruit_end_date      DATE            NOT NULL,
    related_url           VARCHAR(500)    NULL,
    status                VARCHAR(10)     NOT NULL DEFAULT 'RECRUITING',
    completed_at          DATETIME        NULL,
    view_count            INT             NOT NULL DEFAULT 0,
    deleted_at            DATETIME        NULL,
    created_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_recruit_post_contest FOREIGN KEY (contest_id) REFERENCES contest (id),
    CONSTRAINT fk_recruit_post_ext_category FOREIGN KEY (ext_category_id) REFERENCES category (id),
    -- 서비스 공모전(contest_id)과 외부 공모전(ext_*) 중 한쪽만 채운다. ext_contest_url은 선택이라 검사하지 않는다.
    CONSTRAINT ck_recruit_post_contest_source CHECK (
        (contest_id IS NOT NULL
            AND ext_contest_title IS NULL AND ext_category_id IS NULL AND ext_deadline IS NULL)
        OR (contest_id IS NULL
            AND ext_contest_title IS NOT NULL AND ext_category_id IS NOT NULL AND ext_deadline IS NOT NULL)),
    INDEX idx_recruit_post_author_id (author_id),
    INDEX idx_recruit_post_contest_id (contest_id),
    INDEX idx_recruit_post_status_created_at (status, created_at),
    FULLTEXT INDEX ft_recruit_post_title_content (title, content)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
