package com.gongbang.member.repository;

import com.gongbang.member.domain.Bookmark;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookmarkRepository extends JpaRepository<Bookmark, Bookmark.Key> {

    long countByMemberId(Long memberId);

    void deleteByMemberId(Long memberId);

    @Query(value = "select count(*) from contest where id = :contestId", nativeQuery = true)
    long countContest(@Param("contestId") Long contestId);

    /**
     * MY-03 목록. 정렬은 ①마감 안 된 것 먼저(마감된 공모전은 뒤로), ②고른 기준. sort: deadline(마감 임박순) / bookmarked(북마크한 순)
     * / latest(최신순 = 공모전 등록 최신)
     */
    @Query(
            value =
                    "select b.contest_id from bookmark b join contest c on c.id = b.contest_id"
                            + " where b.member_id = :memberId"
                            + " order by (c.end_date < :today),"
                            + " case when :sort = 'deadline' then c.end_date end asc,"
                            + " case when :sort = 'bookmarked' then b.created_at end desc,"
                            + " case when :sort = 'latest' then c.created_at end desc,"
                            + " b.contest_id desc"
                            + " limit :size offset :offset",
            nativeQuery = true)
    List<Long> findContestIds(
            @Param("memberId") Long memberId,
            @Param("today") LocalDate today,
            @Param("sort") String sort,
            @Param("size") int size,
            @Param("offset") int offset);

    /** NOTI-05 ①: 북마크한 공모전 마감 D-1 (내일 마감) 회원·공모전 쌍 */
    @Query(
            value =
                    "select b.member_id, c.id, c.title from bookmark b join contest c on c.id = b.contest_id"
                            + " join member m on m.id = b.member_id and m.status = 'ACTIVE'"
                            + " where c.end_date = :deadline",
            nativeQuery = true)
    List<Object[]> findBookmarksByDeadline(@Param("deadline") LocalDate deadline);
}
