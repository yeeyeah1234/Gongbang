package com.gongbang.workspace.sync;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 실제 스프링 컨텍스트에서 SyncServerClient 빈이 만들어지는지 확인한다(@Value의 Duration 변환 포함). SyncServerClient와
 * RestClientAutoConfiguration만 띄우므로 DB·JPA·Flyway·Security는 뜨지 않는다.
 */
class SyncServerClientContextTest {

    @Nested
    @SpringBootTest(classes = SyncServerClient.class)
    @Import(RestClientAutoConfiguration.class)
    @DisplayName("app.sync 설정이 없을 때")
    class 설정이_없을_때 {

        @Autowired private ApplicationContext context;

        @Test
        @DisplayName("기본값으로 컨텍스트가 뜨고 SyncServerClient 빈이 있다 (DB 없음)")
        void 컨텍스트가_뜬다() {
            assertThat(context.getBeansOfType(SyncServerClient.class)).hasSize(1);
            assertThat(context.getBeanNamesForType(DataSource.class)).isEmpty();
        }
    }

    @Nested
    @SpringBootTest(
            classes = SyncServerClient.class,
            properties = {
                "app.sync.server-url=http://127.0.0.1:1234",
                "app.sync.internal-key=context-test-key",
                "app.sync.connect-timeout=1s",
                "app.sync.read-timeout=2s"
            })
    @Import(RestClientAutoConfiguration.class)
    @DisplayName("app.sync 설정을 넣었을 때")
    class 설정이_있을_때 {

        @Autowired private ApplicationContext context;

        @Autowired private SyncServerClient syncServerClient;

        @Test
        @DisplayName("컨텍스트가 뜨고 주소·키가 주입된다 (DB 없음)")
        void 컨텍스트가_뜬다() {
            assertThat(ReflectionTestUtils.getField(syncServerClient, "serverUrl"))
                    .isEqualTo("http://127.0.0.1:1234");
            assertThat(ReflectionTestUtils.getField(syncServerClient, "internalKey"))
                    .isEqualTo("context-test-key");
            assertThat(context.getBeanNamesForType(DataSource.class)).isEmpty();
        }
    }
}
