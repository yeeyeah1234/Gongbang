package com.gongbang.contest.repository;

import static com.gongbang.contest.domain.QCategory.category;
import static com.gongbang.contest.domain.QContest.contest;

import com.gongbang.contest.domain.Contest;
import com.gongbang.contest.domain.ContestSearchSort;
import com.gongbang.contest.domain.ContestSort;
import com.gongbang.contest.domain.ContestStatus;
import com.gongbang.contest.dto.ContestSearchCondition;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 공모전 목록 조회 (MAIN-01·04·05). 조건이 많아 QueryDSL로 만든다 (AGENTS.md 2-1). */
@Repository
@RequiredArgsConstructor
public class ContestQueryRepository {

    private final JPAQueryFactory queryFactory;

    public List<Contest> findContests(
            ContestSearchCondition condition, LocalDate today, long offset, int limit) {
        return queryFactory
                .selectFrom(contest)
                .join(contest.category, category)
                .fetchJoin()
                .where(filters(condition, today))
                .orderBy(orderOf(condition.sort()))
                .offset(offset)
                .limit(limit)
                .fetch();
    }

    /** SRCH-05 통합 검색의 공모전 부분. condition의 sort 대신 검색 정렬(정확도 등)을 쓴다. */
    public List<Contest> searchContests(
            ContestSearchCondition condition,
            ContestSearchSort sort,
            LocalDate today,
            long offset,
            int limit) {
        return queryFactory
                .selectFrom(contest)
                .join(contest.category, category)
                .fetchJoin()
                .where(filters(condition, today))
                .orderBy(searchOrderOf(sort, condition.keyword()))
                .offset(offset)
                .limit(limit)
                .fetch();
    }

    public long countContests(ContestSearchCondition condition, LocalDate today) {
        Long count =
                queryFactory
                        .select(contest.count())
                        .from(contest)
                        .join(contest.category, category)
                        .where(filters(condition, today))
                        .fetchOne();
        return count == null ? 0 : count;
    }

    // null인 조건은 QueryDSL where에서 무시된다
    private BooleanExpression[] filters(ContestSearchCondition condition, LocalDate today) {
        return new BooleanExpression[] {
            condition.status() == ContestStatus.ACTIVE ? contest.endDate.goe(today) : null,
            condition.categoryCode() != null ? category.code.eq(condition.categoryCode()) : null,
            condition.keyword() != null
                    ? contest.title
                            .contains(condition.keyword())
                            .or(contest.host.contains(condition.keyword()))
                    : null
        };
    }

    // 같은 값이면 나중에 들어온 공모전(id 큰 것)을 앞에 둬서 페이지가 바뀌어도 순서가 흔들리지 않게 한다
    private OrderSpecifier<?>[] orderOf(ContestSort sort) {
        OrderSpecifier<?> primary =
                switch (sort) {
                    case LATEST -> contest.createdAt.desc();
                    case DEADLINE -> contest.endDate.asc();
                    case VIEWS -> contest.viewCount.desc();
                };
        return new OrderSpecifier<?>[] {primary, contest.id.desc()};
    }

    // SRCH-05 ②: 정확도순은 제목이 검색어와 같음 > 제목이 검색어로 시작 > 제목에 포함 > 주최에만 포함 순으로 점수를 매기고,
    // 점수가 같으면 최신 등록순으로 둔다
    private OrderSpecifier<?>[] searchOrderOf(ContestSearchSort sort, String keyword) {
        return switch (sort) {
            case ACCURACY -> {
                NumberExpression<Integer> score =
                        new CaseBuilder()
                                .when(contest.title.eq(keyword))
                                .then(4)
                                .when(contest.title.startsWith(keyword))
                                .then(3)
                                .when(contest.title.contains(keyword))
                                .then(2)
                                .otherwise(1);
                yield new OrderSpecifier<?>[] {
                    score.desc(), contest.createdAt.desc(), contest.id.desc()
                };
            }
            case LATEST -> new OrderSpecifier<?>[] {contest.createdAt.desc(), contest.id.desc()};
            case DEADLINE -> new OrderSpecifier<?>[] {contest.endDate.asc(), contest.id.desc()};
        };
    }
}
