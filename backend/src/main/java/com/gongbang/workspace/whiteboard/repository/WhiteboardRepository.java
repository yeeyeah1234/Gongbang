package com.gongbang.workspace.whiteboard.repository;

import com.gongbang.workspace.whiteboard.domain.Whiteboard;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WhiteboardRepository extends JpaRepository<Whiteboard, Long> {

    /** 삭제되지 않은 보드. 없거나 삭제됐으면 빈 값 (둘 다 404). */
    Optional<Whiteboard> findByIdAndDeletedAtIsNull(Long id);

    /** WB-01 ③ 30개 제한: 팀의 삭제되지 않은 보드 수. 팀 행을 잠근 트랜잭션 안에서 부른다 (AGENTS.md 7). */
    long countByTeamIdAndDeletedAtIsNull(Long teamId);

    /**
     * WB-01 목록: 삭제되지 않은 보드를 updated_at 최신순(같으면 ID 역순)으로. 본문은 SELECT하지 않는다 (AGENTS.md 6).
     * idx(team_id, deleted_at, updated_at)를 탄다.
     */
    @Query(
            "select new com.gongbang.workspace.whiteboard.repository.WhiteboardSummary("
                    + "w.id, w.title, w.creatorId, w.lastEditorId, w.updatedAt)"
                    + " from Whiteboard w"
                    + " where w.teamId = :teamId and w.deletedAt is null"
                    + " order by w.updatedAt desc, w.id desc")
    List<WhiteboardSummary> findSummaries(@Param("teamId") Long teamId);
}
