package com.gongbang.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.domain.Member;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

/** MY-01 ③ 프로필 사진: JPG·PNG만, 5MB 이하, 올리면 바로 프로필 사진 주소가 바뀌고 예전 파일은 지운다 */
class ProfileImageServiceTest {

    private static final byte[] PNG = {
        (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0
    };
    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};

    @TempDir Path dir;

    private MemberService memberService;
    private ProfileImageService service;
    private Member member;

    @BeforeEach
    void setUp() {
        memberService = mock(MemberService.class);
        service =
                new ProfileImageService(
                        memberService, new LocalProfileImageStorage(dir.toString()));
        member = Member.createEmailMember("a@b.com", "hash", "테스터");
        when(memberService.getActiveMember(1L)).thenReturn(member);
    }

    @Test
    void PNG를_올리면_저장되고_프로필_사진_주소가_바뀐다() throws Exception {
        service.change(1L, new MockMultipartFile("file", "me.png", "image/png", PNG));

        assertThat(member.getProfileImageUrl()).startsWith("/uploads/profile/1-").endsWith(".png");
        String name = member.getProfileImageUrl().substring("/uploads/profile/".length());
        assertThat(Files.exists(dir.resolve("profile").resolve(name))).isTrue();
    }

    @Test
    void 새_사진을_올리면_예전_파일은_지운다() throws Exception {
        service.change(1L, new MockMultipartFile("file", "a.png", "image/png", PNG));
        String old = member.getProfileImageUrl().substring("/uploads/profile/".length());

        service.change(1L, new MockMultipartFile("file", "b.jpg", "image/jpeg", JPG));

        assertThat(member.getProfileImageUrl()).endsWith(".jpg");
        assertThat(Files.exists(dir.resolve("profile").resolve(old))).isFalse();
    }

    @Test
    void 이름만_png인_다른_파일은_막는다() {
        MockMultipartFile fake =
                new MockMultipartFile("file", "virus.png", "image/png", "<script>".getBytes());
        assertThatThrownBy(() -> service.change(1L, fake)).isInstanceOf(BusinessException.class);
        assertThat(member.getProfileImageUrl()).isNull();
    }

    @Test
    void 사진이_5MB를_넘으면_막는다() {
        byte[] big = new byte[(int) ProfileImageService.MAX_BYTES + 1];
        System.arraycopy(PNG, 0, big, 0, PNG.length);
        MockMultipartFile file = new MockMultipartFile("file", "big.png", "image/png", big);
        assertThatThrownBy(() -> service.change(1L, file)).isInstanceOf(BusinessException.class);
    }

    @Test
    void rollbackDeletesNewImageAndPreservesOldImage() throws Exception {
        var storage = mock(ProfileImageStorage.class);
        service = new ProfileImageService(memberService, storage);
        member.changeProfileImage("old");
        when(storage.store(
                        org.mockito.ArgumentMatchers.eq(1L),
                        org.mockito.ArgumentMatchers.eq("png"),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn("new");
        org.springframework.transaction.support.TransactionSynchronizationManager
                .initSynchronization();
        try {
            service.change(1L, new MockMultipartFile("file", "a.png", "image/png", PNG));
            org.mockito.Mockito.verify(storage, org.mockito.Mockito.never())
                    .delete(org.mockito.ArgumentMatchers.any());
            for (var sync :
                    org.springframework.transaction.support.TransactionSynchronizationManager
                            .getSynchronizations())
                sync.afterCompletion(
                        org.springframework.transaction.support.TransactionSynchronization
                                .STATUS_ROLLED_BACK);
            org.mockito.Mockito.verify(storage).delete("new");
            org.mockito.Mockito.verify(storage, org.mockito.Mockito.never()).delete("old");
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager
                    .clearSynchronization();
        }
    }

    @Test
    void deletesOldImageOnlyAfterCommit() throws Exception {
        var storage = mock(ProfileImageStorage.class);
        service = new ProfileImageService(memberService, storage);
        member.changeProfileImage("old");
        when(storage.store(
                        org.mockito.ArgumentMatchers.eq(1L),
                        org.mockito.ArgumentMatchers.eq("png"),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn("new");
        org.springframework.transaction.support.TransactionSynchronizationManager
                .initSynchronization();
        try {
            service.change(1L, new MockMultipartFile("file", "a.png", "image/png", PNG));
            org.mockito.Mockito.verify(storage, org.mockito.Mockito.never())
                    .delete(org.mockito.ArgumentMatchers.any());
            for (var sync :
                    org.springframework.transaction.support.TransactionSynchronizationManager
                            .getSynchronizations()) {
                sync.afterCommit();
                sync.afterCompletion(
                        org.springframework.transaction.support.TransactionSynchronization
                                .STATUS_COMMITTED);
            }
            org.mockito.Mockito.verify(storage).delete("old");
            org.mockito.Mockito.verify(storage, org.mockito.Mockito.never()).delete("new");
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager
                    .clearSynchronization();
        }
    }
}
