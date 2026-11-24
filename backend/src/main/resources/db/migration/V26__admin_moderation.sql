-- ADM-03/04: 2026-11-23 사용자 확정 요구사항. 로그인 잠금과 이용 정지를 분리한다.
ALTER TABLE member
    ADD COLUMN warning_count INT NOT NULL DEFAULT 0,
    ADD COLUMN suspended_until DATETIME NULL,
    ADD COLUMN nickname_change_required BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN sanction_reason VARCHAR(200) NULL,
    ADD COLUMN sanctioned_by BIGINT UNSIGNED NULL,
    ADD COLUMN sanctioned_at DATETIME NULL;

CREATE TABLE comment (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    post_id BIGINT UNSIGNED NOT NULL,
    author_id BIGINT UNSIGNED NOT NULL,
    content VARCHAR(2000) NOT NULL,
    deleted_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_comment_post (post_id),
    FOREIGN KEY (post_id) REFERENCES recruit_post(id),
    FOREIGN KEY (author_id) REFERENCES member(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE report
    ADD COLUMN action VARCHAR(30) NULL,
    ADD COLUMN review_note VARCHAR(200) NULL,
    ADD COLUMN reviewer_id BIGINT UNSIGNED NULL,
    ADD COLUMN reviewed_at DATETIME NULL,
    ADD INDEX idx_report_target (target_type, target_id),
    ADD CONSTRAINT fk_report_reviewer FOREIGN KEY (reviewer_id) REFERENCES member(id);
