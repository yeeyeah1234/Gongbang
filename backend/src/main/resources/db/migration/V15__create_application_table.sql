-- REC-001: 모집글 지원 (DB 명세서 application). 지원 취소(REC-002)는 행을 지운다.
-- 거절된 지원도 행이 남아 같은 글에 다시 지원할 수 없다 (UQ).
-- TODO(확인 필요): member 테이블(정진우)이 들어오면 새 번호 마이그레이션으로
-- applicant_id → member.id FK를 추가한다. 지금은 인덱스만 둔다.
CREATE TABLE application (
    id           BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    post_id      BIGINT UNSIGNED NOT NULL,
    applicant_id BIGINT UNSIGNED NOT NULL,
    status       VARCHAR(10)     NOT NULL DEFAULT 'PENDING',
    reject_type  VARCHAR(10)     NULL,
    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at DATETIME        NULL,
    CONSTRAINT fk_application_post FOREIGN KEY (post_id) REFERENCES recruit_post (id),
    CONSTRAINT uk_application_post_applicant UNIQUE (post_id, applicant_id),
    INDEX idx_application_post_status_created_at (post_id, status, created_at),
    INDEX idx_application_applicant_id (applicant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
