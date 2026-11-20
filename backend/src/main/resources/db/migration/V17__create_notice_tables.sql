-- NTC-01~05: 팀 공지와 읽음 기록 (DB 명세서 notice·notice_read). 담당 이상현.
-- 명세서의 room_id 대신 team_id: 워크스페이스 자원은 팀 기준(/api/teams/{teamId}/notices)이다 (CAL-06과 같음).
-- 삭제는 행을 지운다 (notice_read는 CASCADE).
-- TODO(확인 필요): 완전 삭제 테이블 여부를 AGENTS.md 6(Gongbang-archive 저장소)과 대조하지 못했다.
--
-- 팀당 고정 공지 1개는 DB가 막는다: pinned_flag(고정이면 1, 아니면 NULL) + UNIQUE(team_id, pinned_flag).
-- NULL은 서로 다른 값으로 취급되어 고정 안 된 공지는 여러 개, 고정 공지는 팀마다 1개.
-- 생성 열 식에 team_id를 쓰지 않는 이유: MySQL은 STORED 생성 열의 기반 열에 걸린 FK에 CASCADE·SET NULL을 허용하지 않는다
-- (fk_notice_team의 ON DELETE CASCADE를 유지하려고).
-- edited_at: 제목·내용이 바뀔 때만 앱이 채운다 (고정·해제로는 바뀌지 않음). 「(수정됨)」 표시용.
-- author_id NULL 허용 + SET NULL: calendar_event.created_by와 같은 방식 (member 행은 지우지 않으므로 운영 영향은 없음).
CREATE TABLE notice (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    team_id     BIGINT UNSIGNED NOT NULL,
    author_id   BIGINT UNSIGNED NULL,
    title       VARCHAR(50)     NOT NULL,
    content     VARCHAR(2000)   NOT NULL,
    is_pinned   BOOLEAN         NOT NULL DEFAULT FALSE,
    pinned_flag TINYINT AS (IF(is_pinned, 1, NULL)) STORED,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    edited_at   DATETIME        NULL,
    CONSTRAINT uk_notice_team_id_pinned_flag UNIQUE (team_id, pinned_flag),
    INDEX idx_notice_team_id_created_at (team_id, created_at),
    INDEX idx_notice_author_id (author_id),
    CONSTRAINT fk_notice_team FOREIGN KEY (team_id) REFERENCES team (id) ON DELETE CASCADE,
    CONSTRAINT fk_notice_author FOREIGN KEY (author_id) REFERENCES member (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- NTC-02 읽음 기록. 처음 읽은 시각만 남긴다 (다시 열어도 read_at은 그대로).
CREATE TABLE notice_read (
    notice_id BIGINT UNSIGNED NOT NULL,
    member_id BIGINT UNSIGNED NOT NULL,
    read_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (notice_id, member_id),
    INDEX idx_notice_read_member_id (member_id),
    CONSTRAINT fk_notice_read_notice FOREIGN KEY (notice_id) REFERENCES notice (id) ON DELETE CASCADE,
    CONSTRAINT fk_notice_read_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
