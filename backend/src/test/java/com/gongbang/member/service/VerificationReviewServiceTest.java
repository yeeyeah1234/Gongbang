package com.gongbang.member.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.domain.*;
import com.gongbang.member.repository.*;
import com.gongbang.notification.domain.NotificationType;
import com.gongbang.notification.service.NotificationService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class VerificationReviewServiceTest {
    final VerificationRequestRepository requests = mock(VerificationRequestRepository.class);
    final CertificateRepository certificates = mock(CertificateRepository.class);
    final AwardRepository awards = mock(AwardRepository.class);
    final MemberService members = mock(MemberService.class);
    final NotificationService notifications = mock(NotificationService.class);
    final com.gongbang.file.service.ProofFileService proofFiles =
            mock(com.gongbang.file.service.ProofFileService.class);
    final RealNameCipher cipher = mock(RealNameCipher.class);
    final VerificationReviewService service =
            new VerificationReviewService(
                    org.mockito.Mockito.mock(com.gongbang.admin.service.AdminActivityWriter.class),
                    requests,
                    certificates,
                    awards,
                    members,
                    notifications,
                    proofFiles,
                    cipher);

    @BeforeEach
    void admin() {
        Member admin = mock(Member.class);
        when(admin.getRole()).thenReturn(MemberRole.ADMIN);
        when(members.getActiveMember(1L)).thenReturn(admin);
    }

    VerificationRequest pending() {
        var request =
                new VerificationRequest(
                        2L, VerificationRequest.TargetType.CERTIFICATE, 3L, "encrypted", 4L);
        ReflectionTestUtils.setField(request, "id", 5L);
        when(requests.findForReview(5L)).thenReturn(Optional.of(request));
        when(requests.findFirstByMemberIdAndTargetTypeAndTargetIdOrderByIdDesc(
                        2L, request.getTargetType(), 3L))
                .thenReturn(Optional.of(request));
        return request;
    }

    @Test
    void 일반회원은_목록과심사를_할수없다() {
        when(members.getActiveMember(1L).getRole()).thenReturn(MemberRole.USER);
        assertThatThrownBy(() -> service.list(1L, VerifyStatus.PENDING, 0))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.review(1L, 5L, VerifyStatus.REJECTED, "사유"))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(requests);
    }

    @Test
    void 반려는_이력을갱신하고_실명을제거하고_알림을보낸다() {
        var request = pending();
        var certificate = new Certificate(2L);
        certificate.requestVerify(4L);
        when(certificates.findForUpdate(3L)).thenReturn(Optional.of(certificate));
        service.review(1L, 5L, VerifyStatus.REJECTED, "  발급일 확인 필요  ");
        assertThat(request.getRealName()).isNull();
        assertThat(request.getRejectReason()).isEqualTo("발급일 확인 필요");
        assertThat(request.getReviewerId()).isEqualTo(1L);
        assertThat(request.getReviewedAt()).isNotNull();
        assertThat(certificate.getVerifyStatus()).isEqualTo(VerifyStatus.REJECTED);
        verify(notifications)
                .send(
                        2L,
                        NotificationType.VERIFY_RESULT,
                        null,
                        "CERTIFICATE",
                        3L,
                        "인증 요청이 반려되었어요. 사유: 발급일 확인 필요",
                        "/mypage/profile");
    }

    @Test
    void 중복심사는_알림을다시보내지않는다() {
        var request = pending();
        request.review(VerifyStatus.REJECTED, "사유", 1L);
        assertThatThrownBy(() -> service.review(1L, 5L, VerifyStatus.REJECTED, "사유"))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(notifications);
    }

    @Test
    void 실명을확인할수없으면_승인할수없다() {
        var request = pending();
        assertThatThrownBy(() -> service.review(1L, 5L, VerifyStatus.VERIFIED, null))
                .isInstanceOf(BusinessException.class);
        assertThat(request.getStatus()).isEqualTo(VerifyStatus.PENDING);
        verifyNoInteractions(notifications);
    }

    @Test
    void 반려사유는_필수이며_200자이하다() {
        for (String reason : new String[] {"  ", "가".repeat(201)}) {
            assertThatThrownBy(() -> service.review(1L, 5L, VerifyStatus.REJECTED, reason))
                    .isInstanceOf(BusinessException.class);
        }
        verifyNoInteractions(requests);
    }

    @Test
    void 이전요청반려가_새요청이력을_덮어쓰지않는다() {
        var request = pending();
        var newer = new VerificationRequest(2L, request.getTargetType(), 3L, "encrypted", 4L);
        ReflectionTestUtils.setField(newer, "id", 6L);
        when(requests.findFirstByMemberIdAndTargetTypeAndTargetIdOrderByIdDesc(
                        2L, request.getTargetType(), 3L))
                .thenReturn(Optional.of(newer));
        service.review(1L, 5L, VerifyStatus.REJECTED, "이전 요청");
        verify(certificates).findForUpdate(3L);
        verifyNoMoreInteractions(certificates);
        verifyNoInteractions(awards);
    }

    @Test
    void 승인시_이력인증과_실명삭제와_결과알림을처리한다() {
        var request = pending();
        var certificate = new Certificate(2L);
        certificate.requestVerify(4L);
        when(certificates.findForUpdate(3L)).thenReturn(Optional.of(certificate));
        when(cipher.decrypt("encrypted")).thenReturn("테스트 이름");
        service.review(1L, 5L, VerifyStatus.VERIFIED, null);
        assertThat(certificate.getVerifyStatus()).isEqualTo(VerifyStatus.VERIFIED);
        assertThat(request.getStatus()).isEqualTo(VerifyStatus.VERIFIED);
        assertThat(request.getRealName()).isNull();
        verify(proofFiles).requireOwned(4L, 2L);
        verify(proofFiles).read(4L, 1L);
        verify(notifications)
                .send(
                        2L,
                        NotificationType.VERIFY_RESULT,
                        null,
                        "CERTIFICATE",
                        3L,
                        "인증 요청이 승인되었어요.",
                        "/mypage/profile");
    }

    @Test
    void 이력이수정되어_인증이풀린요청은_승인하지않는다() {
        var request = pending();
        when(cipher.decrypt("encrypted")).thenReturn("테스트 이름");
        when(certificates.findForUpdate(3L)).thenReturn(Optional.of(new Certificate(2L)));
        assertThatThrownBy(() -> service.review(1L, 5L, VerifyStatus.VERIFIED, null))
                .isInstanceOf(BusinessException.class);
        assertThat(request.getStatus()).isEqualTo(VerifyStatus.PENDING);
        verifyNoInteractions(notifications);
    }
}
