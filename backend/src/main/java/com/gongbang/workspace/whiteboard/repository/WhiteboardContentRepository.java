package com.gongbang.workspace.whiteboard.repository;

import com.gongbang.workspace.whiteboard.domain.Whiteboard;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * WB-10: 보드 본문(content, LONGBLOB) 읽기·쓰기. {@link Whiteboard} 엔티티는 본문을 매핑하지 않으므로 native 쿼리로만 다룬다. 동기화
 * 서버용 internal API({@code WhiteboardSyncService})만 부른다.
 */
public interface WhiteboardContentRepository extends Repository<Whiteboard, Long> {

    /** 본문 바이트. 행이 없거나 본문이 NULL이면 null. 삭제 여부는 부르는 쪽이 먼저 확인한다. */
    @Query(value = "SELECT content FROM whiteboard WHERE id = :id", nativeQuery = true)
    byte[] findContent(@Param("id") Long id);

    /**
     * 본문을 저장하고 last_editor_id·updated_at을 갱신한다. 삭제된 보드면 아무 행도 바꾸지 않는다(0 → 404).
     *
     * <p>updated_at을 직접 넣는 이유: 같은 바이트·같은 회원이면 바뀐 컬럼이 없어 ON UPDATE CURRENT_TIMESTAMP가 돌지 않는다.
     */
    @Modifying
    @Query(
            value =
                    "UPDATE whiteboard SET content = :content, last_editor_id = :lastEditorId,"
                            + " updated_at = NOW()"
                            + " WHERE id = :id AND deleted_at IS NULL",
            nativeQuery = true)
    int saveContent(
            @Param("id") Long id,
            @Param("content") byte[] content,
            @Param("lastEditorId") Long lastEditorId);
}
