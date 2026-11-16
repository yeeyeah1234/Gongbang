-- WS-01: 팀과 팀원 (DB 명세서 team·team_member). 공통 테이블, 담당 이상현 (팀 합의).
-- ID는 contest·category와 같이 BIGINT UNSIGNED. 팀은 삭제하지 않는다 (AGENTS.md 6).
-- TODO(확인 필요): member는 V9에 이미 있지만 테스트 데이터 준비 부담 때문에 이번엔 FK를 넣지 않는다.
--   recruit_post·member FK는 다음 빈 번호 마이그레이션으로 추가한다
--   (team.post_id → recruit_post.id, team_member.member_id → member.id)
CREATE TABLE team (
    id                BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    post_id           BIGINT UNSIGNED NOT NULL,
    name              VARCHAR(20)     NOT NULL,
    last_activity_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_team_post_id UNIQUE (post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 현재 팀원 = left_at IS NULL. 다시 들어오면 기존 행을 고치지 않고 새 행을 만든다.
-- role: LEADER / MEMBER, color_index: 1~8 (WS-03), leave_type: LEFT / KICKED / WITHDRAWN
CREATE TABLE team_member (
    id           BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    team_id      BIGINT UNSIGNED NOT NULL,
    member_id    BIGINT UNSIGNED NOT NULL,
    role         VARCHAR(10)     NOT NULL DEFAULT 'MEMBER',
    color_index  TINYINT         NOT NULL,
    joined_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    left_at      DATETIME        NULL,
    leave_type   VARCHAR(10)     NULL,
    CONSTRAINT fk_team_member_team FOREIGN KEY (team_id) REFERENCES team (id),
    INDEX idx_team_member_team_id_left_at (team_id, left_at),
    INDEX idx_team_member_member_id (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
