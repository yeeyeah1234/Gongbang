-- CAL-06·CAL-03: 팀 캘린더 일정 (DB 명세서 calendar_event). 완전 삭제하는 테이블 (AGENTS.md 6).
-- 명세서의 room_id·is_auto 대신 team_id·auto_type: 워크스페이스 자원은 팀 기준(/api/teams/{teamId}/events)이고,
-- is_auto(BOOLEAN)로는 UNIQUE로 「팀당 자동 일정 1개」를 막을 수 없어 auto_type(NULL = 일반 일정)으로 바꿨다.
-- UNIQUE(team_id, auto_type): NULL은 서로 다른 값으로 취급되어 일반 일정은 여러 개, 자동 일정은 종류마다 1개.
-- team FK ON DELETE CASCADE: 팀은 지우지 않으므로 운영 영향은 없고, 테스트 정리(DELETE FROM team)가 FK에 막히지 않게 한다.
-- TODO(확인 필요): all_day 기본 FALSE, color 기본 'BLUE'(STYLE_GUIDE 1-3)는 DB 명세서 기본값과 대조하지 못했다.
CREATE TABLE calendar_event (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    team_id     BIGINT UNSIGNED NOT NULL,
    title       VARCHAR(30)     NOT NULL,
    start_at    DATETIME        NOT NULL,
    end_at      DATETIME        NOT NULL,
    all_day     BOOLEAN         NOT NULL DEFAULT FALSE,
    color       VARCHAR(10)     NOT NULL DEFAULT 'BLUE',
    memo        VARCHAR(200)    NULL,
    -- 자동 일정은 NULL
    created_by  BIGINT UNSIGNED NULL,
    -- CONTEST_DEADLINE: 공모전 접수 마감 자동 일정 (CAL-06). 일반 일정은 NULL
    auto_type   VARCHAR(30)     NULL,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_calendar_event_team_id_auto_type UNIQUE (team_id, auto_type),
    INDEX idx_calendar_event_team_id_start_at (team_id, start_at),
    CONSTRAINT fk_calendar_event_team FOREIGN KEY (team_id) REFERENCES team (id) ON DELETE CASCADE,
    CONSTRAINT fk_calendar_event_created_by FOREIGN KEY (created_by) REFERENCES member (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
