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
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter)
            throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/api/v1/auth/**",
                                "/api/v1/register",
                                "/",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**")
                        .permitAll()
                        .requestMatchers("/api/v1/users/**").hasAnyRole("ADMIN", "HIGH_COMMITTEE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/rental-pricing/**").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/rental-pricing/**").hasRole("EQUIPMENT_COMMITTEE")
                        .requestMatchers(HttpMethod.POST, "/api/v1/equipment/**").hasRole("EQUIPMENT_COMMITTEE")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/equipment/**").hasRole("EQUIPMENT_COMMITTEE")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/equipment/**").hasRole("EQUIPMENT_COMMITTEE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/committee/**").hasAnyRole("HIGH_COMMITTEE", "EVENT_COMMITTEE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/users/**").hasAnyRole("ADMIN", "HIGH_COMMITTEE")
                        .requestMatchers("/api/v1/events/**").hasRole("HIGH_COMMITTEE")
                        .anyRequest().authenticated())

                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)

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