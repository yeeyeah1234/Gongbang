-- NET-02 · INT-005 팀 초대 테이블 (DB 명세서 team_invitation)
CREATE TABLE team_invitation (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    post_id BIGINT UNSIGNED NOT NULL,
    inviter_id BIGINT UNSIGNED NOT NULL,
    invitee_id BIGINT UNSIGNED NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    responded_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_team_invitation_post FOREIGN KEY (post_id) REFERENCES recruit_post (id),
    CONSTRAINT fk_team_invitation_inviter FOREIGN KEY (inviter_id) REFERENCES member (id),
    CONSTRAINT fk_team_invitation_invitee FOREIGN KEY (invitee_id) REFERENCES member (id),
    CONSTRAINT uq_team_invitation_post_invitee UNIQUE (post_id, invitee_id),
    INDEX idx_team_invitation_invitee_status (invitee_id, status),
    INDEX idx_team_invitation_post_status (post_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
