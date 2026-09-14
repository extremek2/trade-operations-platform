package com.tradeoperationsplatform.apiserver.domain.mail;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.*;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.MimeMessageHelper;

@Configuration
@Profile("!bootstrap-admin & !maintenance")
@ConditionalOnProperty(name="app.mail.dispatch-enabled", havingValue="true")
@EnableScheduling
public class SmtpMailConfiguration {
    @Bean
    MailTransport smtpTransport(JavaMailSender sender, OutboxCipher cipher, @Value("${app.mail.from}") String from) {
        cipher.requireConfigured();
        if (from.isBlank()) throw new IllegalArgumentException("MAIL_FROM is required");
        return (id, mail) -> {
            try {
                MimeMessage message = sender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
                helper.setFrom(from); helper.setTo(mail.recipient()); helper.setSubject(mail.subject()); helper.setText(mail.body(), false);
                message.setHeader("X-Trade-Ops-Message-Id", id.toString());
                sender.send(message);
            } catch (jakarta.mail.MessagingException e) { throw new IllegalStateException("Mail construction failed", e); }
        };
    }
    @Bean MailPoller mailPoller(MailOutboxDispatcher dispatcher) { return new MailPoller(dispatcher); }
    static class MailPoller {
        private final MailOutboxDispatcher dispatcher;
        MailPoller(MailOutboxDispatcher dispatcher) { this.dispatcher = dispatcher; }
        @Scheduled(fixedDelayString="${app.mail.poll-delay-ms:5000}")
        public void poll() { for (int i=0; i<10 && dispatcher.dispatchOne(); i++) { } }
    }
}
