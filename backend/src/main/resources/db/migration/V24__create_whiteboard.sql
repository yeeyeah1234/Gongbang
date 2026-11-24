-- WB-01·WB-10: 팀 화이트보드 (DB 명세서 whiteboard). 소프트 삭제(deleted_at) 테이블 (AGENTS.md 6).
-- content는 Yjs 상태 바이너리. 동기화 서버가 /internal API로만 읽고 쓴다. 목록 조회에서는 읽지 않는다 (AGENTS.md 6).
-- 컬럼 이름은 DB 명세서 그대로: creator_id(만든 회원), last_editor_id(동기화 서버가 저장할 때 채움, NULL 허용).
-- 번호: develop 최대 V23(uploaded_file), 다른 원격 브랜치에 V24 이상 없음 (2026-11-24 확인).
-- team FK ON DELETE CASCADE: 팀은 지우지 않으므로 운영 영향은 없고, 테스트 정리(DELETE FROM team)가 FK에 막히지 않게 한다 (V22와 같음).
CREATE TABLE whiteboard (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    team_id         BIGINT UNSIGNED NOT NULL,
    title           VARCHAR(50)     NOT NULL DEFAULT '제목 없는 보드',
    content         LONGBLOB        NULL,
    creator_id      BIGINT UNSIGNED NOT NULL,
    last_editor_id  BIGINT UNSIGNED NULL,
    deleted_at      DATETIME        NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_whiteboard_team_id_deleted_at_updated_at (team_id, deleted_at, updated_at),
    CONSTRAINT fk_whiteboard_team FOREIGN KEY (team_id) REFERENCES team (id) ON DELETE CASCADE,
    CONSTRAINT fk_whiteboard_creator FOREIGN KEY (creator_id) REFERENCES member (id),
    CONSTRAINT fk_whiteboard_last_editor FOREIGN KEY (last_editor_id) REFERENCES member (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
