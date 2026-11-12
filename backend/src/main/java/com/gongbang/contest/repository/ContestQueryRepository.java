package com.gongbang.contest.repository;

import static com.gongbang.contest.domain.QCategory.category;
import static com.gongbang.contest.domain.QContest.contest;

import com.gongbang.contest.domain.Contest;
import com.gongbang.contest.domain.ContestSort;
import com.gongbang.contest.domain.ContestStatus;
import com.gongbang.contest.dto.ContestSearchCondition;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
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
}
