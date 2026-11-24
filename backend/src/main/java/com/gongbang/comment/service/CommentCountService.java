package com.gongbang.comment.service;

import com.gongbang.comment.repository.CommentRepository;
import com.gongbang.comment.repository.CommentRepository.CommentTargetCount;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 목록용 댓글 수 일괄 조회 (`BRD-001`·`BRD-008` 게시판 목록, `MY-02` 내 모집글). 삭제·관리자 숨김되지 않은 댓글·답글·비공개
 * 댓글(`deletedAt IS NULL`)만 센다.
 *
 * <p>{@link CommentQueryService}는 삭제 확인을 위해 모집글·게시글 조회 서비스를 쓰므로, 그 서비스들이 댓글 수를 부를 수 있도록 {@link
 * CommentRepository}만 쓰는 별도 서비스로 둔다 (순환 참조 방지).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentCountService {

    private final CommentRepository commentRepository;

    /** 여러 모집글의 댓글 수. 댓글이 없는 글은 맵에 없다. */
    public Map<Long, Long> countByPostIds(Collection<Long> postIds) {
        return countByTargetIds(postIds, commentRepository::countActiveByPostIds);
    }

    /** 여러 커뮤니티 게시글의 댓글 수. 댓글이 없는 글은 맵에 없다. */
    public Map<Long, Long> countByBoardPostIds(Collection<Long> boardPostIds) {
        return countByTargetIds(boardPostIds, commentRepository::countActiveByBoardPostIds);
    }

    private static Map<Long, Long> countByTargetIds(
            Collection<Long> targetIds,
            Function<Collection<Long>, List<CommentTargetCount>> loader) {
        if (targetIds == null || targetIds.isEmpty()) {
            return Map.of();
        }
        Set<Long> normalizedIds =
                targetIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (normalizedIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> counts = new LinkedHashMap<>();
        for (CommentTargetCount row : loader.apply(normalizedIds)) {
            counts.put(row.getTargetId(), row.getCount());
        }
        return counts;
    }
}
