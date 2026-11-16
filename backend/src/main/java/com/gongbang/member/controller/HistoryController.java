package com.gongbang.member.controller;

import com.gongbang.auth.domain.LoginMember;
import com.gongbang.global.common.ApiResponse;
import com.gongbang.member.dto.AwardRequest;
import com.gongbang.member.dto.AwardResponse;
import com.gongbang.member.dto.CareerRequest;
import com.gongbang.member.dto.CareerResponse;
import com.gongbang.member.dto.CertificateRequest;
import com.gongbang.member.dto.CertificateResponse;
import com.gongbang.member.dto.VerifyRequest;
import com.gongbang.member.service.HistoryService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** MY-07·08·09 이력 */
@RestController
@RequestMapping("/api/members/me")
@RequiredArgsConstructor
public class HistoryController {

    private final HistoryService historyService;

    @GetMapping("/careers")
    public ApiResponse<List<CareerResponse>> careers(@AuthenticationPrincipal LoginMember me) {
        return ApiResponse.success(historyService.getCareers(me.getMemberId()));
    }

    @PostMapping("/careers")
    public ApiResponse<CareerResponse> addCareer(
            @AuthenticationPrincipal LoginMember me, @Valid @RequestBody CareerRequest request) {
        return ApiResponse.success(historyService.addCareer(me.getMemberId(), request));
    }

    @PatchMapping("/careers/{careerId}")
    public ApiResponse<CareerResponse> updateCareer(
            @AuthenticationPrincipal LoginMember me,
            @PathVariable Long careerId,
            @Valid @RequestBody CareerRequest request) {
        return ApiResponse.success(
                historyService.updateCareer(me.getMemberId(), careerId, request));
    }

    @DeleteMapping("/careers/{careerId}")
    public ApiResponse<Void> deleteCareer(
            @AuthenticationPrincipal LoginMember me, @PathVariable Long careerId) {
        historyService.deleteCareer(me.getMemberId(), careerId);
        return ApiResponse.success();
    }

    @GetMapping("/certificates")
    public ApiResponse<List<CertificateResponse>> certificates(
            @AuthenticationPrincipal LoginMember me) {
        return ApiResponse.success(historyService.getCertificates(me.getMemberId()));
    }

    @PostMapping("/certificates")
    public ApiResponse<CertificateResponse> addCertificate(
            @AuthenticationPrincipal LoginMember me,
            @Valid @RequestBody CertificateRequest request) {
        return ApiResponse.success(historyService.addCertificate(me.getMemberId(), request));
    }

    @PatchMapping("/certificates/{certificateId}")
    public ApiResponse<CertificateResponse> updateCertificate(
            @AuthenticationPrincipal LoginMember me,
            @PathVariable Long certificateId,
            @Valid @RequestBody CertificateRequest request) {
        return ApiResponse.success(
                historyService.updateCertificate(me.getMemberId(), certificateId, request));
    }

    @DeleteMapping("/certificates/{certificateId}")
    public ApiResponse<Void> deleteCertificate(
            @AuthenticationPrincipal LoginMember me, @PathVariable Long certificateId) {
        historyService.deleteCertificate(me.getMemberId(), certificateId);
        return ApiResponse.success();
    }

    @PostMapping("/certificates/{certificateId}/verify")
    public ApiResponse<Map<String, String>> verifyCertificate(
            @AuthenticationPrincipal LoginMember me,
            @PathVariable Long certificateId,
            @Valid @RequestBody VerifyRequest request) {
        return ApiResponse.success(
                Map.of(
                        "verifyStatus",
                        historyService
                                .requestCertificateVerify(me.getMemberId(), certificateId, request)
                                .name()));
    }

    @GetMapping("/awards")
    public ApiResponse<List<AwardResponse>> awards(@AuthenticationPrincipal LoginMember me) {
        return ApiResponse.success(historyService.getAwards(me.getMemberId()));
    }

    @PostMapping("/awards")
    public ApiResponse<AwardResponse> addAward(
            @AuthenticationPrincipal LoginMember me, @Valid @RequestBody AwardRequest request) {
        return ApiResponse.success(historyService.addAward(me.getMemberId(), request));
    }

    @PatchMapping("/awards/{awardId}")
    public ApiResponse<AwardResponse> updateAward(
            @AuthenticationPrincipal LoginMember me,
            @PathVariable Long awardId,
            @Valid @RequestBody AwardRequest request) {
        return ApiResponse.success(historyService.updateAward(me.getMemberId(), awardId, request));
    }

    @DeleteMapping("/awards/{awardId}")
    public ApiResponse<Void> deleteAward(
            @AuthenticationPrincipal LoginMember me, @PathVariable Long awardId) {
        historyService.deleteAward(me.getMemberId(), awardId);
        return ApiResponse.success();
    }

    @PostMapping("/awards/{awardId}/verify")
    public ApiResponse<Map<String, String>> verifyAward(
            @AuthenticationPrincipal LoginMember me,
            @PathVariable Long awardId,
            @Valid @RequestBody VerifyRequest request) {
        return ApiResponse.success(
                Map.of(
                        "verifyStatus",
                        historyService
                                .requestAwardVerify(me.getMemberId(), awardId, request)
                                .name()));
    }
}
