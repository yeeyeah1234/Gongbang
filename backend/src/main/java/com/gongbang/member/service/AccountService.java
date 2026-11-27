package com.gongbang.member.service;

import com.gongbang.auth.service.AuthService;
import com.gongbang.auth.service.PasswordPolicy;
import com.gongbang.chat.service.ChatMembershipService;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.domain.Member;
import com.gongbang.member.domain.MemberStatus;
import com.gongbang.member.repository.MemberRepository;
import com.gongbang.notification.service.EmailOutboxService;
import com.gongbang.team.service.TeamWithdrawalService;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** MY-04 비밀번호 변경, MY-11 탈퇴·완전 삭제 */
@Service
@RequiredArgsConstructor
public class AccountService {

    /** MY-04 ③: 5번 틀리면 로그아웃 */
    private static final int PASSWORD_CHANGE_FAIL_LIMIT = 5;

    private static final String CONFIRM_TEXT = "탈퇴합니다";

    private final TeamWithdrawalService teamWithdrawal;
    private final ChatMembershipService chatMembership;
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

    /** MY-11 ①~④: 팀 나가기·회원 상태·토큰·메일은 전부 성공하거나 전부 롤백한다. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
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
        var plan = teamWithdrawal.prepare(memberId);
        member = plan.member();
        // 잠금을 기다리는 동안 비밀번호가 바뀐 경우도 기존 비밀번호로 탈퇴하지 못한다.
        if (member.isEmailMember()
                && (password == null
                        || !passwordEncoder.matches(password, member.getPasswordHash())))
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        teamWithdrawal.leaveAll(plan);
        chatMembership.removeMemberFromAllRooms(memberId);
        LocalDateTime now = LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul"));
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
