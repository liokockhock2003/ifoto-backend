package com.ifoto.ifoto_backend.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFilterChainIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void protectedEndpoint_noAuthorizationHeader_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/equipment"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_invalidBearerToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/equipment")
                        .header("Authorization", "Bearer not.a.real.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_malformedAuthorizationHeader_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/equipment")
                        .header("Authorization", "NotBearer somevalue"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicEndpoint_noAuthorization_returns200() throws Exception {
        // GET /api/v1/rental-pricing is public per SecurityConfig
        mockMvc.perform(get("/api/v1/rental-pricing"))
                .andExpect(status().isOk());
    }
}
