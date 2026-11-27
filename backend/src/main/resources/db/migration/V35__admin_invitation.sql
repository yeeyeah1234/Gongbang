-- 관리자 초대 수락 후에만 권한을 부여한다. 대기 초대는 회원당 하나.
CREATE TABLE admin_invitation (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    inviter_id BIGINT UNSIGNED NOT NULL,
    invitee_id BIGINT UNSIGNED NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME NOT NULL,
    responded_at DATETIME NULL,
    pending_invitee_id BIGINT UNSIGNED GENERATED ALWAYS AS (CASE WHEN status = 'PENDING' THEN invitee_id ELSE NULL END) STORED,
    UNIQUE KEY uk_admin_invitation_pending (pending_invitee_id),
    KEY idx_admin_invitation_invitee (invitee_id, status),
    CONSTRAINT fk_admin_invitation_inviter FOREIGN KEY (inviter_id) REFERENCES member(id),
    CONSTRAINT fk_admin_invitation_invitee FOREIGN KEY (invitee_id) REFERENCES member(id)
);
