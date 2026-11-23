package com.gongbang.post.service;

import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.post.domain.RecruitPost;
import com.gongbang.post.domain.RecruitPostImage;
import com.gongbang.post.dto.PostImageResponse;
import com.gongbang.post.dto.PostImagesResponse;
import com.gongbang.post.repository.RecruitPostImageRepository;
import com.gongbang.post.repository.RecruitPostRepository;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * PST-007 외부 공모전 사진 올리기·삭제·순서 변경. 작성자만 할 수 있고(403 NOT_POST_AUTHOR), 외부 공모전 모집글에만 올릴 수 있다 (①). 사진
 * 개수와 순서는 모집글 행을 잠근 뒤(SELECT … FOR UPDATE) 바꿔 동시에 올려도 3장을 넘지 않는다 (AGENTS.md 7).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostImageService {

    // ② 장당 10MB 이하, 최대 3장
    static final long MAX_BYTES = 10L * 1024 * 1024;
    static final int MAX_IMAGES = 3;

    // PST-007 ② 문구
    static final String TYPE_MESSAGE = "JPG·PNG·WebP·GIF·AVIF 사진만 올릴 수 있어요";
    static final String SIZE_MESSAGE = "사진은 10MB 이하만 올릴 수 있어요";
    static final String EXTERNAL_ONLY_MESSAGE = "외부 공모전 모집글에만 사진을 올릴 수 있어요";
    static final String STORE_FAILED_MESSAGE = "사진을 올리지 못했어요. 잠시 후 다시 시도해 주세요";

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final RecruitPostRepository recruitPostRepository;
    private final RecruitPostImageRepository recruitPostImageRepository;
    private final PostImageStorage storage;

    /** 상세(PST-005)용: 삭제되지 않은 사진을 보여 주는 순서대로. */
    @Transactional(readOnly = true)
    public List<PostImageResponse> findImages(Long postId) {
        return toResponses(activeImages(postId));
    }

    /** ②③: 한 장을 맨 뒤에 붙인다. 형식·크기는 파일을 저장하기 전에 확인한다. */
    @Transactional
    public PostImagesResponse upload(Long memberId, Long postId, MultipartFile file) {
        RecruitPost post = lockOwnPost(memberId, postId);
        if (post.getExternalContest() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, EXTERNAL_ONLY_MESSAGE);
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, SIZE_MESSAGE);
        }
        PostImageType type =
                PostImageType.detect(readHead(file))
                        .orElseThrow(
                                () -> new BusinessException(ErrorCode.INVALID_INPUT, TYPE_MESSAGE));

        List<RecruitPostImage> images = activeImages(postId);
        if (images.size() >= MAX_IMAGES) {
            throw new BusinessException(ErrorCode.POST_IMAGE_LIMIT);
        }

        String url = store(postId, type, file);
        // DB 저장이 되돌려지면 방금 저장한 파일을 지운다 (남은 사진이 없는 파일을 가리키지 않게)
        deleteFileOnRollback(url);
        RecruitPostImage saved =
                recruitPostImageRepository.save(RecruitPostImage.of(postId, url, images.size()));
        images.add(saved);
        return new PostImagesResponse(toResponses(images));
    }

    /** ④: 숨김 처리(소프트 삭제)하고 파일은 남긴다. 남은 사진 순서는 0부터 다시 매긴다. */
    @Transactional
    public PostImagesResponse delete(Long memberId, Long postId, Long imageId) {
        lockOwnPost(memberId, postId);
        List<RecruitPostImage> images = activeImages(postId);
        RecruitPostImage target =
                images.stream()
                        .filter(image -> image.getId().equals(imageId))
                        .findFirst()
                        .orElseThrow(() -> new BusinessException(ErrorCode.POST_IMAGE_NOT_FOUND));
        target.delete(LocalDateTime.now(KST));
        images.remove(target);
        renumber(images);
        return new PostImagesResponse(toResponses(images));
    }

    /** ③: 남아 있는 사진 ID 전부를 새 순서로 받는다. 빠지거나 겹치거나 다른 사진이 섞이면 400. */
    @Transactional
    public PostImagesResponse reorder(Long memberId, Long postId, List<Long> imageIds) {
        lockOwnPost(memberId, postId);
        List<RecruitPostImage> images = activeImages(postId);
        if (imageIds == null
                || imageIds.size() != images.size()
                || new HashSet<>(imageIds).size() != imageIds.size()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        Map<Long, RecruitPostImage> byId =
                images.stream()
                        .collect(Collectors.toMap(RecruitPostImage::getId, Function.identity()));
        if (!byId.keySet().equals(Set.copyOf(imageIds))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        List<RecruitPostImage> ordered = imageIds.stream().map(byId::get).toList();
        renumber(ordered);
        return new PostImagesResponse(toResponses(ordered));
    }

    /** 모집글 행을 잠그고, 없거나 삭제된 글이면 404, 작성자가 아니면 403. */
    private RecruitPost lockOwnPost(Long memberId, Long postId) {
        RecruitPost post =
                recruitPostRepository
                        .findByIdForUpdate(postId)
                        .filter(found -> found.getDeletedAt() == null)
                        .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        if (!post.getAuthorId().equals(memberId)) {
            throw new BusinessException(ErrorCode.NOT_POST_AUTHOR);
        }
        return post;
    }

    private List<RecruitPostImage> activeImages(Long postId) {
        // 순서를 바꾸고 붙이므로 고칠 수 있는 목록으로 받는다
        return new ArrayList<>(
                recruitPostImageRepository.findByPostIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(
                        postId));
    }

    private static void renumber(List<RecruitPostImage> images) {
        for (int i = 0; i < images.size(); i++) {
            images.get(i).changeSortOrder(i);
        }
    }

    private static List<PostImageResponse> toResponses(List<RecruitPostImage> images) {
        return images.stream().map(PostImageResponse::from).toList();
    }

    private static byte[] readHead(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(PostImageType.HEAD_BYTES);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, STORE_FAILED_MESSAGE);
        }
    }

    private String store(Long postId, PostImageType type, MultipartFile file) {
        try {
            return storage.store(postId, type, file);
        } catch (IOException | RuntimeException e) {
            log.warn("Post image upload failed postId={}", postId);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, STORE_FAILED_MESSAGE);
        }
    }

    private void deleteFileOnRollback(String url) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status == STATUS_ROLLED_BACK) {
                            deleteQuietly(url);
                        }
                    }
                });
    }

    private void deleteQuietly(String url) {
        try {
            storage.delete(url);
        } catch (IOException | RuntimeException e) {
            log.warn("Post image cleanup failed");
        }
    }
}
