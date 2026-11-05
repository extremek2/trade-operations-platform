package com.tradeoperationsplatform.apiserver.domain.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class OutboxCipher {
    private final byte[] key;
    private final SecureRandom random = new SecureRandom();
    public OutboxCipher(@Value("${app.mail.outbox-key:}") String encodedKey) {
        if (encodedKey.isBlank()) { key = null; return; }
        key = Base64.getDecoder().decode(encodedKey);
        if (key.length != 32) throw new IllegalArgumentException("MAIL_OUTBOX_KEY must encode 32 bytes");
    }
    public void requireConfigured() {
        if (key == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "이메일 서비스 설정이 필요합니다.");
    }
    public String encrypt(String plaintext) {
        requireConfigured();
        try {
            byte[] nonce = new byte[12]; random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(nonce) + "." + Base64.getEncoder().encodeToString(ciphertext);
        } catch (java.security.GeneralSecurityException e) { throw new IllegalStateException("Outbox encryption failed", e); }
    }
    public String decrypt(String payload) {
        requireConfigured();
        try {
            String[] parts = payload.split("\\.");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, Base64.getDecoder().decode(parts[0])));
            return new String(cipher.doFinal(Base64.getDecoder().decode(parts[1])), StandardCharsets.UTF_8);
        } catch (java.security.GeneralSecurityException | IllegalArgumentException | IndexOutOfBoundsException e) {
            throw new IllegalStateException("Outbox decryption failed", e);
        }
    }
}
