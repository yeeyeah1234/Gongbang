package com.gongbang.post.repository;

import com.gongbang.post.domain.RecruitPostImage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecruitPostImageRepository extends JpaRepository<RecruitPostImage, Long> {

    /** PST-007: 삭제되지 않은 사진을 보여 주는 순서대로 (첫 장이 대표 이미지). */
    List<RecruitPostImage> findByPostIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(Long postId);
}
