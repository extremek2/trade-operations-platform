package com.tradeoperationsplatform.apiserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SpringServerApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(SpringServerApplication.class);
        boolean bootstrap = java.util.Arrays.asList(args).contains("--bootstrap-admin");
        boolean migrate = java.util.Arrays.asList(args).contains("--migrate-only");
        if (bootstrap && migrate) throw new IllegalArgumentException("Select only one maintenance operation");
        if (bootstrap || migrate) {
            application.setAdditionalProfiles(bootstrap ? "bootstrap-admin" : "maintenance");
            application.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);
            if (migrate) {
                args = java.util.stream.Stream.concat(java.util.Arrays.stream(args), java.util.stream.Stream.of("--spring.flyway.enabled=true", "--app.mail.dispatch-enabled=false")).toArray(String[]::new);
            }
            try (var context = application.run(args)) {
                // The explicit bootstrap runner completes before the context closes.
            }
        } else {
            application.run(args);
        }
    }

}
