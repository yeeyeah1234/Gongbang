-- NET-04, NET-05, NET-06: 채팅방·참여자·메시지 (DB 명세서 chat_room, chat_room_member, chat_message). 담당 김건우.
-- TODO(확인 필요): uploaded_file(공통, 담당 미정) 테이블이 아직 없어 chat_message.file_id FK는 제외했다.
--   uploaded_file 테이블 추가 후 ALTER TABLE 마이그레이션으로 FK를 추가한다.

CREATE TABLE chat_room (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    team_id         BIGINT UNSIGNED NOT NULL,
    type            VARCHAR(10)     NOT NULL,
    room_key        VARCHAR(60)     NOT NULL,
    last_message_at DATETIME        NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_chat_room_room_key UNIQUE (room_key),
    INDEX idx_chat_room_team_id (team_id),
    INDEX idx_chat_room_last_message_at (last_message_at),
    CONSTRAINT fk_chat_room_team FOREIGN KEY (team_id) REFERENCES team (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE chat_room_member (
    chat_room_id         BIGINT UNSIGNED NOT NULL,
    member_id            BIGINT UNSIGNED NOT NULL,
    last_read_message_id BIGINT UNSIGNED NULL,
    joined_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (chat_room_id, member_id),
    INDEX idx_chat_room_member_member_id (member_id),
    CONSTRAINT fk_chat_room_member_chat_room FOREIGN KEY (chat_room_id) REFERENCES chat_room (id),
    CONSTRAINT fk_chat_room_member_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE chat_message (
    id           BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    chat_room_id BIGINT UNSIGNED NOT NULL,
    sender_id    BIGINT UNSIGNED NULL,
    type         VARCHAR(10)     NOT NULL DEFAULT 'TEXT',
    content      VARCHAR(2000)   NULL,
    file_id      BIGINT UNSIGNED NULL,
    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_chat_message_chat_room_id_id (chat_room_id, id),
    CONSTRAINT fk_chat_message_chat_room FOREIGN KEY (chat_room_id) REFERENCES chat_room (id),
    CONSTRAINT fk_chat_message_sender FOREIGN KEY (sender_id) REFERENCES member (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
