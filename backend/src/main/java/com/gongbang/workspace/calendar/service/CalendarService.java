package com.gongbang.workspace.calendar.service;

import com.gongbang.workspace.calendar.domain.CalendarEvent;
import com.gongbang.workspace.calendar.domain.CalendarEventAutoType;
import com.gongbang.workspace.calendar.repository.CalendarEventRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 팀 캘린더 (CAL-03, CAL-06). 다른 도메인(지원 수락 등)은 repository 대신 이 service를 부른다.
 *
 * <p>CAL-06 공모전 마감 자동 일정: 제목 「공모전 접수 마감」, 하루 종일, 시작·끝 = 마감일 00:00, 색 RED, 작성자 없음. 마감일은 SQL 안에서 계산한다
 * (계산을 한 곳으로: {@link CalendarEventRepository#upsertContestDeadlineForTeam}).
 *
 * <p>TODO(확인 필요): 배치가 마감일을 바꾸는 바로 그 순간 같은 공모전 모집글에서 첫 수락이 진행 중이면, 수락 쪽은 옛 마감일을 읽고 리스너는 아직 커밋되지 않은 새
 * 팀을 보지 못해 옛 날짜가 남을 수 있다. 수락 쪽에서 contest 행을 FOR SHARE로 잠그면 막을 수 있지만 배치가 길면 수락이 잠금 대기로 실패할 수 있어 넣지
 * 않았다.
 */
@Service
@RequiredArgsConstructor
public class CalendarService {

    static final String AUTO_DEADLINE_TITLE = "공모전 접수 마감";

    /** 자동 일정 전용 색 (화면은 STYLE_GUIDE danger). 일반 일정 5색(BLUE·GREEN·YELLOW·PURPLE·GRAY)과 따로 둔다. */
    static final String AUTO_DEADLINE_COLOR = "RED";

    private final CalendarEventRepository calendarEventRepository;

    @Transactional(readOnly = true)
    public Optional<CalendarEvent> findForReminder(Long eventId) {
        return calendarEventRepository.findById(eventId);
    }

    /**
     * CAL-06: 팀의 공모전 마감 자동 일정을 만들거나 날짜를 고친다. 모집글이나 마감일이 없으면 아무것도 하지 않는다.
     *
     * <p>부르는 쪽 트랜잭션에 합류한다. 지원 수락(REC-004, READ COMMITTED) 안에서 팀을 만든 직후 부르며, 같은 트랜잭션에서 넣은 team 행이
     * 보인다. 정상 데이터에서는 예외가 나지 않는다 (SQL 한 문장, 다른 행을 잠그지 않음).
     */
    @Transactional
    public void upsertAutoEvent(Long teamId) {
        calendarEventRepository.upsertContestDeadlineForTeam(
                teamId,
                AUTO_DEADLINE_TITLE,
                AUTO_DEADLINE_COLOR,
                CalendarEventAutoType.CONTEST_DEADLINE.name());
    }

    /**
     * CAL-06, CRW-01 ⑦: 그 공모전을 쓰는 모집글들의 팀 전체의 자동 일정을 맞춘다. 새 마감일은 DB에서 다시 읽는다.
     *
     * <p>항상 새 트랜잭션으로 돈다. 배치 커밋 뒤(AFTER_COMMIT) {@link ContestDeadlineChangedListener}가 부르는데, 그때는 끝난
     * 배치 트랜잭션에 합류하면 저장되지 않기 때문이다. 경계를 리스너가 아니라 여기에 두어, 실패해도 이 트랜잭션만 롤백되고 예외는 리스너의 catch로 간다.
     *
     * <p>READ COMMITTED: INSERT … SELECT의 SELECT 부분이 recruit_post·team·contest 행에 공유 잠금을 걸지 않아, 진행
     * 중인 지원 수락(recruit_post FOR UPDATE)을 막거나 기다리지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void upsertAutoEventsByContest(Long contestId) {
        syncContestMilestones(contestId);
    }

    @Transactional
    public void syncContestMilestones(Long contestId) {
        calendarEventRepository.upsertContestDeadlineForContest(
                contestId,
                AUTO_DEADLINE_TITLE,
                AUTO_DEADLINE_COLOR,
                CalendarEventAutoType.CONTEST_DEADLINE.name());
    }

    @Transactional(readOnly = true)
    public List<CalendarEvent> contestDeadlines() {
        return calendarEventRepository.findByAutoType(CalendarEventAutoType.CONTEST_DEADLINE);
    }
}
