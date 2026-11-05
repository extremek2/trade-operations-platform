package com.tradeoperationsplatform.apiserver;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import static org.assertj.core.api.Assertions.*;

class BootstrapModeIntegrationTest extends PostgresTestSupport {
    @Test void nonInteractiveBootstrapFailsClosedAfterNonWebStartup() {
        SpringApplication application = new SpringApplication(SpringServerApplication.class);
        application.setAdditionalProfiles("bootstrap-admin");
        application.setWebApplicationType(WebApplicationType.NONE);
        assertThatThrownBy(() -> application.run("--bootstrap-admin",
                "--spring.datasource.url=" + DATABASE.getJdbcUrl(),
                "--spring.datasource.username=" + DATABASE.getUsername(),
                "--spring.datasource.password=" + DATABASE.getPassword()))
                .hasStackTraceContaining("비밀번호 비노출 입력을 위해 대화형 터미널에서 실행해 주세요.");
    }
}
