package com.tradeoperationsplatform.apiserver.domain.mail;
import java.util.UUID;
public interface MailTransport { void send(UUID messageId, MailMessage message); }
