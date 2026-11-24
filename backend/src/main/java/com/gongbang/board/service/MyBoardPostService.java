package com.gongbang.board.service;

import com.gongbang.board.dto.MyBoardPostItem;
import com.gongbang.board.repository.BoardPostRepository;
import com.gongbang.global.common.PageResponse;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyBoardPostService {
    private final BoardPostRepository repository;

    public PageResponse<MyBoardPostItem> list(Long memberId, int page, int size) {
        if (memberId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        if (page < 0 || size < 1 || size > 100)
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        var result = repository.findMine(memberId, PageRequest.of(page, size));
        return PageResponse.of(result.getContent(), page, size, result.getTotalElements());
    }
}
