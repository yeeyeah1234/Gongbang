package com.gongbang.member.service;

import com.gongbang.auth.domain.VerificationPurpose;
import com.gongbang.auth.dto.SignupRequest;
import com.gongbang.auth.dto.TermsItem;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.global.validation.UrlPolicy;
import com.gongbang.member.domain.DesiredRole;
import com.gongbang.member.domain.LoginType;
import com.gongbang.member.domain.Member;
import com.gongbang.member.domain.MemberStatus;
import com.gongbang.member.domain.TermsAgreement;
import com.gongbang.member.domain.TermsType;
import com.gongbang.member.dto.MemberProfileSummary;
import com.gongbang.member.dto.MyProfileResponse;
import com.gongbang.member.dto.PublicProfileResponse;
import com.gongbang.member.dto.UpdateProfileRequest;
import com.gongbang.member.repository.AwardRepository;
import com.gongbang.member.repository.BookmarkRepository;
import com.gongbang.member.repository.CareerRepository;
import com.gongbang.member.repository.CertificateRepository;
import com.gongbang.member.repository.MemberRepository;
import com.gongbang.member.repository.TermsAgreementRepository;
import com.gongbang.member.repository.VerificationRequestRepository;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원 정보의 주인 (다른 도메인은 MemberRepository 대신 이 service를 부른다, AGENTS 3) */
@Service
@RequiredArgsConstructor
public class MemberService {

    /** MY-01 ③ 기술 태그 최대 10개 */
    public static final int SKILL_TAG_LIMIT = 10;

    /** TODO(확인 필요): 약관 버전은 법무 문서가 정해지면 맞춘다 (Figma 13: 버전 1.2) */
    public static final String CURRENT_TERMS_VERSION = "1.2";

    private final MemberRepository memberRepository;
    private final TermsAgreementRepository termsAgreementRepository;
    private final CareerRepository careerRepository;
    private final CertificateRepository certificateRepository;
    private final AwardRepository awardRepository;
    private final BookmarkRepository bookmarkRepository;
    private final VerificationRequestRepository verificationRequestRepository;

    // ---------- 다른 도메인이 쓰는 조회 ----------

    @Transactional(readOnly = true)
    public Optional<Member> findByEmail(String email) {
        return memberRepository.findByEmail(email);
    }

    @Transactional(readOnly = true)
    public Optional<Member> findByNickname(String nickname) {
        return memberRepository.findByNickname(nickname).filter(Member::isActive);
    }

    @Transactional(readOnly = true)
    public Member getActiveMember(Long memberId) {
        return memberRepository
                .findById(memberId)
                .filter(Member::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    /** 예외 없이 활동 중인 회원만 찾는다. 탈퇴·삭제면 빈 값 (부르는 쪽 트랜잭션을 rollback-only로 만들지 않음) */
    @Transactional(readOnly = true)
    public Optional<Member> findActiveMember(Long memberId) {
        return memberRepository.findById(memberId).filter(Member::isActive);
    }

    /** 화면 표시용 닉네임. 완전 삭제·탈퇴는 「탈퇴한 사용자」 (AGENTS 6, MY-11 ①) */
    @Transactional(readOnly = true)
    public String displayName(Long memberId) {
        return memberRepository
                .findById(memberId)
                .filter(Member::isActive)
                .map(Member::getNickname)
                .orElse("탈퇴한 사용자");
    }

    @Transactional(readOnly = true)
    public List<Long> findActiveMemberIds() {
        return memberRepository.findIdsByStatus(MemberStatus.ACTIVE);
    }

    /** 채팅·팀원 목록 등 다른 도메인이 여러 회원의 닉네임·프로필·희망 역할을 한 번에 읽는다. */
    @Transactional(readOnly = true)
    public Map<Long, MemberProfileSummary> getProfileSummaries(Collection<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, MemberProfileSummary> result = new HashMap<>();
        for (Member member : memberRepository.findAllById(memberIds)) {
            result.put(member.getId(), toProfileSummary(member));
        }
        for (Long memberId : memberIds) {
            result.putIfAbsent(
                    memberId, new MemberProfileSummary(memberId, "탈퇴한 사용자", null, List.of()));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public MemberProfileSummary getProfileSummary(Long memberId) {
        if (memberId == null) {
            return null;
        }
        return memberRepository
                .findById(memberId)
                .map(MemberService::toProfileSummary)
                .orElseGet(() -> new MemberProfileSummary(memberId, "탈퇴한 사용자", null, List.of()));
    }

    private static MemberProfileSummary toProfileSummary(Member member) {
        if (!member.isActive()) {
            return new MemberProfileSummary(member.getId(), "탈퇴한 사용자", null, List.of());
        }
        List<String> roles = member.getDesiredRoles().stream().map(DesiredRole::name).toList();
        return new MemberProfileSummary(
                member.getId(), member.getNickname(), member.getProfileImageUrl(), roles);
    }

    // ---------- AUTH ----------

    /** AUTH-05 ⑥⑦ */
    @Transactional(readOnly = true)
    public void checkEmailAvailableForSignup(String email, boolean newSignup) {
        memberRepository
                .findByEmail(email)
                .ifPresent(
                        member -> {
                            if (member.isWithdrawn() && newSignup) {
                                return; // ⑦ 가입 완료 때 이전 계정을 완전 삭제한다
                            }
                            if (member.isWithdrawn()) {
                                throw withdrawn(member);
                            }
                            if (member.getLoginType() != LoginType.EMAIL) {
                                // ⑥ 카카오·구글로 가입된 이메일 → 그 방식으로 로그인 안내
                                String provider =
                                        member.getLoginType() == LoginType.KAKAO ? "카카오" : "구글";
                                throw new BusinessException(
                                        ErrorCode.SOCIAL_EMAIL_REGISTERED,
                                        provider + "로 가입된 이메일이에요. " + provider + "로 로그인해 주세요");
                            }
                            throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
                        });
    }

    /** 가입 외 용도(로그인 추가 인증·재설정·복원)는 가입된 이메일이어야 한다 (AUTH-02 ⑥) */
    @Transactional(readOnly = true)
    public void checkEmailRegistered(String email, VerificationPurpose purpose) {
        Member member =
                memberRepository
                        .findByEmail(email)
                        .orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_NOT_FOUND));
        if (purpose == VerificationPurpose.RESTORE) {
            if (!member.isWithdrawn()) {
                throw new BusinessException(ErrorCode.EMAIL_NOT_FOUND);
            }
            if (member.restoreDeadline().isBefore(LocalDateTime.now())) {
                throw new BusinessException(ErrorCode.RESTORE_EXPIRED);
            }
            return;
        }
        if (member.isWithdrawn()) {
            throw withdrawn(member);
        }
        if (!member.isActive()) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_FOUND);
        }
    }

    /** AUTH-06 ③ [중복 확인] */
    @Transactional(readOnly = true)
    public boolean isNicknameAvailable(String raw) {
        String nickname = NicknamePolicy.validate(raw);
        // MY-11 ⑤: 복원 대기 중인 닉네임도 다른 사람이 쓸 수 없다 (행이 남아 있어 UQ로 막힘)
        return !memberRepository.existsByNickname(nickname);
    }

    /** AUTH-04·07 ④: 서버가 이메일·닉네임 중복을 한 번 더 확인 */
    @Transactional
    public Member createEmailMember(String email, String passwordHash, SignupRequest request) {
        validateTerms(request.terms());
        String nickname = NicknamePolicy.validate(request.nickname());
        Optional<Member> existing = memberRepository.findByEmail(email);
        if (existing.isPresent()) {
            Member old = existing.get();
            if (!old.isWithdrawn()) {
                throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
            }
            // AUTH-05 ⑦ [새로 가입하기]: 이전 계정을 바로 완전 삭제하고 이어 간다
            eraseMember(old);
            memberRepository.flush();
        }
        if (memberRepository.existsByNickname(nickname)) {
            throw new BusinessException(ErrorCode.NICKNAME_DUPLICATED);
        }
        Member member = Member.createEmailMember(email, passwordHash, nickname);
        member.updateProfile(
                nickname,
                null,
                trimToNull(request.bio()),
                trimToNull(request.school()),
                trimToNull(request.major()),
                request.grade(),
                null);
        member.replaceInterests(resolveCategoryIds(request.interestCategoryCodes()));
        member.replaceDesiredRoles(toRoleSet(request.desiredRoles()));
        try {
            memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException e) {
            // 동시에 같은 이메일·닉네임으로 가입한 경우
            throw new BusinessException(ErrorCode.NICKNAME_DUPLICATED);
        }
        for (TermsItem item : request.terms()) {
            termsAgreementRepository.save(
                    new TermsAgreement(member.getId(), item.type(), item.version(), item.agreed()));
        }
        return member;
    }

    /** AUTH-10 ④: 이미 가입한 카카오·구글 계정 찾기 (탈퇴 대기 계정 포함) */
    @Transactional(readOnly = true)
    public Optional<Member> findBySocialId(LoginType loginType, String providerId) {
        return loginType == LoginType.GOOGLE
                ? memberRepository.findByGoogleId(providerId)
                : memberRepository.findByKakaoId(providerId);
    }

    /** AUTH-10 ⑤: 추가 정보(필수 약관 + 닉네임 + 관심 분야)를 받아 가입 */
    @Transactional
    public Member createSocialMember(
            LoginType loginType,
            String providerId,
            String email,
            String rawNickname,
            List<TermsItem> terms,
            List<String> interestCategoryCodes) {
        validateTerms(terms);
        String nickname = NicknamePolicy.validate(rawNickname);
        if (findBySocialId(loginType, providerId).isPresent()
                || memberRepository.findByEmail(email).isPresent()) {
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
        }
        if (memberRepository.existsByNickname(nickname)) {
            throw new BusinessException(ErrorCode.NICKNAME_DUPLICATED);
        }
        Member member = Member.createSocialMember(loginType, providerId, email, nickname);
        member.replaceInterests(resolveCategoryIds(interestCategoryCodes));
        try {
            memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.NICKNAME_DUPLICATED);
        }
        for (TermsItem item : terms) {
            termsAgreementRepository.save(
                    new TermsAgreement(member.getId(), item.type(), item.version(), item.agreed()));
        }
        return member;
    }

    /** MY-11 ⑥ */
    @Transactional
    public Member restore(String email) {
        Member member =
                memberRepository
                        .findByEmail(email)
                        .filter(Member::isWithdrawn)
                        .orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_NOT_FOUND));
        if (member.restoreDeadline().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.RESTORE_EXPIRED);
        }
        member.restore();
        return member;
    }

    // ---------- MY-01·05 ----------

    @Transactional(readOnly = true)
    public MyProfileResponse getMyProfile(Long memberId) {
        Member member = getActiveMember(memberId);
        return MyProfileResponse.of(member, categoryCodes(member.getInterestCategoryIds()));
    }

    @Transactional
    public MyProfileResponse updateMyProfile(Long memberId, UpdateProfileRequest request) {
        Member member = getActiveMember(memberId);
        String nickname = member.getNickname();
        if (request.nickname() != null && !request.nickname().trim().equals(member.getNickname())) {
            // MY-01 ④: 닉네임을 바꿀 때만 중복 확인
            nickname = NicknamePolicy.validate(request.nickname());
            if (memberRepository.existsByNickname(nickname)) {
                throw new BusinessException(ErrorCode.NICKNAME_DUPLICATED);
            }
        }
        if (request.skillTags() != null && request.skillTags().size() > SKILL_TAG_LIMIT) {
            throw new BusinessException(ErrorCode.SKILL_TAG_LIMIT);
        }
        if (request.interestCategoryCodes() != null && request.interestCategoryCodes().isEmpty()) {
            // MY-01 ③ 관심 분야 1개 이상
            throw new BusinessException(ErrorCode.INVALID_INPUT, "관심 분야를 1개 이상 골라 주세요");
        }
        member.updateProfile(
                nickname,
                request.profileImageUrl() != null
                        ? UrlPolicy.validateOrNull(request.profileImageUrl())
                        : member.getProfileImageUrl(),
                request.bio() != null ? trimToNull(request.bio()) : member.getBio(),
                request.school() != null ? trimToNull(request.school()) : member.getSchool(),
                request.major() != null ? trimToNull(request.major()) : member.getMajor(),
                request.grade() != null ? request.grade() : member.getGrade(),
                request.portfolioUrl() != null
                        ? UrlPolicy.validateOrNull(request.portfolioUrl())
                        : member.getPortfolioUrl());
        if (request.interestCategoryCodes() != null) {
            member.replaceInterests(resolveCategoryIds(request.interestCategoryCodes()));
        }
        if (request.desiredRoles() != null) {
            member.replaceDesiredRoles(toRoleSet(request.desiredRoles()));
        }
        if (request.skillTags() != null) {
            Set<String> tags = new LinkedHashSet<>();
            for (String tag : request.skillTags()) {
                String trimmed = tag == null ? "" : tag.trim();
                if (!trimmed.isEmpty() && trimmed.length() <= 20) {
                    tags.add(trimmed);
                }
            }
            member.replaceSkillTags(tags);
        }
        // MY-01 ④ 사진 삭제
        if (Boolean.TRUE.equals(request.removeProfileImage())) {
            member.updateProfile(
                    member.getNickname(),
                    null,
                    member.getBio(),
                    member.getSchool(),
                    member.getMajor(),
                    member.getGrade(),
                    member.getPortfolioUrl());
        }
        memberRepository.flush();
        return MyProfileResponse.of(member, categoryCodes(member.getInterestCategoryIds()));
    }

    /** MY-05: 이메일·가입 방식·실명·증빙 파일은 넣지 않는다 (AGENTS 9) */
    @Transactional(readOnly = true)
    public PublicProfileResponse getPublicProfile(Long memberId) {
        Member member =
                memberRepository
                        .findById(memberId)
                        .filter(Member::isActive)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.MEMBER_NOT_FOUND, "없거나 탈퇴한 회원이에요"));
        return PublicProfileResponse.of(
                member,
                categoryCodes(member.getInterestCategoryIds()),
                careerRepository.findByMemberIdOrderByStartDateDescIdDesc(memberId),
                certificateRepository.findByMemberIdOrderByAcquiredDateDescIdDesc(memberId),
                awardRepository.findByMemberIdOrderByAwardedDateDescIdDesc(memberId));
    }

    // ---------- MY-11 ----------

    /** 30일이 지났거나 [새로 가입하기]: 개인정보 비우고 이력·북마크 행 삭제 (DB 명세서 member 설명) */
    @Transactional
    public void eraseMember(Member member) {
        Long id = member.getId();
        careerRepository.deleteByMemberId(id);
        certificateRepository.deleteByMemberId(id);
        awardRepository.deleteByMemberId(id);
        verificationRequestRepository.deleteByMemberId(id);
        bookmarkRepository.deleteByMemberId(id);
        member.erasePersonalData();
    }

    // ---------- 내부 ----------

    private void validateTerms(List<TermsItem> terms) {
        Set<TermsType> agreed = EnumSet.noneOf(TermsType.class);
        for (TermsItem item : terms) {
            if (item.agreed()) {
                agreed.add(item.type());
            }
        }
        for (TermsType type : TermsType.values()) {
            if (type.isRequired() && !agreed.contains(type)) {
                throw new BusinessException(ErrorCode.TERMS_REQUIRED);
            }
        }
    }

    private Set<Long> resolveCategoryIds(Collection<String> codes) {
        if (codes == null || codes.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "관심 분야를 1개 이상 골라 주세요");
        }
        Set<String> unique = new HashSet<>(codes);
        List<Long> ids = memberRepository.findCategoryIdsByCodes(unique);
        if (ids.size() != unique.size()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return new LinkedHashSet<>(ids);
    }

    private List<String> categoryCodes(Set<Long> ids) {
        return ids.isEmpty() ? List.of() : memberRepository.findCategoryCodesByIds(ids);
    }

    private static Set<DesiredRole> toRoleSet(List<DesiredRole> roles) {
        return roles == null || roles.isEmpty()
                ? EnumSet.noneOf(DesiredRole.class)
                : EnumSet.copyOf(roles);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static BusinessException withdrawn(Member member) {
        long daysLeft =
                Math.max(
                        0,
                        ChronoUnit.DAYS.between(
                                LocalDateTime.now().toLocalDate(),
                                member.restoreDeadline().toLocalDate()));
        return new BusinessException(
                ErrorCode.ACCOUNT_WITHDRAWN, "탈퇴 신청한 계정이에요. 복원할까요? (" + daysLeft + "일 남음)");
    }
}
