package com.gongbang.board.repository;

import com.gongbang.board.domain.BoardPostImage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoardPostImageRepository extends JpaRepository<BoardPostImage, Long> {

    /** BRD-006: 삭제되지 않은 사진을 보여 주는 순서대로 (첫 장이 대표). */
    List<BoardPostImage> findByBoardPostIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(
            Long boardPostId);
}
