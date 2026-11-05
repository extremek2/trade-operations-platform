package com.tradeoperationsplatform.apiserver.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ProductionConfigurationTest {
    MockEnvironment valid() {
        byte[] bytes=new byte[32]; new java.security.SecureRandom().nextBytes(bytes);
        return new MockEnvironment().withProperty("app.auth.jwt-secret",UUID.randomUUID()+UUID.randomUUID().toString())
            .withProperty("app.mail.outbox-key",Base64.getEncoder().encodeToString(bytes))
            .withProperty("app.auth.secure-cookie","true").withProperty("app.auth.allowed-origins","https://trade.example.com")
            .withProperty("app.mail.verification-url","https://trade.example.com/verify-email")
            .withProperty("app.mail.case-access-url","https://trade.example.com/external-access")
            .withProperty("spring.datasource.password","test-only-long-database-password")
            .withProperty("spring.jpa.hibernate.ddl-auto","validate")
            .withProperty("app.auth.access-token-minutes","15").withProperty("app.auth.refresh-token-days","14")
            .withProperty("app.collaboration.invitation-days","7").withProperty("app.collaboration.login-minutes","15");
    }
    @Test void acceptsConfiguredMaintenanceWithMailOff() {assertThatCode(() -> ProductionConfiguration.validate(valid())).doesNotThrowAnyException();}
    @Test void rejectsDevelopmentSecretsAndDoesNotExposeTheirValues() {
        var e=valid().withProperty("app.auth.jwt-secret","local-development-secret-change-me-1234567890");
        assertThatThrownBy(() -> ProductionConfiguration.validate(e)).hasMessageNotContaining(e.getProperty("app.auth.jwt-secret"));
        assertThatThrownBy(() -> ProductionConfiguration.validate(valid().withProperty("app.mail.outbox-key","AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="))).isInstanceOf(IllegalStateException.class);
    }
    @Test void rejectsInsecureCookiesUrlsAndWrongEmailDestination() {
        for(var pair:List.of(new String[]{"app.auth.secure-cookie","false"},new String[]{"app.auth.allowed-origins","http://localhost:3000"},
                new String[]{"app.mail.case-access-url","https://other.example.com/external-access"},new String[]{"app.mail.verification-url","https://trade.example.com/verify-email?token=secret"}))
            assertThatThrownBy(() -> ProductionConfiguration.validate(valid().withProperty(pair[0],pair[1]))).isInstanceOf(IllegalStateException.class);
    }
    @Test void rejectsUnboundedTokensAndAutomaticBaseline() {
        assertThatThrownBy(() -> ProductionConfiguration.validate(valid().withProperty("app.collaboration.login-minutes","0"))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ProductionConfiguration.validate(valid().withProperty("spring.flyway.baseline-on-migrate","true"))).isInstanceOf(IllegalStateException.class);
    }
    @Test void smtpDispatchRequiresCredentialsAndVerifiedTls() {
        var e=valid().withProperty("app.mail.dispatch-enabled","true");
        assertThatThrownBy(() -> ProductionConfiguration.validate(e)).isInstanceOf(IllegalStateException.class);
        e.withProperty("spring.mail.host","smtp.example.com").withProperty("app.mail.from","ops@example.com")
            .withProperty("spring.mail.username","test-user").withProperty("spring.mail.password","test-only")
            .withProperty("spring.mail.properties.mail.smtp.auth","true")
            .withProperty("spring.mail.properties.mail.smtp.starttls.enable","true")
            .withProperty("spring.mail.properties.mail.smtp.starttls.required","true")
            .withProperty("spring.mail.properties.mail.smtp.ssl.checkserveridentity","true");
        for(String name:List.of("connectiontimeout","timeout","writetimeout")) e.withProperty("spring.mail.properties.mail.smtp."+name,"5000");
        assertThatCode(() -> ProductionConfiguration.validate(e)).doesNotThrowAnyException();
        e.withProperty("spring.mail.properties.mail.smtp.starttls.required","false");
        assertThatThrownBy(() -> ProductionConfiguration.validate(e)).isInstanceOf(IllegalStateException.class);
    }
}
