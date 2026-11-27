package com.gongbang.admin.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gongbang.admin.domain.AdminActivity;
import com.gongbang.admin.repository.AdminActivityRepository;
import com.gongbang.global.error.ErrorCode;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.service.AdminMemberService;
import com.gongbang.member.service.MemberService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;

class AdminActivityQueryServiceTest {
    final AdminActivityRepository repo = mock(AdminActivityRepository.class);
    final AdminMemberService admins = mock(AdminMemberService.class);
    final MemberService members = mock(MemberService.class);
    final AdminActivityQueryService service = new AdminActivityQueryService(repo, admins, members);

    @Test
    void 일반회원의로그조회는차단한다() {
        doThrow(new BusinessException(ErrorCode.NOT_ADMIN)).when(admins).requireAdmin(1L);
        assertThatThrownBy(() -> service.list(1L, "", 0)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(repo, members);
    }

    @Test
    void 필터와최신순페이지조건을적용한다() {
        var expected =
                PageRequest.of(1, 20, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        when(repo.findByAction("WARN", expected))
                .thenReturn(
                        new PageImpl<>(List.of(new AdminActivity(2L, "WARN", "MEMBER", 3L, "사유"))));
        when(members.displayName(2L)).thenReturn("관리자");
        var item = service.list(1L, "WARN", 1).getContent().get(0);
        assertThat(item.actorNickname()).isEqualTo("관리자");
        assertThat(item.action()).isEqualTo("WARN");
        verify(repo).findByAction("WARN", expected);
    }

    @Test
    void 음수페이지는조회하지않는다() {
        assertThatThrownBy(() -> service.list(1L, "", -1)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(repo);
    }
}
