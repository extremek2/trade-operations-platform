package com.tradeoperationsplatform.apiserver.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Reject unsafe production settings before datasource/Flyway beans are instantiated. */
@Configuration
@Profile("production")
public class ProductionConfiguration {
    @Bean
    static BeanFactoryPostProcessor productionSettings(Environment environment) {
        return factory -> validate(environment);
    }
    static void validate(Environment e) {
        String jwt=required(e,"app.auth.jwt-secret");
        if(jwt.getBytes(StandardCharsets.UTF_8).length<32 || jwt.contains("local-development") || placeholder(jwt)) fail("JWT_SECRET must be a generated production secret");
        byte[] key;
        try {key=Base64.getDecoder().decode(required(e,"app.mail.outbox-key"));}
        catch(IllegalArgumentException ex) {throw new IllegalStateException("MAIL_OUTBOX_KEY must be Base64 for 32 random bytes");}
        if(key.length!=32 || Arrays.equals(key,new byte[32])) fail("MAIL_OUTBOX_KEY must encode 32 random bytes; test key is forbidden");
        if(!e.getProperty("app.auth.secure-cookie",Boolean.class,false)) fail("AUTH_SECURE_COOKIE must be true");
        String[] origins=required(e,"app.auth.allowed-origins").split(",");
        Set<String> allowed=new HashSet<>();
        for(String raw:origins) {
            URI uri=https(raw.trim());
            if(uri.getRawPath()!=null && !uri.getRawPath().isEmpty()) fail("APP_ALLOWED_ORIGINS must contain HTTPS origins without paths");
            allowed.add(uri.toString());
        }
        link(e,"app.mail.verification-url","/verify-email",allowed);
        link(e,"app.mail.case-access-url","/external-access",allowed);
        String password=required(e,"spring.datasource.password");
        if(password.equals("product_pass") || placeholder(password) || password.length()<16) fail("Database password must be explicitly configured for production");
        if(!"validate".equals(e.getProperty("spring.jpa.hibernate.ddl-auto"))) fail("Production schema management requires ddl-auto=validate");
        if(e.getProperty("spring.flyway.baseline-on-migrate",Boolean.class,false)) fail("Automatic Flyway baseline is not allowed");
        positive(e,"app.auth.access-token-minutes",60);
        positive(e,"app.auth.refresh-token-days",30);
        positive(e,"app.collaboration.invitation-days",30);
        positive(e,"app.collaboration.login-minutes",60);
        if(e.getProperty("app.mail.dispatch-enabled",Boolean.class,false)) {
            required(e,"spring.mail.host");
            String from=required(e,"app.mail.from");
            if(!from.matches("[^\\s@<>]+@[^\\s@<>]+\\.[^\\s@<>]+")) fail("MAIL_FROM must be a single email address");
            required(e,"spring.mail.username"); required(e,"spring.mail.password");
            if(!e.getProperty("spring.mail.properties.mail.smtp.auth",Boolean.class,false)) fail("SMTP authentication must be enabled");
            boolean tls=e.getProperty("spring.mail.properties.mail.smtp.starttls.enable",Boolean.class,false)
                    && e.getProperty("spring.mail.properties.mail.smtp.starttls.required",Boolean.class,false);
            if(!tls && !e.getProperty("spring.mail.properties.mail.smtp.ssl.enable",Boolean.class,false)) fail("SMTP must require STARTTLS or TLS");
            if(!e.getProperty("spring.mail.properties.mail.smtp.ssl.checkserveridentity",Boolean.class,false)) fail("SMTP server identity verification must be enabled");
            for(String timeout:List.of("connectiontimeout","timeout","writetimeout")) positive(e,"spring.mail.properties.mail.smtp."+timeout,30000);
        }
    }
    private static void link(Environment e,String property,String path,Set<String> origins) {
        URI uri=https(required(e,property));
        if(!path.equals(uri.getPath()) || !origins.contains(uri.getScheme()+"://"+uri.getRawAuthority())) fail("Mail links must use the configured frontend origin and expected path");
    }
    private static URI https(String raw) {
        try {
            URI uri=URI.create(raw);
            if(!"https".equals(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null || uri.getRawQuery()!=null || uri.getRawFragment()!=null
                    || List.of("localhost","127.0.0.1","0.0.0.0").contains(uri.getHost()) || uri.getHost().endsWith(".invalid")) fail("Production URL must be an explicit HTTPS address");
            return uri;
        } catch(IllegalArgumentException ex) {throw new IllegalStateException("Invalid production URL");}
    }
    private static void positive(Environment e,String name,int max) {
        Integer value=e.getProperty(name,Integer.class);
        if(value==null || value<1 || value>max) fail(name+" is outside its supported range");
    }
    private static String required(Environment e,String name) {
        String value=e.getProperty(name,"");
        if(value.isBlank() || placeholder(value)) fail("Missing production setting: "+name);
        return value;
    }
    private static boolean placeholder(String value) {return value.toLowerCase(Locale.ROOT).contains("replace_me") || value.toLowerCase(Locale.ROOT).contains("change_me");}
    private static void fail(String message) {throw new IllegalStateException(message);}
}
