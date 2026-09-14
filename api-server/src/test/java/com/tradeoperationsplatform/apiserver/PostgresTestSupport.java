package com.tradeoperationsplatform.apiserver;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

public abstract class PostgresTestSupport {
    static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>("postgres:15-alpine");
    static { DATABASE.start(); }
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("app.mail.outbox-key", () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        properties.add("spring.datasource.url", DATABASE::getJdbcUrl);
        properties.add("spring.datasource.username", DATABASE::getUsername);
        properties.add("spring.datasource.password", DATABASE::getPassword);
    }
}
