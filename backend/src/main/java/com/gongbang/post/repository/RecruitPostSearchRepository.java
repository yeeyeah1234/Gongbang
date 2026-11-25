package com.gongbang.post.repository;

import static com.gongbang.post.domain.QRecruitPost.recruitPost;

import com.gongbang.post.domain.PostSearchSort;
import com.gongbang.post.domain.PostStatus;
import com.gongbang.post.domain.RecruitPost;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.DateExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 통합 검색(SRCH-05 ①)의 모집글 부분. 제목·본문에 검색어가 있거나 작성자가 주어진 회원인 글을 찾는다. 삭제된 글(관리자 숨김 포함)은 빠진다. 조건과 정렬이 많아
 * QueryDSL로 만든다 (AGENTS.md 2-1).
 */
@Repository
@RequiredArgsConstructor
public class RecruitPostSearchRepository {

    private final JPAQueryFactory queryFactory;

    public List<RecruitPost> search(
            String keyword,
            Collection<Long> authorIds,
            PostSearchSort sort,
            LocalDate today,
            long offset,
            int limit) {
        return queryFactory
                .selectFrom(recruitPost)
                .where(matches(keyword, authorIds))
                .orderBy(orderOf(sort, keyword, today))
                .offset(offset)
                .limit(limit)
                .fetch();
    }

    public long count(String keyword, Collection<Long> authorIds) {
        Long count =
                queryFactory
                        .select(recruitPost.count())
                        .from(recruitPost)
                        .where(matches(keyword, authorIds))
                        .fetchOne();
        return count == null ? 0 : count;
    }

    // 검색어는 대소문자 구분 없이 부분 일치 (utf8mb4_0900_ai_ci). 작성자 ID가 없으면 그 조건은 빼고 찾는다
    private static BooleanExpression matches(String keyword, Collection<Long> authorIds) {
        BooleanExpression text =
                recruitPost.title.contains(keyword).or(recruitPost.content.contains(keyword));
        BooleanExpression found =
                authorIds == null || authorIds.isEmpty()
                        ? text
                        : text.or(recruitPost.authorId.in(authorIds));
        return recruitPost.deletedAt.isNull().and(found);
    }

    // 같은 값이면 나중에 쓴 글(id 큰 것)을 앞에 둬서 페이지가 바뀌어도 순서가 흔들리지 않게 한다
    private static OrderSpecifier<?>[] orderOf(
            PostSearchSort sort, String keyword, LocalDate today) {
        return switch (sort) {
                // SRCH-05 ②: 제목이 검색어와 같음 > 제목이 검색어로 시작 > 제목에 포함 > 본문·작성자만 일치, 같으면 최신순
            case ACCURACY -> {
                NumberExpression<Integer> score =
                        new CaseBuilder()
                                .when(recruitPost.title.eq(keyword))
                                .then(4)
                                .when(recruitPost.title.startsWith(keyword))
                                .then(3)
                                .when(recruitPost.title.contains(keyword))
                                .then(2)
                                .otherwise(1);
                yield new OrderSpecifier<?>[] {
                    score.desc(), recruitPost.createdAt.desc(), recruitPost.id.desc()
                };
            }
            case LATEST ->
                    new OrderSpecifier<?>[] {recruitPost.createdAt.desc(), recruitPost.id.desc()};
                // SRCH-02 ②: 모집 종료일 기준. 지원할 수 있는 글(모집중이고 종료일이 오늘 이후)을 종료일 빠른 순으로 앞에,
                // 모집완료·기간 지난 글은 뒤에 최신순으로 둔다
            case DEADLINE -> {
                BooleanExpression open =
                        recruitPost
                                .status
                                .eq(PostStatus.RECRUITING)
                                .and(recruitPost.recruitEndDate.goe(today));
                NumberExpression<Integer> closedLast =
                        new CaseBuilder().when(open).then(0).otherwise(1);
                // 종료일 정렬은 지원할 수 있는 글에만 건다 (끝난 글은 모두 같은 값이라 최신순만 남는다)
                DateExpression<LocalDate> openEndDate =
                        new CaseBuilder()
                                .when(open)
                                .then(recruitPost.recruitEndDate)
                                .otherwise(Expressions.nullExpression(LocalDate.class));
                yield new OrderSpecifier<?>[] {
                    closedLast.asc(),
                    openEndDate.asc(),
                    recruitPost.createdAt.desc(),
                    recruitPost.id.desc()
                };
            }
        };
    }
}
