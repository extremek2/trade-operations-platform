package com.tradeoperationsplatform.apiserver.domain.mail;

import com.tradeoperationsplatform.apiserver.PostgresTestSupport;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class MailOutboxIntegrationTest extends PostgresTestSupport {
    @Autowired MailOutbox outbox;
    @Autowired MailOutboxDispatcher dispatcher;
    @Autowired OutboxCipher cipher;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean MailTransport transport;

    @BeforeEach void isolatePendingDeliveries() {
        jdbc.update("UPDATE mail_outbox SET available_at=NOW()+INTERVAL '1 day' WHERE status='PENDING'");
    }
    long enqueue(LocalDateTime expiry) {
        return new TransactionTemplate(transactions).execute(status -> {
            outbox.enqueue(null, new MailMessage("test@example.com", "verification", "sensitive-token"), expiry);
            return jdbc.queryForObject("SELECT max(id) FROM mail_outbox",Long.class);
        });
    }
    String state(long id) { return jdbc.queryForObject("SELECT status FROM mail_outbox WHERE id=?",String.class,id); }
    String payload(long id) { return jdbc.queryForObject("SELECT encrypted_payload FROM mail_outbox WHERE id=?",String.class,id); }

    @Test void onlyEncryptedPayloadIsStoredAndSuccessErasesIt() {
        long id=enqueue(LocalDateTime.now().plusMinutes(15));
        assertThat(payload(id)).doesNotContain("sensitive-token", "test@example.com");
        assertThat(dispatcher.dispatchOne()).isTrue();
        verify(transport).send(any(UUID.class), eq(new MailMessage("test@example.com","verification","sensitive-token")));
        assertThat(state(id)).isEqualTo("SENT"); assertThat(payload(id)).isNull();
        assertThat(dispatcher.dispatchOne()).isFalse();
    }

    @Test void failedDeliveryRetriesWithoutPersistingProviderSecrets() {
        long id=enqueue(LocalDateTime.now().plusMinutes(15));
        doThrow(new IllegalStateException("provider exposed sensitive-token")).when(transport).send(any(),any());
        dispatcher.dispatchOne();
        assertThat(state(id)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT last_error FROM mail_outbox WHERE id=?",String.class,id)).isEqualTo("DELIVERY_FAILED");
        reset(transport);
        jdbc.update("UPDATE mail_outbox SET available_at=NOW() WHERE id=?",id);
        dispatcher.dispatchOne();
        assertThat(state(id)).isEqualTo("SENT"); assertThat(payload(id)).isNull();
    }

    @Test void retryExhaustionErasesPayloadAndDoesNotSendAgain() {
        long id=enqueue(LocalDateTime.now().plusMinutes(15));
        doThrow(new IllegalStateException("failure")).when(transport).send(any(),any());
        for(int i=0;i<5;i++) { jdbc.update("UPDATE mail_outbox SET available_at=NOW() WHERE id=?",id); dispatcher.dispatchOne(); }
        assertThat(state(id)).isEqualTo("FAILED"); assertThat(payload(id)).isNull();
        verify(transport,times(5)).send(any(),any());
        assertThat(dispatcher.dispatchOne()).isFalse();
    }

    @Test void expiredMessageIsPurgedWithoutSending() {
        long id=enqueue(LocalDateTime.now().minusSeconds(1));
        assertThat(dispatcher.dispatchOne()).isFalse();
        assertThat(state(id)).isEqualTo("EXPIRED"); assertThat(payload(id)).isNull();
        verifyNoInteractions(transport);
    }

    @Test void rolledBackBusinessTransactionLeavesNoEmail() {
        long before=jdbc.queryForObject("SELECT count(*) FROM mail_outbox",Long.class);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            outbox.enqueue(null,new MailMessage("test@example.com","test","test"),LocalDateTime.now().plusMinutes(5));
            status.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mail_outbox",Long.class)).isEqualTo(before);
    }

    @Test void missingKeyAndTamperedCiphertextFailClosed() {
        assertThatThrownBy(() -> new OutboxCipher("").encrypt("token"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        String encoded = cipher.encrypt("token");
        String tampered = encoded.substring(0,encoded.length()-4)+"AAAA";
        assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
    }
}
