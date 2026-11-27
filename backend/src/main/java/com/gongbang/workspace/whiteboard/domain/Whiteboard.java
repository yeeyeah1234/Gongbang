package com.gongbang.workspace.whiteboard.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 팀 화이트보드 (WB-01). 소프트 삭제(deletedAt)하는 테이블이다 (AGENTS.md 6).
 *
 * <p>본문(content, LONGBLOB)은 일부러 매핑하지 않는다 ({@code Document}와 같은 이유). 본문은 {@code
 * WhiteboardContentRepository}가 native 쿼리로만 읽고 쓴다.
 *
 * <p>lastEditorId는 DB 명세서대로 동기화 서버가 본문을 저장할 때만 채운다. 만들 때는 NULL이고 이름 변경으로는 바뀌지 않는다.
 */
@Entity
@Table(name = "whiteboard")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Whiteboard {

    public static final int TITLE_MAX_LENGTH = 50;
    public static final String DEFAULT_TITLE = "제목 없는 보드";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "title", nullable = false, length = 50)
    private String title;

    @Column(name = "creator_id", nullable = false)
    private Long creatorId;

    // 동기화 서버 저장(native 쿼리)으로만 바뀐다
    @Column(name = "last_editor_id", insertable = false, updatable = false)
    private Long lastEditorId;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // DB가 채우는 공통 컬럼.
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    private Whiteboard(Long teamId, Long creatorId, String title) {
        this.teamId = teamId;
        this.title = title;
        this.creatorId = creatorId;
    }

    /** WB-01 ② 새 보드: 제목 「제목 없는 보드」, 빈 캔버스(NULL). 30개 제한은 service가 확인한 뒤 부른다. */
    public static Whiteboard create(Long teamId, Long creatorId) {
        return create(teamId, creatorId, DEFAULT_TITLE);
    }

    /** WB-01 ② 새 보드: 번호가 붙은 기본 제목으로 만든다. 30개 제한·제목 계산은 service가 끝낸 뒤 부른다. */
    public static Whiteboard create(Long teamId, Long creatorId, String title) {
        return new Whiteboard(teamId, creatorId, title);
    }

    /** WB-01 ① 이름 변경 (DOC-03과 같음). 제목 정리·검증은 service가 끝낸 뒤 부른다. 같은 제목이면 바꾸지 않고 false. */
    public boolean rename(String title) {
        if (Objects.equals(this.title, title)) {
            return false;
        }
        this.title = title;
        return true;
    }

    /** WB-01 ① 삭제: deleted_at을 기록한다. */
    public void softDelete(LocalDateTime now) {
        this.deletedAt = now;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
