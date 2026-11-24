package com.gongbang.member.service;

import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.domain.*;
import com.gongbang.member.repository.*;
import com.gongbang.notification.domain.NotificationType;
import com.gongbang.notification.service.NotificationService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** ADM-01: 인증 요청 심사. 증빙과 최신 이력의 일치를 확인한다. */
@Service
@RequiredArgsConstructor
public class VerificationReviewService {
    private final VerificationRequestRepository requests;
    private final CertificateRepository certificates;
    private final AwardRepository awards;
    private final MemberService members;
    private final NotificationService notifications;
    private final com.gongbang.file.service.ProofFileService proofFiles;
    private final RealNameCipher cipher;

    public record Detail(Item item, String realName, boolean nameAvailable) {}

    @Transactional(readOnly = true)
    public Detail detail(Long adminId, Long requestId) {
        requireAdmin(adminId);
        var request =
                requests.findById(requestId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        String name = null;
        if (request.getStatus() == VerifyStatus.PENDING) {
            try {
                name = cipher.decrypt(request.getRealName());
            } catch (IllegalStateException ignored) {
                /* 이전 임시 키 데이터는 재제출 필요 */
            }
        }
        return new Detail(item(request), name, name != null);
    }

    public record Item(
            Long id,
            Long memberId,
            String nickname,
            String targetType,
            Long targetId,
            String title,
            String description,
            Long proofFileId,
            String status,
            String rejectReason,
            Long reviewerId,
            LocalDateTime requestedAt,
            LocalDateTime reviewedAt,
            boolean proofAvailable) {}

    public record Result(List<Item> content, long totalElements, int totalPages, int number) {}

    private void requireAdmin(Long memberId) {
        if (members.getActiveMember(memberId).getRole() != MemberRole.ADMIN) {
            throw new BusinessException(ErrorCode.NOT_ADMIN);
        }
    }

    @Transactional(readOnly = true)
    public Result list(Long adminId, VerifyStatus status, int page) {
        requireAdmin(adminId);
        if (page < 0 || status == VerifyStatus.NONE)
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        var result =
                requests.findByStatus(
                        status,
                        PageRequest.of(page, 20, Sort.by("requestedAt", "id").descending()));
        return new Result(
                result.getContent().stream().map(this::item).toList(),
                result.getTotalElements(),
                result.getTotalPages(),
                page);
    }

    private Item item(VerificationRequest request) {
        String title = "삭제된 이력";
        String description = "현재 이력 정보를 찾을 수 없습니다.";
        if (request.getTargetType() == VerificationRequest.TargetType.CERTIFICATE) {
            var target = certificates.findById(request.getTargetId()).orElse(null);
            if (target != null && Objects.equals(target.getMemberId(), request.getMemberId())) {
                title = target.getName();
                description = target.getIssuer() + " · " + target.getAcquiredDate();
            }
        } else {
            var target = awards.findById(request.getTargetId()).orElse(null);
            if (target != null && Objects.equals(target.getMemberId(), request.getMemberId())) {
                title = target.getContestName();
                description = target.getAwardName() + " · " + target.getAwardedDate();
            }
        }
        return new Item(
                request.getId(),
                request.getMemberId(),
                members.displayName(request.getMemberId()),
                request.getTargetType().name(),
                request.getTargetId(),
                title,
                description,
                request.getProofFileId(),
                request.getStatus().name(),
                request.getRejectReason(),
                request.getReviewerId(),
                request.getRequestedAt(),
                request.getReviewedAt(),
                proofFiles.available(request.getProofFileId(), request.getMemberId()));
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void review(Long adminId, Long requestId, VerifyStatus status, String reason) {
        requireAdmin(adminId);
        if (status != VerifyStatus.VERIFIED && status != VerifyStatus.REJECTED)
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        String trimmed = reason == null ? "" : reason.strip();
        if (status == VerifyStatus.REJECTED && (trimmed.isBlank() || trimmed.length() > 200)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "반려 사유를 1~200자로 입력해 주세요");
        }
        var request =
                requests.findForReview(requestId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (request.getStatus() != VerifyStatus.PENDING)
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이미 심사가 완료된 요청입니다");
        if (status == VerifyStatus.VERIFIED) {
            proofFiles.requireOwned(request.getProofFileId(), request.getMemberId());
            proofFiles.read(request.getProofFileId(), adminId);
            try {
                if (cipher.decrypt(request.getRealName()) == null)
                    throw new IllegalStateException();
            } catch (IllegalStateException e) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "실명을 확인할 수 없어 재제출이 필요합니다");
            }
        }
        // 삭제되거나 수정된 예전 요청도 반려해 실명을 제거하되 새 이력 상태는 덮어쓰지 않는다.
        var certificate =
                request.getTargetType() == VerificationRequest.TargetType.CERTIFICATE
                        ? certificates.findForUpdate(request.getTargetId())
                        : java.util.Optional.<Certificate>empty();
        var award =
                request.getTargetType() == VerificationRequest.TargetType.AWARD
                        ? awards.findForUpdate(request.getTargetId())
                        : java.util.Optional.<Award>empty();
        boolean latest =
                requests.findFirstByMemberIdAndTargetTypeAndTargetIdOrderByIdDesc(
                                request.getMemberId(),
                                request.getTargetType(),
                                request.getTargetId())
                        .map(v -> Objects.equals(v.getId(), request.getId()))
                        .orElse(false);
        boolean targetUpdated = false;
        if (latest && request.getTargetType() == VerificationRequest.TargetType.CERTIFICATE) {
            var target =
                    certificate
                            .filter(
                                    c ->
                                            Objects.equals(c.getMemberId(), request.getMemberId())
                                                    && c.getVerifyStatus() == VerifyStatus.PENDING
                                                    && Objects.equals(
                                                            c.getProofFileId(),
                                                            request.getProofFileId()))
                            .orElse(null);
            if (target != null) {
                target.review(status);
                targetUpdated = true;
            }
        } else if (latest) {
            var target =
                    award.filter(
                                    a ->
                                            Objects.equals(a.getMemberId(), request.getMemberId())
                                                    && a.getVerifyStatus() == VerifyStatus.PENDING
                                                    && Objects.equals(
                                                            a.getProofFileId(),
                                                            request.getProofFileId()))
                            .orElse(null);
            if (target != null) {
                target.review(status);
                targetUpdated = true;
            }
        }
        if (status == VerifyStatus.VERIFIED && !targetUpdated)
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이력이 변경되었거나 삭제되어 다시 요청해야 합니다");
        request.review(status, trimmed, adminId);
        notifications.send(
                request.getMemberId(),
                NotificationType.VERIFY_RESULT,
                null,
                request.getTargetType().name(),
                request.getTargetId(),
                status == VerifyStatus.VERIFIED
                        ? "인증 요청이 승인되었어요."
                        : "인증 요청이 반려되었어요. 사유: " + trimmed,
                "/mypage/profile");
    }
}
