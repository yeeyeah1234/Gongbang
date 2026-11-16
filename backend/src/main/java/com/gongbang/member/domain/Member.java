package com.gongbang.member.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 회원 (DB 명세서 member). 행은 지우지 않고, 완전 삭제(DELETED) 때 개인정보 칸만 비운다. */
@Entity
@Table(name = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

    /** AUTH-01 ③④: 5번 틀리면 추가 인증, 추가 인증 뒤 5번 더(10번) 틀리면 30분 잠금 */
    public static final int EXTRA_VERIFICATION_FAIL_COUNT = 5;

    public static final int LOCK_FAIL_COUNT = 10;
    public static final int LOCK_MINUTES = 30;

    /** MY-11 ①: 탈퇴 후 30일 안에는 복원할 수 있다 */
    public static final int RESTORE_DAYS = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", length = 100, unique = true)
    private String email;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "login_type", nullable = false, length = 10)
    private LoginType loginType = LoginType.EMAIL;

    @Column(name = "kakao_id", length = 50, unique = true)
    private String kakaoId;

    /** AUTH-10 구글 로그인 (V10). 완전 삭제 시 NULL */
    @Column(name = "google_id", length = 50, unique = true)
    private String googleId;

    @Column(name = "nickname", length = 10, unique = true)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 10)
    private MemberRole role = MemberRole.USER;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Column(name = "bio", length = 50)
    private String bio;

    @Column(name = "school", length = 50)
    private String school;

    @Column(name = "major", length = 50)
    private String major;

    @Enumerated(EnumType.STRING)
    @Column(name = "grade", length = 10)
    private Grade grade;

    @Column(name = "portfolio_url", length = 500)
    private String portfolioUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private MemberStatus status = MemberStatus.ACTIVE;

    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    @Column(name = "login_fail_count", nullable = false)
    private int loginFailCount = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    /** member_interest (category.id) */
    @ElementCollection
    @CollectionTable(name = "member_interest", joinColumns = @JoinColumn(name = "member_id"))
    @Column(name = "category_id", nullable = false)
    private Set<Long> interestCategoryIds = new LinkedHashSet<>();

    /** member_desired_role */
    @ElementCollection
    @Enumerated(EnumType.STRING)
    @CollectionTable(name = "member_desired_role", joinColumns = @JoinColumn(name = "member_id"))
    @Column(name = "role_code", nullable = false, length = 20)
    private Set<DesiredRole> desiredRoles = new LinkedHashSet<>();

    /** member_skill_tag (id는 DB AUTO_INCREMENT가 채운다) */
    @ElementCollection
    @CollectionTable(name = "member_skill_tag", joinColumns = @JoinColumn(name = "member_id"))
    @Column(name = "name", nullable = false, length = 20)
    private Set<String> skillTags = new LinkedHashSet<>();

    public static Member createEmailMember(String email, String passwordHash, String nickname) {
        Member member = new Member();
        member.email = email;
        member.passwordHash = passwordHash;
        member.nickname = nickname;
        member.loginType = LoginType.EMAIL;
        return member;
    }

    /** AUTH-10 ⑤: 카카오·구글로 처음 가입 (비밀번호 없음) */
    public static Member createSocialMember(
            LoginType loginType, String providerId, String email, String nickname) {
        Member member = new Member();
        member.email = email;
        member.nickname = nickname;
        member.loginType = loginType;
        if (loginType == LoginType.GOOGLE) {
            member.googleId = providerId;
        } else {
            member.kakaoId = providerId;
        }
        return member;
    }

    public boolean isActive() {
        return status == MemberStatus.ACTIVE;
    }

    public boolean isWithdrawn() {
        return status == MemberStatus.WITHDRAWN;
    }

    public boolean isEmailMember() {
        return loginType == LoginType.EMAIL;
    }

    public boolean isAdmin() {
        return role == MemberRole.ADMIN;
    }

    public boolean isLocked(LocalDateTime now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public LocalDateTime restoreDeadline() {
        return withdrawnAt == null ? null : withdrawnAt.plusDays(RESTORE_DAYS);
    }

    /** AUTH-01 ②④: 가입된 회원이 비밀번호를 틀리면 1 올린다. 10번이면 30분 잠그고 횟수를 처음으로 돌린다. */
    public int increaseLoginFail(LocalDateTime now) {
        loginFailCount++;
        if (loginFailCount >= LOCK_FAIL_COUNT) {
            lockedUntil = now.plusMinutes(LOCK_MINUTES);
        }
        return loginFailCount;
    }

    /** AUTH-01 ③: 5번 틀린 뒤 추가 인증을 통과해야 다시 로그인을 받는다 */
    public boolean needsExtraVerification() {
        return loginFailCount == EXTRA_VERIFICATION_FAIL_COUNT;
    }

    /** 추가 인증 통과: 5 → 6으로 올려 다음 실패부터 잠금까지(10번) 센다 */
    public void passExtraVerification() {
        if (loginFailCount == EXTRA_VERIFICATION_FAIL_COUNT) {
            loginFailCount++;
        }
    }

    /** 잠금 시간이 지나 다시 시도할 때 처음부터 센다 */
    public void clearExpiredLock(LocalDateTime now) {
        if (lockedUntil != null && !lockedUntil.isAfter(now)) {
            resetLoginFail();
        }
    }

    /** AUTH-01 ⑧ 로그인 성공, AUTH-02 ⑤ 비밀번호 변경: 틀린 횟수·잠금을 푼다 */
    public void resetLoginFail() {
        loginFailCount = 0;
        lockedUntil = null;
    }

    public void loggedIn(LocalDateTime now) {
        resetLoginFail();
        lastLoginAt = now;
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        resetLoginFail();
    }

    public void updateProfile(
            String nickname,
            String profileImageUrl,
            String bio,
            String school,
            String major,
            Grade grade,
            String portfolioUrl) {
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
        this.bio = bio;
        this.school = school;
        this.major = major;
        this.grade = grade;
        this.portfolioUrl = portfolioUrl;
    }

    public void replaceInterests(Set<Long> categoryIds) {
        interestCategoryIds.clear();
        interestCategoryIds.addAll(categoryIds);
    }

    public void replaceDesiredRoles(Set<DesiredRole> roles) {
        desiredRoles.clear();
        desiredRoles.addAll(roles);
    }

    public void replaceSkillTags(Set<String> tags) {
        skillTags.clear();
        skillTags.addAll(tags);
    }

    /** MY-11 ④: 복원 대기 상태로 바꾼다 */
    public void withdraw(LocalDateTime now) {
        status = MemberStatus.WITHDRAWN;
        withdrawnAt = now;
    }

    /** MY-11 ⑥ */
    public void restore() {
        status = MemberStatus.ACTIVE;
        withdrawnAt = null;
        resetLoginFail();
    }

    /** MY-11 ⑦, 30일 경과: 개인정보 칸을 비운다 (DB 명세서 member 설명). 이력·북마크 행은 서비스가 지운다. */
    public void erasePersonalData() {
        status = MemberStatus.DELETED;
        email = null;
        passwordHash = null;
        kakaoId = null;
        googleId = null;
        nickname = null;
        profileImageUrl = null;
        bio = null;
        school = null;
        major = null;
        grade = null;
        portfolioUrl = null;
        interestCategoryIds.clear();
        desiredRoles.clear();
        skillTags.clear();
    }
}
