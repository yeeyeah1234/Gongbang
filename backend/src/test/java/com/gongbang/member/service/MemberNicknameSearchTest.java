package com.gongbang.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.gongbang.member.repository.MemberRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class MemberNicknameSearchTest {
    @Mock MemberRepository repository;
    @InjectMocks MemberService service;

    @Test
    void shortOrMissingKeywordDoesNotQueryAllMembers() {
        for (String keyword : new String[] {null, "", "  ", " 진 ", "😀"}) {
            assertThat(service.findActiveMemberIdsByNickname(keyword)).isEmpty();
        }
        verifyNoInteractions(repository);
    }

    @Test
    void trimsKeywordAndLimitsDatabaseQueryToFiftyIds() {
        when(repository.findActiveIdsByNicknameContaining("진우", PageRequest.of(0, 50)))
                .thenReturn(List.of(2L, 8L));
        assertThat(service.findActiveMemberIdsByNickname("  진우  ")).containsExactly(2L, 8L);
        verify(repository).findActiveIdsByNicknameContaining("진우", PageRequest.of(0, 50));
    }

    @Test
    void preservesLiteralCharactersAndReturnsEmptyForNoMatch() {
        when(repository.findActiveIdsByNicknameContaining("%_", PageRequest.of(0, 50)))
                .thenReturn(List.of());
        assertThat(service.findActiveMemberIdsByNickname("%_")).isEmpty();
        verify(repository).findActiveIdsByNicknameContaining("%_", PageRequest.of(0, 50));
    }
}
