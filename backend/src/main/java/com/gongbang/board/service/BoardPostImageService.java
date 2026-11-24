package com.gongbang.board.service;

import com.gongbang.board.domain.BoardCategory;
import com.gongbang.board.domain.BoardPost;
import com.gongbang.board.domain.BoardPostImage;
import com.gongbang.board.dto.BoardPostDetailResponse.ImageInfo;
import com.gongbang.board.dto.BoardPostImagesResponse;
import com.gongbang.board.repository.BoardPostImageRepository;
import com.gongbang.board.repository.BoardPostRepository;
import com.gongbang.file.service.AllowedImageType;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.post.service.ImageFolder;
import com.gongbang.post.service.PostImageUploader;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * BRD-006 결과물 사진 올리기·삭제·순서 변경. 작성자만(403), 결과물 자랑 글에만 올릴 수 있다 (①). 사진 개수와 순서는 게시글 행을 잠근 뒤 바꿔 동시에 올려도
 * 3장을 넘지 않는다 (AGENTS.md 7). 저장 위치·형식 판단은 PST-007과 같은 것을 쓴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BoardPostImageService {

    // ② 장당 10MB 이하, 최대 3장 (PST-007과 같음)
    static final long MAX_BYTES = 10L * 1024 * 1024;
    static final int MAX_IMAGES = 3;

    // ② 문구 (형식·크기·실패는 PST-007 ②와 같은 문구)
    static final String SHOWCASE_ONLY_MESSAGE = "결과물 자랑 글에만 사진을 올릴 수 있어요";
    static final String TYPE_MESSAGE = "JPG·PNG·WebP·GIF·AVIF 사진만 올릴 수 있어요";
    static final String SIZE_MESSAGE = "사진은 10MB 이하만 올릴 수 있어요";
    static final String STORE_FAILED_MESSAGE = "사진을 올리지 못했어요. 잠시 후 다시 시도해 주세요";

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final BoardPostRepository boardPostRepository;
    private final BoardPostImageRepository boardPostImageRepository;
    private final PostImageUploader uploader;

    /** 상세(BRD-004)용: 삭제되지 않은 사진을 보여 주는 순서대로. */
    @Transactional(readOnly = true)
    public List<ImageInfo> findImages(Long boardPostId) {
        return toInfos(activeImages(boardPostId));
    }

    /** BRD-003 ③: 결과물 자랑에서 다른 말머리로 바꾸면 올린 사진을 모두 소프트 삭제한다. 부르는 쪽이 게시글 행을 잠근 트랜잭션 안에서 부른다. */
    @Transactional
    public void deleteAll(Long boardPostId, LocalDateTime now) {
        activeImages(boardPostId).forEach(image -> image.delete(now));
    }

    /** ②③: 한 장을 맨 뒤에 붙인다. 형식·크기는 파일을 저장하기 전에 확인한다. */
    @Transactional
    public BoardPostImagesResponse upload(Long memberId, Long boardPostId, MultipartFile file) {
        BoardPost post = lockOwnPost(memberId, boardPostId);
        if (post.getCategory() != BoardCategory.SHOWCASE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, SHOWCASE_ONLY_MESSAGE);
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, SIZE_MESSAGE);
        }
        AllowedImageType type =
                detectType(file)
                        .orElseThrow(
                                () -> new BusinessException(ErrorCode.INVALID_INPUT, TYPE_MESSAGE));

        List<BoardPostImage> images = activeImages(boardPostId);
        if (images.size() >= MAX_IMAGES) {
            throw new BusinessException(ErrorCode.BOARD_POST_IMAGE_LIMIT);
        }

        String url = store(boardPostId, type, file);
        images.add(
                boardPostImageRepository.save(BoardPostImage.of(boardPostId, url, images.size())));
        return new BoardPostImagesResponse(toInfos(images));
    }

    /** ④: 숨김 처리(소프트 삭제)하고 파일은 남긴다. 남은 사진 순서는 0부터 다시 매긴다. */
    @Transactional
    public BoardPostImagesResponse delete(Long memberId, Long boardPostId, Long imageId) {
        lockOwnPost(memberId, boardPostId);
        List<BoardPostImage> images = activeImages(boardPostId);
        BoardPostImage target =
                images.stream()
                        .filter(image -> image.getId().equals(imageId))
                        .findFirst()
                        .orElseThrow(
                                () -> new BusinessException(ErrorCode.BOARD_POST_IMAGE_NOT_FOUND));
        target.delete(LocalDateTime.now(KST));
        images.remove(target);
        renumber(images);
        return new BoardPostImagesResponse(toInfos(images));
    }

    /** ③: 남아 있는 사진 ID 전부를 새 순서로 받는다. 빠지거나 겹치거나 다른 사진이 섞이면 400. */
    @Transactional
    public BoardPostImagesResponse reorder(Long memberId, Long boardPostId, List<Long> imageIds) {
        lockOwnPost(memberId, boardPostId);
        List<BoardPostImage> images = activeImages(boardPostId);
        if (imageIds == null
                || imageIds.size() != images.size()
                || new HashSet<>(imageIds).size() != imageIds.size()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        Map<Long, BoardPostImage> byId =
                images.stream()
                        .collect(Collectors.toMap(BoardPostImage::getId, Function.identity()));
        if (!byId.keySet().equals(Set.copyOf(imageIds))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        List<BoardPostImage> ordered = imageIds.stream().map(byId::get).toList();
        renumber(ordered);
        return new BoardPostImagesResponse(toInfos(ordered));
    }

    /** 게시글 행을 잠그고, 없거나 삭제된 글이면 404, 작성자가 아니면 403. */
    private BoardPost lockOwnPost(Long memberId, Long boardPostId) {
        BoardPost post =
                boardPostRepository
                        .findByIdForUpdate(boardPostId)
                        .filter(found -> !found.isDeleted())
                        .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_POST_NOT_FOUND));
        if (!post.isWrittenBy(memberId)) {
            throw new BusinessException(ErrorCode.NOT_BOARD_POST_AUTHOR);
        }
        return post;
    }

    private List<BoardPostImage> activeImages(Long boardPostId) {
        // 순서를 바꾸고 붙이므로 고칠 수 있는 목록으로 받는다
        return new ArrayList<>(
                boardPostImageRepository
                        .findByBoardPostIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(boardPostId));
    }

    private static void renumber(List<BoardPostImage> images) {
        for (int i = 0; i < images.size(); i++) {
            images.get(i).changeSortOrder(i);
        }
    }

    private static List<ImageInfo> toInfos(List<BoardPostImage> images) {
        return images.stream()
                .map(image -> new ImageInfo(image.getId(), image.getImageUrl()))
                .toList();
    }

    private static Optional<AllowedImageType> detectType(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return AllowedImageType.detect(in);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, STORE_FAILED_MESSAGE);
        }
    }

    private String store(Long boardPostId, AllowedImageType type, MultipartFile file) {
        try {
            return uploader.store(ImageFolder.BOARD, boardPostId, type, file);
        } catch (IOException | RuntimeException e) {
            log.warn("Board post image upload failed boardPostId={}", boardPostId);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, STORE_FAILED_MESSAGE);
        }
    }
}
