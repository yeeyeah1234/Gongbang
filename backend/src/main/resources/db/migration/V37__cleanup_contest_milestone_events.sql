-- CAL-06 자동 일정을 「공모전 접수 마감」(CONTEST_DEADLINE) 1개로 되돌린다.
-- 기존에 D-7·D-1 알림용으로 생성된 CONTEST_D7·CONTEST_D1 자동 일정과
-- 옛 target_type('MILESTONE')으로 대기 중인 예약 알림을 정리한다.
-- 앞으로 D-7·D-1 알림은 CONTEST_DEADLINE 마감일에서 계산해 MILESTONE_D7·MILESTONE_D1로 예약한다.
UPDATE scheduled_notification
SET status = 'CANCELED'
WHERE target_type = 'MILESTONE' AND status = 'PENDING';

DELETE FROM calendar_event
WHERE auto_type IN ('CONTEST_D7', 'CONTEST_D1');
