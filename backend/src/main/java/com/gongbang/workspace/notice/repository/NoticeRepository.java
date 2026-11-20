package com.gongbang.workspace.notice.repository;

import com.gongbang.workspace.notice.domain.Notice;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    /** NTC-01 목록. 정렬은 부르는 쪽 Pageable의 Sort로 준다 (고정 → 최신순). */
    Page<Notice> findByTeamId(Long teamId, Pageable pageable);

    /** 팀의 고정 공지 (UNIQUE(team_id, pinned_flag)로 많아야 1개). */
    Optional<Notice> findByTeamIdAndPinnedTrue(Long teamId);

    /** 안 읽은 공지 수: 내가 쓴 공지가 아니고 내 읽음 기록이 없는 공지. 작성자가 없는(NULL) 공지는 남의 공지로 센다. */
    @Query(
            "select count(n) from Notice n"
                    + " where n.teamId = :teamId"
                    + " and (n.authorId is null or n.authorId <> :memberId)"
                    + " and not exists (select 1 from NoticeRead r"
                    + " where r.id.noticeId = n.id and r.id.memberId = :memberId)")
    long countUnread(@Param("teamId") Long teamId, @Param("memberId") Long memberId);
}
