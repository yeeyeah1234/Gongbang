package com.gongbang.contest.service;

import com.gongbang.contest.domain.*;
import com.gongbang.contest.repository.CategoryRepository;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.service.AdminMemberService;
import com.gongbang.post.domain.QRecruitPost;
import com.gongbang.team.domain.QTeam;
import com.gongbang.workspace.calendar.service.CalendarService;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminContestService {
    private final AdminMemberService access;
    private final CategoryRepository categories;
    private final CalendarService calendar;
    private final EntityManager em;

    public record Item(
            Long id,
            String title,
            LocalDate endDate,
            Long categoryId,
            String categoryName,
            String sourceUrl) {}

    public record TeamItem(Long id, String name) {}

    public record CategoryItem(Long id, String code, String name, int sortOrder) {}

    public record Update(
            String title, LocalDate endDate, Long categoryId, LocalDate expectedEndDate) {}

    public record CategoryUpdate(String name, int sortOrder) {}

    private Item item(Contest c) {
        return new Item(
                c.getId(),
                c.getTitle(),
                c.getEndDate(),
                c.getCategory().getId(),
                c.getCategory().getName(),
                c.getSourceUrl());
    }

    @Transactional(readOnly = true)
    public Page<Item> list(Long actor, String keyword, int page) {
        access.requireAdmin(actor);
        if (page < 0 || keyword.length() > 100)
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        var c = QContest.contest;
        var q = new JPAQueryFactory(em);
        var where = c.title.containsIgnoreCase(keyword);
        var rows =
                q.selectFrom(c)
                        .join(c.category)
                        .fetchJoin()
                        .where(where)
                        .orderBy(c.id.desc())
                        .offset((long) page * 20)
                        .limit(20)
                        .fetch();
        Long count = q.select(c.count()).from(c).where(where).fetchOne();
        return new PageImpl<>(
                rows.stream().map(this::item).toList(),
                PageRequest.of(page, 20),
                count == null ? 0 : count);
    }

    @Transactional(readOnly = true)
    public List<TeamItem> teams(Long actor, Long id) {
        access.requireAdmin(actor);
        var t = QTeam.team;
        var p = QRecruitPost.recruitPost;
        return new JPAQueryFactory(em)
                        .select(t.id, t.name)
                        .from(t)
                        .join(p)
                        .on(t.postId.eq(p.id))
                        .where(p.contestId.eq(id))
                        .fetch()
                        .stream()
                        .map(row -> new TeamItem(row.get(t.id), row.get(t.name)))
                        .toList();
    }

    @Transactional
    public Item update(Long actor, Long id, Update body) {
        access.requireAdmin(actor);
        Contest c = em.find(Contest.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (c == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        if (body.title() == null
                || body.title().isBlank()
                || body.title().strip().length() > 200
                || body.endDate() == null
                || body.categoryId() == null) throw new BusinessException(ErrorCode.INVALID_INPUT);
        if (!c.getEndDate().equals(body.expectedEndDate()))
            throw new BusinessException(ErrorCode.INVALID_INPUT, "마감일이 변경되었습니다. 새로고침 후 다시 확인해 주세요");
        if (c.getStartDate() != null && body.endDate().isBefore(c.getStartDate()))
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        Category category =
                categories
                        .findById(body.categoryId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT));
        c.updateByAdmin(body.title().strip(), body.endDate(), category);
        em.flush();
        calendar.syncContestMilestones(id);
        return item(c);
    }

    @Transactional(readOnly = true)
    public List<CategoryItem> categories(Long actor) {
        access.requireAdmin(actor);
        return categories.findAll(Sort.by("sortOrder")).stream()
                .map(c -> new CategoryItem(c.getId(), c.getCode(), c.getName(), c.getSortOrder()))
                .toList();
    }

    @Transactional
    public void category(Long actor, Long id, CategoryUpdate body) {
        access.requireAdmin(actor);
        if (body.name() == null
                || body.name().isBlank()
                || body.name().strip().length() > 20
                || body.sortOrder() < 0) throw new BusinessException(ErrorCode.INVALID_INPUT);
        categories
                .findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND))
                .update(body.name().strip(), body.sortOrder());
    }
}
