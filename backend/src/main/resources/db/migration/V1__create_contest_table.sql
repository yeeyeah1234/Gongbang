-- CRW-01: 크롤러가 수집한 공모전을 저장하는 테이블.
-- TODO(확인 필요): DB 명세서에서 category를 별도 테이블(category.code)로 정규화하는지,
-- contest.category_code로 단순 저장하는지 아직 확인하지 못함. 일단 후자로 만듦.
CREATE TABLE contest (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    source_url      VARCHAR(500)    NOT NULL,
    source_site     VARCHAR(50)     NOT NULL,
    title           VARCHAR(255)    NOT NULL,
    host            VARCHAR(255)    NULL,
    category_code   VARCHAR(50)     NOT NULL,
    thumbnail_url   VARCHAR(500)    NULL,
    start_date      DATE            NULL,
    end_date        DATE            NOT NULL,
    description     LONGTEXT        NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_contest_source_url UNIQUE (source_url)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
