-- NET-05 등 공통 파일 업로드(DB 명세서 uploaded_file) 테이블 생성 및 chat_message.file_id FK 연결.
CREATE TABLE uploaded_file (
    id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    uploader_id   BIGINT UNSIGNED NOT NULL,
    purpose       VARCHAR(20)     NOT NULL,
    storage_key   VARCHAR(300)    NOT NULL,
    original_name VARCHAR(255)    NOT NULL,
    content_type  VARCHAR(100)    NOT NULL,
    size_bytes    BIGINT          NOT NULL,
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_uploaded_file_storage_key UNIQUE (storage_key),
    INDEX idx_uploaded_file_uploader_id (uploader_id),
    CONSTRAINT fk_uploaded_file_uploader FOREIGN KEY (uploader_id) REFERENCES member (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE chat_message
    ADD CONSTRAINT fk_chat_message_file FOREIGN KEY (file_id) REFERENCES uploaded_file (id);
