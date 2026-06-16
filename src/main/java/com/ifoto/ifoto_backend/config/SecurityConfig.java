package com.ifoto.ifoto_backend.config;

import com.ifoto.ifoto_backend.security.JwtAuthenticationFilter;
import com.ifoto.ifoto_backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.ifoto.ifoto_backend.security.JwtUtil;
import com.ifoto.ifoto_backend.security.RateLimitFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtUtil, userDetailsService());
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter,
            RateLimitFilter rateLimitFilter) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/api/v1/auth/**",
                                "/api/v1/register",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/me/bank-details").hasRole("EQUIPMENT_COMMITTEE")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/users/me/bank-details").hasRole("EQUIPMENT_COMMITTEE")
                        .requestMatchers("/api/v1/users/**").hasAnyRole("ADMIN", "HIGH_COMMITTEE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/rental-pricing/**").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/rental-pricing/**").hasRole("EQUIPMENT_COMMITTEE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/equipment/main/*/statuses")
                        .hasRole("EQUIPMENT_COMMITTEE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/equipment/sub/*/quantity-holds")
                        .hasRole("EQUIPMENT_COMMITTEE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/equipment/available").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/equipment").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/equipment/**").hasRole("EQUIPMENT_COMMITTEE")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/equipment/**").hasRole("EQUIPMENT_COMMITTEE")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/equipment/**").hasRole("EQUIPMENT_COMMITTEE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/my")
                        .hasAnyRole("HIGH_COMMITTEE", "EVENT_COMMITTEE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/committee/**")
                        .hasAnyRole("ADMIN", "HIGH_COMMITTEE", "EVENT_COMMITTEE")
                        .requestMatchers("/api/v1/events/**").hasRole("HIGH_COMMITTEE")
                        // Rental endpoints
                        .requestMatchers(HttpMethod.POST, "/api/v1/rentals/trigger-active")
                        .hasRole("EQUIPMENT_COMMITTEE")
                        .requestMatchers(HttpMethod.POST, "/api/v1/rentals/trigger-overdue")
                        .hasRole("EQUIPMENT_COMMITTEE")
                        .requestMatchers(HttpMethod.POST, "/api/v1/rentals").hasAnyRole("STUDENT", "NON_STUDENT")
                        .requestMatchers(HttpMethod.GET, "/api/v1/rentals/my").hasAnyRole("STUDENT", "NON_STUDENT")
                        .requestMatchers(HttpMethod.POST, "/api/v1/rentals/*/pay").hasAnyRole("STUDENT", "NON_STUDENT")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/rentals/*").hasAnyRole("STUDENT", "NON_STUDENT")
                        .requestMatchers(HttpMethod.GET, "/api/v1/rentals/my-approvals").hasRole("EQUIPMENT_COMMITTEE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/rentals").hasRole("EQUIPMENT_COMMITTEE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/rentals/equipment/*").hasRole("EQUIPMENT_COMMITTEE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/rentals/sub-equipment/*")
                        .hasRole("EQUIPMENT_COMMITTEE")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/rentals/**").hasRole("EQUIPMENT_COMMITTEE")
                        // Event Equipment Requests
                        .requestMatchers(HttpMethod.POST, "/api/v1/event-equipment-requests").hasRole("EVENT_COMMITTEE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/event-equipment-requests/equipment/*")
                        .hasRole("EQUIPMENT_COMMITTEE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/event-equipment-requests/sub-equipment/*")
                        .hasRole("EQUIPMENT_COMMITTEE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/event-equipment-requests/event/**")
                        .hasAnyRole("EVENT_COMMITTEE", "EQUIPMENT_COMMITTEE")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/event-equipment-requests/*")
                        .hasRole("EVENT_COMMITTEE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/event-equipment-requests")
                        .hasRole("EQUIPMENT_COMMITTEE")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/event-equipment-requests/**")
                        .hasRole("EQUIPMENT_COMMITTEE")
                        .requestMatchers(HttpMethod.POST, "/api/v1/event-equipment-requests/trigger-active")
                        .hasRole("EQUIPMENT_COMMITTEE")
                        // Reports
                        .requestMatchers(HttpMethod.GET, "/api/v1/reports/**")
                        .hasAnyRole("EQUIPMENT_COMMITTEE", "HIGH_COMMITTEE")
                        // Payment callback (public — Billplz server POSTs here)
                        .requestMatchers(HttpMethod.POST, "/api/v1/payments/callback").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/payments/result").permitAll()
                        .anyRequest().authenticated())

                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(rateLimitFilter, JwtAuthenticationFilter.class)

                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, authEx) -> {
                            res.setStatus(401);
                            res.setContentType("application/json");
                            res.getWriter().write("{\"error\":\"Unauthorized\"}");
                        })
                        .accessDeniedHandler((req, res, accessEx) -> {
                            res.setStatus(403);
                            res.setContentType("application/json");
                            res.getWriter().write("{\"error\":\"Forbidden\"}");
                        }));

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> userService.findByUsername(username)
                .map(user -> {
                    var authorities = user.getRoles().stream()
                            .map(role -> new SimpleGrantedAuthority(role.getName()))
                            .toList();
                    return org.springframework.security.core.userdetails.User
                            .withUsername(user.getUsername())
                            .password(user.getPasswordHash())
                            .authorities(authorities)
                            .accountExpired(false)
                            .accountLocked(user.isLocked())
                            .credentialsExpired(false)
                            .disabled(!user.isActive())
                            .build();
                })
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }
}