package com.tradeoperationsplatform.apiserver;

import com.tradeoperationsplatform.apiserver.domain.mail.*;
import com.tradeoperationsplatform.apiserver.domain.platform.service.AdminBootstrapService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Test-classpath-only fixture. Never included in bootJar or attached to a service database. */
public class OnboardingE2eServer {
    public static void main(String[] args) {
        PostgreSQLContainer<?> database = new PostgreSQLContainer<>("postgres:15-alpine");
        database.start();
        Runtime.getRuntime().addShutdownHook(new Thread(database::stop));
        SpringApplication application = new SpringApplication(SpringServerApplication.class, FixtureConfiguration.class);
        application.setAdditionalProfiles("onboarding-e2e");
        var context = application.run("--server.address=127.0.0.1", "--server.port=18080",
                "--spring.datasource.url=" + database.getJdbcUrl(),
                "--spring.datasource.username=" + database.getUsername(), "--spring.datasource.password=" + database.getPassword(),
                "--app.mail.outbox-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", "--app.mail.dispatch-enabled=false",
                "--app.mail.verification-url=http://localhost:3000/verify-email",
                "--app.mail.case-access-url=http://localhost:3000/external-access");
        context.getBean(AdminBootstrapService.class).createInitialAdmin("admin@example.test", "테스트 관리자", "E2e-password-123!".toCharArray());
        context.getBean(TestMailbox.class).ready = true;
    }

    @TestConfiguration
    static class FixtureConfiguration {
        @Bean MailTransport fakeTransport(TestMailbox mailbox) { return (id, message) -> mailbox.messages.put(message.recipient(), message); }
        @Bean @Order(0) SecurityFilterChain fixtureSecurity(HttpSecurity http) throws Exception {
            return http.securityMatcher("/_e2e/**").csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(requests -> requests.anyRequest().permitAll()).build();
        }
    }
    @RestController
    @org.springframework.context.annotation.Profile("onboarding-e2e")
    static class TestMailbox {
        private final MailOutboxDispatcher dispatcher;
        private final Map<String, MailMessage> messages = new ConcurrentHashMap<>();
        volatile boolean ready;
        TestMailbox(MailOutboxDispatcher dispatcher) { this.dispatcher = dispatcher; }
        @GetMapping("/_e2e/ready") Map<String, Boolean> ready() { return Map.of("ready", ready); }
        @PostMapping("/_e2e/mail") MailMessage mail(@RequestParam String email) {
            for (int i = 0; i < 100 && dispatcher.dispatchOne(); i++) { }
            MailMessage message = messages.get(email);
            if (message == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            return message;
        }
    }
}
