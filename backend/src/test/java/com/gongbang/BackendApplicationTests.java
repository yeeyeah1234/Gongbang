package com.gongbang;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 서버 전체(모든 빈)가 뜨는지 확인한다. QueryDslConfig·repository가 JPA를 써야 해서 실제 DB가 있어야 뜬다.
 *
 * <p>환경 변수 TEST_DATASOURCE_URL이 있을 때만 돈다 (TeamMysqlTest와 같은 방식). 반드시 테스트 전용 스키마(예: gongbang_test)를
 * 가리키게 한다. Flyway가 V1부터 적용한다. DB 이름에 _test가 없으면 건너뛴다 (개발용 DB 보호).
 *
 * <pre>
 * TEST_DATASOURCE_URL=jdbc:mysql://localhost:3306/gongbang_test?serverTimezone=Asia/Seoul
 * TEST_DATASOURCE_USERNAME=root   (없으면 root)
 * TEST_DATASOURCE_PASSWORD=...    (없으면 빈 값)
 * </pre>
 */
@SpringBootTest(
        properties = {
            // test application.yml이 끈 DataSource·JPA·Flyway 자동 설정을 이 테스트에서만 다시 켠다.
            "spring.autoconfigure.exclude=",
            "spring.datasource.url=${TEST_DATASOURCE_URL:}",
            "spring.datasource.username=${TEST_DATASOURCE_USERNAME:root}",
            "spring.datasource.password=${TEST_DATASOURCE_PASSWORD:}"
        })
// Flyway가 이 DB에 마이그레이션을 적용하므로, 개발용 DB를 넣는 실수를 막으려고 DB 이름(호스트·옵션 말고)에 _test가 있을 때만 돈다.
@EnabledIfEnvironmentVariable(
        named = "TEST_DATASOURCE_URL",
        matches = "jdbc:mysql://[^/]+/[^/?]*_test[^/?]*(\\?.*)?")
class BackendApplicationTests {

    @Test
    void contextLoads() {}
}
