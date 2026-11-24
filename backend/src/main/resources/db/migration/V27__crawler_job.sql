-- ADM-06 수동 재수집 요청. Python worker가 내부 키로 요청을 가져가 실행한다.
CREATE TABLE crawler_job (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    requested_by BIGINT UNSIGNED NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    active_slot TINYINT GENERATED ALWAYS AS (IF(status IN ('PENDING','RUNNING'),1,NULL)) STORED,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at DATETIME NULL,
    finished_at DATETIME NULL,
    CONSTRAINT uq_crawler_active UNIQUE(active_slot),
    FOREIGN KEY(requested_by) REFERENCES member(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
