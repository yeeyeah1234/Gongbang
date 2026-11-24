package com.gongbang.member.service;

import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.global.validation.UrlPolicy;
import com.gongbang.member.domain.Award;
import com.gongbang.member.domain.Career;
import com.gongbang.member.domain.Certificate;
import com.gongbang.member.domain.VerificationRequest;
import com.gongbang.member.domain.VerifyStatus;
import com.gongbang.member.dto.AwardRequest;
import com.gongbang.member.dto.AwardResponse;
import com.gongbang.member.dto.CareerRequest;
import com.gongbang.member.dto.CareerResponse;
import com.gongbang.member.dto.CertificateRequest;
import com.gongbang.member.dto.CertificateResponse;
import com.gongbang.member.dto.VerifyRequest;
import com.gongbang.member.repository.AwardRepository;
import com.gongbang.member.repository.CareerRepository;
import com.gongbang.member.repository.CertificateRepository;
import com.gongbang.member.repository.VerificationRequestRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** MY-07 경력, MY-08 자격증, MY-09 수상 경력 */
@Service
@RequiredArgsConstructor
public class HistoryService {

    private final CareerRepository careerRepository;
    private final CertificateRepository certificateRepository;
    private final AwardRepository awardRepository;
    private final VerificationRequestRepository verificationRequestRepository;
    private final RealNameCipher realNameCipher;

    // ---------- MY-07 ----------

    @Transactional(readOnly = true)
    public List<CareerResponse> getCareers(Long memberId) {
        return careerRepository.findByMemberIdOrderByStartDateDescIdDesc(memberId).stream()
                .map(CareerResponse::from)
                .toList();
    }

    @Transactional
    public CareerResponse addCareer(Long memberId, CareerRequest request) {
        if (careerRepository.countByMemberId(memberId) >= Career.MAX_PER_MEMBER) {
            throw new BusinessException(ErrorCode.CAREER_LIMIT);
        }
        Career career = new Career(memberId);
        applyCareer(career, request);
        return CareerResponse.from(careerRepository.save(career));
    }

    @Transactional
    public CareerResponse updateCareer(Long memberId, Long careerId, CareerRequest request) {
        Career career = careerRepository.findById(careerId).orElseThrow(this::notFound);
        checkOwner(memberId, career.getMemberId());
        applyCareer(career, request);
        return CareerResponse.from(career);
    }

    @Transactional
    public void deleteCareer(Long memberId, Long careerId) {
        Career career = careerRepository.findById(careerId).orElseThrow(this::notFound);
        checkOwner(memberId, career.getMemberId());
        careerRepository.delete(career);
    }

    private void applyCareer(Career career, CareerRequest request) {
        if (request.endDate() != null && request.endDate().isBefore(request.startDate())) {
            // MY-07 ③ 문구
            throw new BusinessException(ErrorCode.INVALID_PERIOD, "종료일은 시작일 이후여야 해요");
        }
        career.update(
                request.organization().trim(),
                request.role().trim(),
                request.startDate(),
                request.endDate(),
                request.description(),
                UrlPolicy.validateOrNull(request.linkUrl()));
    }

    // ---------- MY-08 ----------

    @Transactional(readOnly = true)
    public List<CertificateResponse> getCertificates(Long memberId) {
        return certificateRepository.findByMemberIdOrderByAcquiredDateDescIdDesc(memberId).stream()
                .map(CertificateResponse::from)
                .toList();
    }

    @Transactional
    public CertificateResponse addCertificate(Long memberId, CertificateRequest request) {
        Certificate certificate = new Certificate(memberId);
        applyCertificate(certificate, request);
        return CertificateResponse.from(certificateRepository.save(certificate));
    }

    @Transactional
    public CertificateResponse updateCertificate(
            Long memberId, Long certificateId, CertificateRequest request) {
        Certificate certificate =
                certificateRepository.findById(certificateId).orElseThrow(this::notFound);
        checkOwner(memberId, certificate.getMemberId());
        // ⑥ 고치면 인증이 풀린다 (update 안에서 NONE)
        applyCertificate(certificate, request);
        return CertificateResponse.from(certificate);
    }

    @Transactional
    public void deleteCertificate(Long memberId, Long certificateId) {
        Certificate certificate =
                certificateRepository.findById(certificateId).orElseThrow(this::notFound);
        checkOwner(memberId, certificate.getMemberId());
        certificateRepository.delete(certificate);
    }

    @Transactional
    public VerifyStatus requestCertificateVerify(
            Long memberId, Long certificateId, VerifyRequest request) {
        Certificate certificate =
                certificateRepository.findById(certificateId).orElseThrow(this::notFound);
        checkOwner(memberId, certificate.getMemberId());
        if (certificate.getVerifyStatus() == VerifyStatus.PENDING) {
            throw new BusinessException(ErrorCode.ALREADY_PENDING);
        }
        Long proofFileId = requireProof(request.proofFileId(), certificate.getProofFileId());
        certificate.requestVerify(proofFileId);
        verificationRequestRepository.save(
                new VerificationRequest(
                        memberId,
                        VerificationRequest.TargetType.CERTIFICATE,
                        certificateId,
                        realNameCipher.encrypt(request.realName().trim()),
                        proofFileId));
        return certificate.getVerifyStatus();
    }

    private void applyCertificate(Certificate certificate, CertificateRequest request) {
        checkNotFuture(request.acquiredDate());
        certificate.update(
                request.name().trim(),
                request.issuer().trim(),
                request.acquiredDate(),
                request.certNumber(),
                request.proofFileId());
    }

    // ---------- MY-09 ----------

    @Transactional(readOnly = true)
    public List<AwardResponse> getAwards(Long memberId) {
        return awardRepository.findByMemberIdOrderByAwardedDateDescIdDesc(memberId).stream()
                .map(AwardResponse::from)
                .toList();
    }

    @Transactional
    public AwardResponse addAward(Long memberId, AwardRequest request) {
        Award award = new Award(memberId);
        applyAward(award, request);
        return AwardResponse.from(awardRepository.save(award));
    }

    @Transactional
    public AwardResponse updateAward(Long memberId, Long awardId, AwardRequest request) {
        Award award = awardRepository.findById(awardId).orElseThrow(this::notFound);
        checkOwner(memberId, award.getMemberId());
        applyAward(award, request);
        return AwardResponse.from(award);
    }

    @Transactional
    public void deleteAward(Long memberId, Long awardId) {
        Award award = awardRepository.findById(awardId).orElseThrow(this::notFound);
        checkOwner(memberId, award.getMemberId());
        awardRepository.delete(award);
    }

    @Transactional
    public VerifyStatus requestAwardVerify(Long memberId, Long awardId, VerifyRequest request) {
        Award award = awardRepository.findById(awardId).orElseThrow(this::notFound);
        checkOwner(memberId, award.getMemberId());
        if (award.getVerifyStatus() == VerifyStatus.PENDING) {
            throw new BusinessException(ErrorCode.ALREADY_PENDING);
        }
        Long proofFileId = requireProof(request.proofFileId(), award.getProofFileId());
        award.requestVerify(proofFileId);
        verificationRequestRepository.save(
                new VerificationRequest(
                        memberId,
                        VerificationRequest.TargetType.AWARD,
                        awardId,
                        realNameCipher.encrypt(request.realName().trim()),
                        proofFileId));
        return award.getVerifyStatus();
    }

    private void applyAward(Award award, AwardRequest request) {
        checkNotFuture(request.awardedDate());
        award.update(
                request.contestName().trim(),
                request.awardName().trim(),
                request.awardedDate(),
                request.proofFileId());
    }

    // ---------- 공통 ----------

    private static Long requireProof(Long requested, Long saved) {
        Long proof = requested != null ? requested : saved;
        if (proof == null) {
            throw new BusinessException(ErrorCode.PROOF_REQUIRED);
        }
        return proof;
    }

    private static void checkNotFuture(LocalDate date) {
        if (date.isAfter(LocalDate.now())) {
            throw new BusinessException(ErrorCode.FUTURE_DATE);
        }
    }

    private static void checkOwner(Long memberId, Long ownerId) {
        if (!ownerId.equals(memberId)) {
            throw new BusinessException(ErrorCode.NOT_OWNER);
        }
    }

    private BusinessException notFound() {
        return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
    }
}
