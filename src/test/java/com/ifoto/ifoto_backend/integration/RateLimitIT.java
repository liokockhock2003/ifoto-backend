package com.ifoto.ifoto_backend.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RateLimitIT {

    @Autowired
    private MockMvc mockMvc;

    // Uses a unique X-Forwarded-For IP so this test's bucket is isolated from other tests.
    private static final String TEST_IP = "192.0.2.55";

    private static final String LOGIN_BODY = """
            {"username":"testuser","password":"wrongpassword"}
            """;

    @Test
    void loginEndpoint_sixthRequest_returns429() throws Exception {
        // Requests 1–5: consume the bucket (response may be 200 or 401, not 429)
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                    .header("X-Forwarded-For", TEST_IP)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(LOGIN_BODY));
        }

        // 6th request must be blocked by rate limit
        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", TEST_IP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void loginEndpoint_fifthRequest_isStillAllowed() throws Exception {
        // Uses a different IP to avoid sharing a bucket with the other test
        String isolatedIp = "192.0.2.56";

        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                    .header("X-Forwarded-For", isolatedIp)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(LOGIN_BODY));
        }

        // 5th request is the boundary — must NOT be rate-limited (401 = auth failed, not 429)
        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", isolatedIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andExpect(status().isUnauthorized());
    }
}
