-- WS-08 ⑨ 팀 삭제(소프트 삭제): 팀장 혼자 남은 팀 정리용 (11/26 팀 결정)
ALTER TABLE team
    ADD COLUMN deleted_at DATETIME NULL AFTER created_at;
