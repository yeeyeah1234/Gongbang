package com.gongbang.post.repository;

import com.gongbang.post.domain.RecruitPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecruitPostRepository extends JpaRepository<RecruitPost, Long> {

    /** PST-001: 삭제되지 않은 모집글 (PST-004 소프트 삭제 제외). */
    Page<RecruitPost> findByDeletedAtIsNull(Pageable pageable);
}
