package com.gongbang.comment.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(name = "comment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long postId;

    @Column(nullable = false)
    private Long authorId;

    @Column(nullable = false, length = 2000)
    private String content;

    private LocalDateTime deletedAt;

    @Column(insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public void hide(LocalDateTime now) {
        deletedAt = now;
    }

    public String visibleContent() {
        return deletedAt == null ? content : "관리자에 의해 숨김 처리된 댓글입니다.";
    }
}
