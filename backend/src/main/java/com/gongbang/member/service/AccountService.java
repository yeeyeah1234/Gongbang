package com.gongbang.member.service;

import com.gongbang.auth.service.AuthService;
import com.gongbang.auth.service.PasswordPolicy;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.domain.Member;
import com.gongbang.member.domain.MemberStatus;
import com.gongbang.member.repository.MemberRepository;
import com.gongbang.notification.service.EmailOutboxService;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** MY-04 비밀번호 변경, MY-11 탈퇴·완전 삭제 */
@Service
@RequiredArgsConstructor
public class AccountService {

    /** MY-04 ③: 5번 틀리면 로그아웃 */
    private static final int PASSWORD_CHANGE_FAIL_LIMIT = 5;

    private static final String CONFIRM_TEXT = "탈퇴합니다";

    private final MemberService memberService;
    private final MemberRepository memberRepository;
    private final AuthService authService;
    private final PasswordEncoder passwordEncoder;
    private final EmailOutboxService emailOutboxService;

    /** MY-04 */
    @Transactional(noRollbackFor = BusinessException.class)
    public void changePassword(
            Long memberId, String currentPassword, String newPassword, String rawRefreshToken) {
        Member member = memberService.getActiveMember(memberId);
        if (!member.isEmailMember()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "카카오로 가입한 계정은 카카오에서 비밀번호를 관리해요");
        }
        if (!passwordEncoder.matches(currentPassword, member.getPasswordHash())) {
            int failCount = countFail(memberId);
            if (failCount >= PASSWORD_CHANGE_FAIL_LIMIT) {
                // ③ 로그아웃시키고 비밀번호 찾기로 안내 (프론트가 401 뒤 로그인 화면)
                authService.revokeAllDevices(memberId);
                throw new BusinessException(
                        ErrorCode.UNAUTHORIZED, "비밀번호를 5번 틀려 로그아웃됐어요. 비밀번호 찾기로 다시 설정해 주세요");
            }
            throw new BusinessException(
                    ErrorCode.PASSWORD_MISMATCH, "현재 비밀번호가 맞지 않아요 (5번 중 " + failCount + "번)");
        }
        PasswordPolicy.validate(newPassword);
        if (passwordEncoder.matches(newPassword, member.getPasswordHash())) {
            throw new BusinessException(ErrorCode.SAME_AS_OLD_PASSWORD);
        }
        passwordFailCounts.remove(memberId);
        member.changePassword(passwordEncoder.encode(newPassword));
        authService.revokeOtherDevices(memberId, rawRefreshToken);
        emailOutboxService.enqueuePasswordChanged(member);
    }

    /** MY-11 ①~④ */
    @Transactional(noRollbackFor = BusinessException.class)
    public LocalDateTime withdraw(Long memberId, String password, String confirmText) {
        Member member = memberService.getActiveMember(memberId);
        if (!CONFIRM_TEXT.equals(confirmText == null ? null : confirmText.trim())) {
            throw new BusinessException(ErrorCode.CONFIRM_TEXT_MISMATCH);
        }
        if (member.isEmailMember()
                && (password == null
                        || !passwordEncoder.matches(password, member.getPasswordHash()))) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH, "비밀번호가 맞지 않아요");
        }
        // TODO(확인 필요): ③ 팀장인 채팅방이 있으면 409 LEADER_CANNOT_WITHDRAW.
        //  team·team_member(이상현 TeamMemberService)가 develop에 들어오면 「팀장인지」 확인을 붙인다.
        // TODO(확인 필요): ④ 참여 중인 채팅방·팀에서 나가기 = 이상현 「팀원 변동 처리」 연동
        LocalDateTime now = LocalDateTime.now();
        member.withdraw(now);
        authService.revokeAllDevices(memberId);
        emailOutboxService.enqueueWithdrawn(member);
        return member.restoreDeadline();
    }

    /** MY-11: 30일 지난 계정은 매일 1회 완전 삭제 (새벽 4시) */
    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    @Transactional
    public void eraseExpiredWithdrawals() {
        LocalDateTime limit = LocalDateTime.now().minusDays(Member.RESTORE_DAYS);
        for (Member member :
                memberRepository.findByStatusAndWithdrawnAtBefore(MemberStatus.WITHDRAWN, limit)) {
            memberService.eraseMember(member);
        }
    }

    /** MY-04 ③ 현재 비밀번호 틀린 횟수 (서버 메모리, 성공하거나 5번이 되면 지운다) */
    private final Map<Long, Integer> passwordFailCounts = new ConcurrentHashMap<>();

    private int countFail(Long memberId) {
        int count = passwordFailCounts.merge(memberId, 1, Integer::sum);
        if (count >= PASSWORD_CHANGE_FAIL_LIMIT) {
            passwordFailCounts.remove(memberId);
        }
        return count;
    }
}
