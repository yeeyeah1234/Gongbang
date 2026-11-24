-- DOC-01~04·DOC-10: 팀 문서 (DB 명세서 document). 소프트 삭제(deleted_at) 테이블 (AGENTS.md 6).
-- content는 Yjs 문서 상태 바이너리. 동기화 서버가 /internal API로만 읽고 쓴다. 목록 조회에서는 읽지 않는다 (AGENTS.md 6).
-- 번호: develop 최대 V20, V21은 NET-02(report) 브랜치가 쓰고 있어 V22.
-- team FK ON DELETE CASCADE: 팀은 지우지 않으므로 운영 영향은 없고, 테스트 정리(DELETE FROM team)가 FK에 막히지 않게 한다 (V16과 같음).
-- TODO(확인 필요): created_by·updated_by NOT NULL(회원 행은 지우지 않음, AGENTS.md 6)은 DB 명세서와 대조하지 못했다.
CREATE TABLE document (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    team_id     BIGINT UNSIGNED NOT NULL,
    title       VARCHAR(50)     NOT NULL DEFAULT '제목 없는 문서',
    content     LONGBLOB        NULL,
    created_by  BIGINT UNSIGNED NOT NULL,
    -- 마지막으로 본문을 저장했거나 이름을 바꾼 회원. 만들 때는 created_by와 같다
    updated_by  BIGINT UNSIGNED NOT NULL,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at  DATETIME        NULL,
    INDEX idx_document_team_id_deleted_at_updated_at (team_id, deleted_at, updated_at),
    CONSTRAINT fk_document_team FOREIGN KEY (team_id) REFERENCES team (id) ON DELETE CASCADE,
    CONSTRAINT fk_document_created_by FOREIGN KEY (created_by) REFERENCES member (id),
    CONSTRAINT fk_document_updated_by FOREIGN KEY (updated_by) REFERENCES member (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
