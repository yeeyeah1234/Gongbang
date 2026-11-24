package com.gongbang.file.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gongbang.file.domain.FilePurpose;
import com.gongbang.file.domain.UploadedFile;
import com.gongbang.file.repository.UploadedFileRepository;
import com.gongbang.global.error.exception.BusinessException;
import com.gongbang.member.domain.*;
import com.gongbang.member.service.MemberService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ProofFileServiceTest {
    final UploadedFileRepository files = mock(UploadedFileRepository.class);
    final ProofStorage storage = mock(ProofStorage.class);
    final MemberService members = mock(MemberService.class);
    final ProofFileService service = new ProofFileService(files, storage, members);

    void reader(MemberRole role) {
        Member member = mock(Member.class);
        when(member.getRole()).thenReturn(role);
        when(members.getActiveMember(2L)).thenReturn(member);
        when(files.findById(1L))
                .thenReturn(
                        Optional.of(
                                UploadedFile.create(
                                        3L,
                                        FilePurpose.PROOF,
                                        "local:proof/key",
                                        "proof.pdf",
                                        "application/pdf",
                                        5)));
    }

    @Test
    void 타인의증빙은_열람과사용을_거부한다() {
        reader(MemberRole.USER);
        assertThatThrownBy(() -> service.read(1L, 2L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.requireOwned(1L, 2L))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(storage);
    }

    @Test
    void 관리자는_증빙을열람한다() throws Exception {
        reader(MemberRole.ADMIN);
        when(storage.read("local:proof/key")).thenReturn(new byte[] {1, 2});
        assertThat(service.read(1L, 2L).bytes()).containsExactly(1, 2);
    }

    @Test
    void 본인은_증빙을열람한다() throws Exception {
        reader(MemberRole.USER);
        when(members.getActiveMember(3L)).thenReturn(mock(Member.class));
        when(storage.read("local:proof/key")).thenReturn(new byte[] {1});
        assertThat(service.read(1L, 3L).bytes()).containsExactly(1);
    }

    @Test
    void 확장자와MIME만으로_파일을허용하지않는다() {
        var file =
                new MockMultipartFile(
                        "file", "proof.png", "image/png", "<script>bad</script>".getBytes());
        assertThatThrownBy(() -> service.upload(2L, "PROOF", file))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(storage, files);
    }

    @Test
    void 크기와용도를_검증한다() {
        var huge = new MockMultipartFile("file", new byte[5 * 1024 * 1024 + 1]);
        assertThatThrownBy(() -> service.upload(2L, "PROOF", huge))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.upload(2L, "PROFILE", huge))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(storage, files);
    }

    @Test
    void DB저장실패시_파일을정리한다() throws Exception {
        when(storage.write(anyString(), any(), eq("application/pdf")))
                .thenReturn("local:proof/key");
        when(files.save(any())).thenThrow(new IllegalStateException("db"));
        assertThatThrownBy(
                        () ->
                                service.upload(
                                        2L,
                                        "PROOF",
                                        new MockMultipartFile(
                                                "file",
                                                "proof.pdf",
                                                "application/pdf",
                                                "%PDF-1.7".getBytes())))
                .isInstanceOf(IllegalStateException.class);
        verify(storage).delete("local:proof/key");
    }
}
