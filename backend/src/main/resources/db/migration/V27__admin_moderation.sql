-- ADM-03/04: 2026-11-23 사용자 확정 요구사항. 로그인 잠금과 이용 정지를 분리한다.
ALTER TABLE member
    ADD COLUMN warning_count INT NOT NULL DEFAULT 0,
    ADD COLUMN suspended_until DATETIME NULL,
    ADD COLUMN nickname_change_required BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN sanction_reason VARCHAR(200) NULL,
    ADD COLUMN sanctioned_by BIGINT UNSIGNED NULL,
    ADD COLUMN sanctioned_at DATETIME NULL;

ALTER TABLE report
    ADD COLUMN action VARCHAR(30) NULL,
    ADD COLUMN review_note VARCHAR(200) NULL,
    ADD COLUMN reviewer_id BIGINT UNSIGNED NULL,
    ADD COLUMN reviewed_at DATETIME NULL,
    ADD INDEX idx_report_target (target_type, target_id),
    ADD CONSTRAINT fk_report_reviewer FOREIGN KEY (reviewer_id) REFERENCES member(id);
