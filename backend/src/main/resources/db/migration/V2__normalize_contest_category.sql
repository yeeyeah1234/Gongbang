-- DB 명세서 확인 후 수정 (V1에서 category를 문자열로 잘못 저장했음).
-- V1은 이미 머지되어 있어 수정하지 않고 새 번호로 만든다 (AGENTS.md 4번).
CREATE TABLE category (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    code        VARCHAR(30) NOT NULL,
    name        VARCHAR(20) NOT NULL,
    sort_order  INT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_category_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO category (code, name, sort_order) VALUES
    ('IT_SW', 'IT/소프트웨어', 1),
    ('PLANNING', '기획/아이디어', 2),
    ('DESIGN_ART', '디자인/예술', 3),
    ('MARKETING', '마케팅', 4),
    ('ETC', '기타', 5);

ALTER TABLE contest
    MODIFY title VARCHAR(200) NOT NULL,
    MODIFY host VARCHAR(100) NULL,
    MODIFY source_site VARCHAR(30) NOT NULL,
    MODIFY description TEXT NULL,
    ADD COLUMN category_id BIGINT UNSIGNED NOT NULL AFTER title,
    ADD COLUMN view_count INT NOT NULL DEFAULT 0 AFTER description,
    DROP COLUMN category_code,
    ADD CONSTRAINT fk_contest_category FOREIGN KEY (category_id) REFERENCES category (id),
    ADD INDEX idx_contest_category_id (category_id),
    ADD INDEX idx_contest_end_date (end_date),
    ADD INDEX idx_contest_view_count (view_count),
    ADD FULLTEXT INDEX ft_contest_title_host (title, host);
