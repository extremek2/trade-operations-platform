package com.tradeoperationsplatform.apiserver.domain.mail;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MailOutbox {
    private final JdbcTemplate jdbc;
    private final OutboxCipher cipher;
    private final ObjectMapper mapper;
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(Long tokenId, MailMessage message, LocalDateTime expiresAt) {
        try {
            String payload = cipher.encrypt(mapper.writeValueAsString(message));
            jdbc.update("INSERT INTO mail_outbox(email_token_id,encrypted_payload,expires_at) VALUES (?,?,?)", tokenId, payload, expiresAt);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("Mail serialization failed", e); }
    }
}
