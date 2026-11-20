package com.gongbang.workspace.notice.domain;

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
 * 팀 공지 (NTC-01~05). 삭제는 행을 지운다.
 *
 * <p>DB의 생성 열 pinned_flag(고정이면 1)는 매핑하지 않는다. Hibernate는 매핑한 열만 INSERT·UPDATE하므로 생성 열에 값을 넣으려다 실패하지
 * 않고, 팀당 고정 1개는 UNIQUE(team_id, pinned_flag)가 막는다 (V17).
 *
 * <p>editedAt은 제목·내용이 바뀔 때만 채운다. 고정·해제는 editedAt을 건드리지 않는다 (「(수정됨)」 표시용).
 */
@Entity
@Table(name = "notice")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notice {

    public static final int TITLE_MAX_LENGTH = 50;
    public static final int CONTENT_MAX_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    // 작성자 회원이 지워지면 NULL (FK ON DELETE SET NULL)
    @Column(name = "author_id")
    private Long authorId;

    @Column(name = "title", nullable = false, length = TITLE_MAX_LENGTH)
    private String title;

    @Column(name = "content", nullable = false, length = CONTENT_MAX_LENGTH)
    private String content;

    @Column(name = "is_pinned", nullable = false)
    private boolean pinned;

    // DB 기본값 CURRENT_TIMESTAMP로 채운다.
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "edited_at")
    private LocalDateTime editedAt;

    private Notice(Long teamId, Long authorId, String title, String content, boolean pinned) {
        this.teamId = teamId;
        this.authorId = authorId;
        this.title = title;
        this.content = content;
        this.pinned = pinned;
    }

    /** 값 검증(글자 수)은 service가 끝낸 뒤 부른다. */
    public static Notice create(
            Long teamId, Long authorId, String title, String content, boolean pinned) {
        return new Notice(teamId, authorId, title, content, pinned);
    }

    /** 제목·내용이 실제로 바뀌었을 때만 고치고 editedAt을 채운다. 바뀌었으면 true. */
    public boolean edit(String title, String content, LocalDateTime now) {
        if (Objects.equals(this.title, title) && Objects.equals(this.content, content)) {
            return false;
        }
        this.title = title;
        this.content = content;
        this.editedAt = now;
        return true;
    }

    public boolean isEdited() {
        return editedAt != null;
    }

    public void pin() {
        this.pinned = true;
    }

    public void unpin() {
        this.pinned = false;
    }
}
