-- NET-02: 신고 접수 (DB 명세서 report). 담당 김건우.
-- member.id 타입(BIGINT UNSIGNED)에 맞춰 FK 타입을 통일한다.
CREATE TABLE report (
    id                 BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    reporter_id        BIGINT UNSIGNED NOT NULL,
    reported_member_id BIGINT UNSIGNED NOT NULL,
    target_type        VARCHAR(10)     NOT NULL,
    target_id          BIGINT UNSIGNED NULL,
    reason_code        VARCHAR(20)     NOT NULL,
    detail             VARCHAR(500)    NULL,
    status             VARCHAR(10)     NOT NULL DEFAULT 'RECEIVED',
    created_at         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_report_reported_member (reported_member_id),
    INDEX idx_report_status (status),
    CONSTRAINT fk_report_reporter FOREIGN KEY (reporter_id) REFERENCES member (id),
    CONSTRAINT fk_report_reported_member FOREIGN KEY (reported_member_id) REFERENCES member (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
