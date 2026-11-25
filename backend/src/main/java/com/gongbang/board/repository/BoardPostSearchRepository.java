package com.gongbang.board.repository;

import static com.gongbang.board.domain.QBoardPost.boardPost;

import com.gongbang.board.domain.BoardPost;
import com.gongbang.board.domain.BoardPostSearchSort;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 통합 검색(SRCH-05 ①)의 커뮤니티 게시글 부분. 제목·내용에 검색어가 있거나 작성자가 주어진 회원인 글을 찾는다. 삭제된 글(관리자 숨김 포함)은 빠진다. 모집글
 * 검색(RecruitPostSearchRepository)과 같은 방식이다 (AGENTS.md 2-1 QueryDSL).
 */
@Repository
@RequiredArgsConstructor
public class BoardPostSearchRepository {

    private final JPAQueryFactory queryFactory;

    public List<BoardPost> search(
            String keyword,
            Collection<Long> authorIds,
            BoardPostSearchSort sort,
            long offset,
            int limit) {
        return queryFactory
                .selectFrom(boardPost)
                .where(matches(keyword, authorIds))
                .orderBy(orderOf(sort, keyword))
                .offset(offset)
                .limit(limit)
                .fetch();
    }

    public long count(String keyword, Collection<Long> authorIds) {
        Long count =
                queryFactory
                        .select(boardPost.count())
                        .from(boardPost)
                        .where(matches(keyword, authorIds))
                        .fetchOne();
        return count == null ? 0 : count;
    }

    // 검색어는 대소문자 구분 없이 부분 일치 (utf8mb4_0900_ai_ci). 작성자 ID가 없으면 그 조건은 빼고 찾는다
    private static BooleanExpression matches(String keyword, Collection<Long> authorIds) {
        BooleanExpression text =
                boardPost.title.contains(keyword).or(boardPost.content.contains(keyword));
        BooleanExpression found =
                authorIds == null || authorIds.isEmpty()
                        ? text
                        : text.or(boardPost.authorId.in(authorIds));
        return boardPost.deletedAt.isNull().and(found);
    }

    // 같은 값이면 나중에 쓴 글(id 큰 것)을 앞에 둬서 페이지가 바뀌어도 순서가 흔들리지 않게 한다
    private static OrderSpecifier<?>[] orderOf(BoardPostSearchSort sort, String keyword) {
        return switch (sort) {
                // SRCH-05 ②: 제목이 검색어와 같음 > 제목이 검색어로 시작 > 제목에 포함 > 내용·작성자만 일치, 같으면 최신순
            case ACCURACY -> {
                NumberExpression<Integer> score =
                        new CaseBuilder()
                                .when(boardPost.title.eq(keyword))
                                .then(4)
                                .when(boardPost.title.startsWith(keyword))
                                .then(3)
                                .when(boardPost.title.contains(keyword))
                                .then(2)
                                .otherwise(1);
                yield new OrderSpecifier<?>[] {
                    score.desc(), boardPost.createdAt.desc(), boardPost.id.desc()
                };
            }
                // SRCH-02 ②: 게시글은 마감일이 없어 마감 임박순도 최신순
            case LATEST, DEADLINE ->
                    new OrderSpecifier<?>[] {boardPost.createdAt.desc(), boardPost.id.desc()};
        };
    }
}
