package com.gongbang.workspace.document.domain;

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
 * 팀 문서 (DOC-01~04). 소프트 삭제(deletedAt)하는 테이블이다 (AGENTS.md 6).
 *
 * <p>본문(content, LONGBLOB)은 일부러 매핑하지 않는다. 이름 변경·삭제처럼 엔티티를 읽을 때 큰 바이너리를 끌어오지 않게 하려는 것이다 (LAZY basic은
 * 바이트코드 강화 없이는 적용되지 않음). 본문은 {@code DocumentContentRepository}가 native 쿼리로만 읽고 쓴다.
 */
@Entity
@Table(name = "document")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Document {

    public static final int TITLE_MAX_LENGTH = 50;
    public static final String DEFAULT_TITLE = "제목 없는 문서";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "title", nullable = false, length = 50)
    private String title;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "updated_by", nullable = false)
    private Long updatedBy;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // DB가 채우는 공통 컬럼.
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    private Document(Long teamId, Long createdBy) {
        this.teamId = teamId;
        this.title = DEFAULT_TITLE;
        this.createdBy = createdBy;
        this.updatedBy = createdBy;
    }

    /** DOC-02 새 문서: 제목 「제목 없는 문서」, 빈 본문(NULL). 50개 제한은 service가 확인한 뒤 부른다. */
    public static Document create(Long teamId, Long createdBy) {
        return new Document(teamId, createdBy);
    }

    /**
     * DOC-03 이름 변경. 제목 정리·검증은 service가 끝낸 뒤 부른다. 같은 제목이면 아무것도 바꾸지 않고 false.
     *
     * <p>TODO(확인 필요): 이름 변경도 updated_by를 바꾼 사람으로 갱신한다. updated_at은 ON UPDATE로 바뀌는데 updated_by만 이전
     * 사람이면 목록의 「마지막 수정」이 맞지 않아 보여서다. 기능정의서 기준과 대조하지 못했다.
     */
    public boolean rename(String title, Long memberId) {
        if (Objects.equals(this.title, title)) {
            return false;
        }
        this.title = title;
        this.updatedBy = memberId;
        return true;
    }

    /** DOC-04 삭제: deleted_at을 기록한다. */
    public void softDelete(LocalDateTime now) {
        this.deletedAt = now;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
