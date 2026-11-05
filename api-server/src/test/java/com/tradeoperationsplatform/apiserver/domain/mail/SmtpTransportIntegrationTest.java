package com.tradeoperationsplatform.apiserver.domain.mail;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class SmtpTransportIntegrationTest {
    @Test void smtpAdapterSendsKoreanBodyAndStableMessageIdToLocalReceiver() throws Exception {
        try(var receiver=new Receiver(false)) {
            var id=UUID.randomUUID(); var transport=transport(receiver);
            transport.send(id,new MailMessage("recipient@example.test","한글 제목","이메일 확인 링크 테스트"));
            String raw=receiver.message.get(5,TimeUnit.SECONDS);
            var message=new MimeMessage(Session.getInstance(new Properties()),new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));
            assertThat(message.getSubject()).isEqualTo("한글 제목");
            assertThat(message.getContent().toString()).contains("이메일 확인 링크 테스트");
            assertThat(message.getHeader("X-Trade-Ops-Message-Id",null)).isEqualTo(id.toString());
            assertThat(message.getAllRecipients()[0].toString()).isEqualTo("recipient@example.test");
        }
    }
    @Test void smtpRejectionPropagatesForOutboxRetry() throws Exception {
        try(var receiver=new Receiver(true)) {
            assertThatThrownBy(() -> transport(receiver).send(UUID.randomUUID(),new MailMessage("recipient@example.test","test","body")))
                .isInstanceOf(org.springframework.mail.MailException.class);
        }
    }
    MailTransport transport(Receiver receiver) {
        var sender=new JavaMailSenderImpl(); sender.setHost("127.0.0.1");sender.setPort(receiver.server.getLocalPort());
        sender.getJavaMailProperties().setProperty("mail.smtp.timeout","3000");
        return new SmtpMailConfiguration().smtpTransport(sender,new OutboxCipher("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="),"sender@example.test");
    }
    static class Receiver implements AutoCloseable {
        final ServerSocket server; final ExecutorService thread=Executors.newSingleThreadExecutor(); final Future<String> message;
        Receiver(boolean reject) throws IOException {
            server=new ServerSocket(0,1,InetAddress.getLoopbackAddress());server.setSoTimeout(5000);
            message=thread.submit(() -> {
                try(var socket=server.accept()) {
                    socket.setSoTimeout(5000);
                    var reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));
                    var writer=new PrintWriter(new OutputStreamWriter(socket.getOutputStream(),StandardCharsets.UTF_8),true);
                    writer.print("220 localhost test SMTP\r\n");writer.flush();
                    StringBuilder body=new StringBuilder(); boolean data=false;String line;
                    while((line=reader.readLine())!=null) {
                        if(data && !line.equals(".")) {body.append(line.startsWith("..")?line.substring(1):line).append("\r\n");continue;}
                        if(data) {data=false;writer.print("250 accepted\r\n");}
                        else if(line.startsWith("DATA")) {data=true;writer.print("354 send data\r\n");}
                        else if(line.startsWith("RCPT") && reject) writer.print("550 rejected by test receiver\r\n");
                        else if(line.startsWith("QUIT")) {writer.print("221 bye\r\n");writer.flush();break;}
                        else writer.print("250 localhost\r\n");
                        writer.flush();
                    }
                    return body.toString();
                }
            });
        }
        public void close() throws IOException {server.close();thread.shutdownNow();}
    }
}
