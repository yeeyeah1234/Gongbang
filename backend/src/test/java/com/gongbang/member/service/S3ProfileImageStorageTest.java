package com.gongbang.member.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

class S3ProfileImageStorageTest {
    @Test
    void storesContentTypeAndBytesAndDeletesOnlyOwnedUrls() throws Exception {
        S3Client client = mock(S3Client.class);
        var store =
                new S3ProfileImageStorage(client, "gongbang-public", "https://example.com/files/");
        byte[] bytes = {1, 2, 3};
        String url = store.store(4L, "png", new MockMultipartFile("file", bytes));
        var request = ArgumentCaptor.forClass(PutObjectRequest.class);
        var body = ArgumentCaptor.forClass(RequestBody.class);
        verify(client).putObject(request.capture(), body.capture());
        assertThat(request.getValue().bucket()).isEqualTo("gongbang-public");
        assertThat(request.getValue().contentType()).isEqualTo("image/png");
        assertThat(url).isEqualTo("https://example.com/files/" + request.getValue().key());
        try (var in = body.getValue().contentStreamProvider().newStream()) {
            assertThat(in.readAllBytes()).isEqualTo(bytes);
        }
        store.delete("https://other.com/files/profile/4/abc.png");
        store.delete("https://example.com/files/../private/secret");
        store.delete("/uploads/profile/old.png");
        verify(client, never()).deleteObject(any(DeleteObjectRequest.class));
        store.delete(url);
        verify(client)
                .deleteObject(
                        DeleteObjectRequest.builder()
                                .bucket("gongbang-public")
                                .key(request.getValue().key())
                                .build());
    }
}
