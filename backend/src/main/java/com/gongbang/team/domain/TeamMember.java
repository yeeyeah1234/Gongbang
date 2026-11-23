package com.gongbang.team.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 팀원 여부의 기준 (AGENTS.md 6). left_at IS NULL이 현재 팀원이다. 다시 들어오면 기존 행을 고치지 않고 새 행을 만든다. */
@Entity
@Table(name = "team_member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamMember {

    /** 팀장은 항상 1번 색 (WS-03). */
    public static final int LEADER_COLOR_INDEX = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    // TODO(확인 필요): member는 V9에 이미 있지만 이번엔 FK를 넣지 않는다. 다음 빈 번호 마이그레이션에서 추가한다.
    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 10)
    private TeamRole role;

    // 팀원 고유 색 1~8 (WS-03, STYLE_GUIDE 1-3).
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "color_index", nullable = false)
    private int colorIndex;

    // DB 기본값 CURRENT_TIMESTAMP로 채운다.
    @Column(name = "joined_at", insertable = false, updatable = false)
    private LocalDateTime joinedAt;

    @Column(name = "left_at")
    private LocalDateTime leftAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "leave_type", length = 10)
    private LeaveType leaveType;

    private TeamMember(Team team, Long memberId, TeamRole role, int colorIndex) {
        this.team = team;
        this.memberId = memberId;
        this.role = role;
        this.colorIndex = colorIndex;
    }

    public static TeamMember leader(Team team, Long memberId) {
        return new TeamMember(team, memberId, TeamRole.LEADER, LEADER_COLOR_INDEX);
    }

    public static TeamMember member(Team team, Long memberId, int colorIndex) {
        return new TeamMember(team, memberId, TeamRole.MEMBER, colorIndex);
    }

    /** 팀원을 강제 퇴장 처리한다. */
    public void kick(LocalDateTime leftAt) {
        this.leftAt = leftAt != null ? leftAt : LocalDateTime.now();
        this.leaveType = LeaveType.KICKED;
    }

    /** 팀원 역할을 변경한다 (팀장 위임 등). */
    public void changeRole(TeamRole newRole) {
        this.role = newRole;
    }
}
