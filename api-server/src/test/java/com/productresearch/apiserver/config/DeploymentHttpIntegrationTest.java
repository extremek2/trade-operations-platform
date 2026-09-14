package com.productresearch.apiserver.config;

import com.productresearch.apiserver.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"app.auth.allowed-origins=https://trade.example.com","app.auth.secure-cookie=true"})
@AutoConfigureMockMvc
class DeploymentHttpIntegrationTest extends PostgresTestSupport {
    @Autowired MockMvc mvc;
    @Test void configuredHttpsFrontendOriginIsAllowedAndUnknownOriginIsRejected() throws Exception {
        mvc.perform(options("/api/v1/auth/login").header("Origin","https://trade.example.com").header("Access-Control-Request-Method","POST"))
            .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin","https://trade.example.com"));
        mvc.perform(post("/api/v1/auth/logout").header("Origin","https://untrusted.example.com"))
            .andExpect(status().isForbidden());
    }
    @Test void cookieRemovalRetainsSecureHttpOnlySameSiteAndPath() throws Exception {
        mvc.perform(post("/api/v1/auth/logout")).andExpect(status().isOk())
            .andExpect(header().string("Set-Cookie",org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.containsString("Secure"),org.hamcrest.Matchers.containsString("HttpOnly"),org.hamcrest.Matchers.containsString("SameSite=Strict"),org.hamcrest.Matchers.containsString("Path=/api/v1/auth"))));
    }
}
