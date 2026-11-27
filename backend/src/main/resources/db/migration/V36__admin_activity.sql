CREATE TABLE admin_activity (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
 actor_id BIGINT UNSIGNED NOT NULL,
 action VARCHAR(40) NOT NULL,
 target_type VARCHAR(30) NOT NULL,
 target_id BIGINT UNSIGNED NOT NULL,
 reason VARCHAR(200) NULL,
 created_at DATETIME NOT NULL,
 INDEX idx_admin_activity_time (created_at, id),
 INDEX idx_admin_activity_action_time (action, created_at, id)
);
