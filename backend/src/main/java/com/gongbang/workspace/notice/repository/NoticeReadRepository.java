package com.gongbang.workspace.notice.repository;

import com.gongbang.workspace.notice.domain.NoticeRead;
import com.gongbang.workspace.notice.domain.NoticeReadId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NoticeReadRepository extends JpaRepository<NoticeRead, NoticeReadId> {

    /**
     * NTC-02: 읽음 기록을 남긴다. 이미 있으면 그대로 둔다 (read_at = 처음 읽은 시각).
     *
     * <p>「조회 후 insert」가 아니라 PK에 기대는 한 문장이라 두 번·동시에 불려도 에러 없이 1행만 남는다. INSERT IGNORE는 FK 오류까지 경고로 바꿔
     * 버려 쓰지 않는다.
     */
    @Modifying
    @Query(
            value =
                    "INSERT INTO notice_read (notice_id, member_id) VALUES (:noticeId, :memberId)"
                            + " ON DUPLICATE KEY UPDATE read_at = read_at",
            nativeQuery = true)
    int markRead(@Param("noticeId") Long noticeId, @Param("memberId") Long memberId);

    /** 주어진 공지 중 내가 읽은 공지 ID. */
    @Query(
            "select r.id.noticeId from NoticeRead r"
                    + " where r.id.memberId = :memberId and r.id.noticeId in :noticeIds")
    List<Long> findReadNoticeIds(
            @Param("memberId") Long memberId, @Param("noticeIds") Collection<Long> noticeIds);
}
