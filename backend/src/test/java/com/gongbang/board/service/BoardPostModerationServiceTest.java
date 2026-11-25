package com.gongbang.board.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gongbang.board.domain.*;
import com.gongbang.board.repository.BoardPostRepository;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.service.AdminMemberService;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BoardPostModerationServiceTest {
    final BoardPostRepository repository = mock(BoardPostRepository.class);
    final AdminMemberService access = mock(AdminMemberService.class);
    final BoardPostModerationService service = new BoardPostModerationService(repository, access);

    @Test
    void 삭제된_글도_관리자가_조회하고_중복숨김은_삭제시각을_유지한다() {
        var post = BoardPost.of(2L, BoardCategory.SHOWCASE, null, "제목", "내용", null);
        var deletedAt = LocalDateTime.of(2026, 11, 25, 9, 0);
        post.delete(deletedAt);
        when(repository.findById(8L)).thenReturn(Optional.of(post));
        when(repository.findByIdForUpdate(8L)).thenReturn(Optional.of(post));
        assertThat(service.target(1L, 8L).deletedAt()).isEqualTo(deletedAt);
        assertThat(service.target(1L, 8L).content()).isEqualTo("내용");
        service.hide(1L, 8L);
        assertThat(post.getDeletedAt()).isEqualTo(deletedAt);
    }

    @Test
    void 숨김은_행잠금후_소프트삭제한다() {
        var post = BoardPost.of(2L, BoardCategory.SHOWCASE, null, "제목", "내용", null);
        when(repository.findByIdForUpdate(8L)).thenReturn(Optional.of(post));
        service.hide(1L, 8L);
        assertThat(post.isDeleted()).isTrue();
        verify(repository).findByIdForUpdate(8L);
    }

    @Test
    void 일반회원은_조회와_숨김을_할수없다() {
        doThrow(new BusinessException(ErrorCode.NOT_ADMIN)).when(access).requireAdmin(1L);
        assertThatThrownBy(() -> service.target(1L, 8L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.hide(1L, 8L)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(repository);
    }
}
