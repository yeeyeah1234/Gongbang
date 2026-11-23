package com.gongbang.workspace.document.repository;

import com.gongbang.workspace.document.domain.Document;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    /** 삭제되지 않은 문서. 없거나 삭제됐으면 빈 값 (둘 다 404). */
    Optional<Document> findByIdAndDeletedAtIsNull(Long id);

    /** DOC-02 50개 제한: 팀의 삭제되지 않은 문서 수. 팀 행을 잠근 트랜잭션 안에서 부른다 (AGENTS.md 7). */
    long countByTeamIdAndDeletedAtIsNull(Long teamId);

    /**
     * DOC-01 목록: 삭제되지 않은 문서를 updated_at 최신순(같으면 ID 역순)으로. 본문은 SELECT하지 않는다 (AGENTS.md 6).
     * idx(team_id, deleted_at, updated_at)를 탄다.
     */
    @Query(
            "select new com.gongbang.workspace.document.repository.DocumentSummary("
                    + "d.id, d.title, d.createdBy, d.updatedBy, d.updatedAt)"
                    + " from Document d"
                    + " where d.teamId = :teamId and d.deletedAt is null"
                    + " order by d.updatedAt desc, d.id desc")
    List<DocumentSummary> findSummaries(@Param("teamId") Long teamId);
}
