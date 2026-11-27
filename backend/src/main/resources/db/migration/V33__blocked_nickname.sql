CREATE TABLE blocked_nickname (
    nickname VARCHAR(10) NOT NULL PRIMARY KEY,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

INSERT INTO blocked_nickname (nickname)
SELECT DISTINCT nickname FROM member WHERE nickname_change_required = TRUE AND nickname IS NOT NULL;
