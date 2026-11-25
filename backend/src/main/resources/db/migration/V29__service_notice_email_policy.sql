ALTER TABLE service_notice
    ADD COLUMN policy_change BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN email_queued_at DATETIME NULL;
