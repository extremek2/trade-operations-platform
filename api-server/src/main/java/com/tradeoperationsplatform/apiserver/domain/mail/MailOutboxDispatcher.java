package com.tradeoperationsplatform.apiserver.domain.mail;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MailOutboxDispatcher {
    private final JdbcTemplate jdbc;
    private final OutboxCipher cipher;
    private final ObjectMapper mapper;
    private final ObjectProvider<MailTransport> transports;

    @Transactional
    public boolean dispatchOne() {
        jdbc.update("UPDATE mail_outbox SET status='EXPIRED', encrypted_payload=NULL WHERE status='PENDING' AND expires_at <= NOW()");
        var rows = jdbc.queryForList("SELECT id,public_id,encrypted_payload,attempts FROM mail_outbox WHERE status='PENDING' AND available_at<=NOW() ORDER BY id FOR UPDATE SKIP LOCKED LIMIT 1");
        if (rows.isEmpty()) return false;
        var row = rows.get(0);
        MailTransport transport = transports.getIfAvailable();
        if (transport == null) return false;
        int attempts = ((Number)row.get("attempts")).intValue() + 1;
        try {
            MailMessage message = mapper.readValue(cipher.decrypt((String)row.get("encrypted_payload")), MailMessage.class);
            transport.send((UUID)row.get("public_id"), message);
            jdbc.update("UPDATE mail_outbox SET status='SENT',encrypted_payload=NULL,sent_at=NOW(),attempts=?,last_error=NULL WHERE id=?", attempts, row.get("id"));
        } catch (Exception e) {
            // Do not persist provider messages: they can contain addresses, body text or authentication tokens.
            jdbc.update("UPDATE mail_outbox SET attempts=?,status=?,encrypted_payload=CASE WHEN ? THEN NULL ELSE encrypted_payload END,last_error='DELIVERY_FAILED',available_at=NOW()+INTERVAL '1 minute' WHERE id=?",
                    attempts, attempts >= 5 ? "FAILED" : "PENDING", attempts >= 5, row.get("id"));
        }
        return true;
    }
}
