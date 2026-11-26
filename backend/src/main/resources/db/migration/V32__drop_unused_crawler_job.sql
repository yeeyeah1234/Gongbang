-- Administrator manual recrawling was removed. Regular contest batch ingestion is unchanged.
-- V28 remains immutable; this migration permanently removes the unused job history.
DROP TABLE IF EXISTS crawler_job;
