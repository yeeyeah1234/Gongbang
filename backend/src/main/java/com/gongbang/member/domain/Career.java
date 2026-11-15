package com.gongbang.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** MY-07 경력. end_date가 NULL이면 진행 중. */
@Entity
@Table(name = "career")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Career {

    /** MY-07 ④ 최대 20개 */
    public static final int MAX_PER_MEMBER = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "organization", nullable = false, length = 50)
    private String organization;

    @Column(name = "role", nullable = false, length = 50)
    private String role;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "description", length = 200)
    private String description;

    public Career(Long memberId) {
        this.memberId = memberId;
    }

    public void update(
            String organization,
            String role,
            LocalDate startDate,
            LocalDate endDate,
            String description) {
        this.organization = organization;
        this.role = role;
        this.startDate = startDate;
        this.endDate = endDate;
        this.description = description;
    }
}
