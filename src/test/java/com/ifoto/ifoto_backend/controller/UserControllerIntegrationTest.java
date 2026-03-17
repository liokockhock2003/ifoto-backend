package com.ifoto.ifoto_backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifoto.ifoto_backend.model.Role;
import com.ifoto.ifoto_backend.model.User;
import com.ifoto.ifoto_backend.security.JwtUtil;
import com.ifoto.ifoto_backend.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @Test
    void getUserRoles_returnsRolesForUser() throws Exception {
        User user = buildUser("johndoe", Set.of("ROLE_CLUB_MEMBER", "ROLE_GUEST"));
        given(userService.getByUsername("johndoe")).willReturn(user);

        mockMvc.perform(get("/api/v1/users/johndoe/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("johndoe"))
                .andExpect(jsonPath("$.roles").isArray())
                .andExpect(jsonPath("$.roles.length()").value(2));
    }

    @Test
    void getUserRoles_returns404WhenUserNotFound() throws Exception {
        given(userService.getByUsername("missing"))
                .willThrow(new UsernameNotFoundException("User not found: missing"));

        mockMvc.perform(get("/api/v1/users/missing/roles"))
                .andExpect(status().isNotFound());
    }

    @Test
    void addRoleToUser_returnsUpdatedRoles() throws Exception {
        User user = buildUser("johndoe", Set.of("ROLE_CLUB_MEMBER", "ROLE_ADMIN"));
        given(userService.assignRoleToUser("johndoe", "ROLE_ADMIN")).willReturn(user);

        String body = objectMapper.writeValueAsString(java.util.Map.of("roleName", "ROLE_ADMIN"));

        mockMvc.perform(post("/api/v1/users/johndoe/roles")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("johndoe"))
                .andExpect(jsonPath("$.roles.length()").value(2));
    }

    @Test
    void addRoleToUser_returns400ForInvalidRole() throws Exception {
        given(userService.assignRoleToUser("johndoe", "ROLE_UNKNOWN"))
                .willThrow(new IllegalArgumentException("Role not found: ROLE_UNKNOWN"));

        String body = objectMapper.writeValueAsString(java.util.Map.of("roleName", "ROLE_UNKNOWN"));

        mockMvc.perform(post("/api/v1/users/johndoe/roles")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void replaceUserRoles_returnsUpdatedRoles() throws Exception {
        User user = buildUser("johndoe", Set.of("ROLE_GUEST"));
        given(userService.replaceUserRoles("johndoe", Set.of("ROLE_GUEST"))).willReturn(user);

        String body = objectMapper.writeValueAsString(java.util.Map.of("roles", Set.of("ROLE_GUEST")));

        mockMvc.perform(put("/api/v1/users/johndoe/roles")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("johndoe"))
                .andExpect(jsonPath("$.roles.length()").value(1));
    }

    @Test
    void replaceUserRoles_returns400WhenRolesPayloadIsMissing() throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of());

        mockMvc.perform(put("/api/v1/users/johndoe/roles")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void removeRoleFromUser_returnsUpdatedRoles() throws Exception {
        User user = buildUser("johndoe", Set.of("ROLE_CLUB_MEMBER"));
        given(userService.removeRoleFromUser("johndoe", "ROLE_GUEST")).willReturn(user);

        mockMvc.perform(delete("/api/v1/users/johndoe/roles/ROLE_GUEST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("johndoe"))
                .andExpect(jsonPath("$.roles.length()").value(1));
    }

    @Test
    void removeRoleFromUser_returns400WhenUserDoesNotHaveRole() throws Exception {
        willThrow(new IllegalArgumentException("User does not have role: ROLE_GUEST"))
                .given(userService)
                .removeRoleFromUser("johndoe", "ROLE_GUEST");

        mockMvc.perform(delete("/api/v1/users/johndoe/roles/ROLE_GUEST"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void removeRoleFromUser_returns404WhenUserNotFound() throws Exception {
        willThrow(new UsernameNotFoundException("User not found: missing"))
                .given(userService)
                .removeRoleFromUser("missing", "ROLE_GUEST");

        mockMvc.perform(delete("/api/v1/users/missing/roles/ROLE_GUEST"))
                .andExpect(status().isNotFound());
    }

    private User buildUser(String username, Set<String> roleNames) {
        Set<Role> roles = roleNames.stream()
                .map(roleName -> Role.builder().name(roleName).build())
                .collect(java.util.stream.Collectors.toSet());

        return User.builder()
                .id(1L)
                .username(username)
                .email(username + "@ifoto.com")
                .passwordHash("hashed")
                .roles(roles)
                .build();
    }
}
