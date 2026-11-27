ALTER TABLE report ADD COLUMN applied_hidden_at DATETIME NULL, ADD COLUMN hide_provenance_known BOOLEAN NOT NULL DEFAULT FALSE;
CREATE TABLE report_review_cancellation (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    report_id BIGINT NOT NULL,
    previous_action VARCHAR(30) NOT NULL,
    previous_note VARCHAR(200),
    previous_reviewer_id BIGINT,
    previous_reviewed_at DATETIME,
    canceled_by BIGINT NOT NULL,
    cancel_reason VARCHAR(200) NOT NULL,
    canceled_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_report_cancel_report (report_id)
);
