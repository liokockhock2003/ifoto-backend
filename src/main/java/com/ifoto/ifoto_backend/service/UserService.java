package com.ifoto.ifoto_backend.service;

import com.ifoto.ifoto_backend.model.Role;
import com.ifoto.ifoto_backend.model.User;
import com.ifoto.ifoto_backend.repository.RoleRepository;
import com.ifoto.ifoto_backend.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Validated
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User register(@Valid User user) {
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }

        user.setPasswordHash(passwordEncoder.encode(user.getPasswordHash()));

        // Assign default role ROLE_GUEST if none provided
        if (user.getRoles() == null || user.getRoles().isEmpty()) {
            Role guestRole = roleRepository.findByName("ROLE_GUEST")
                    .orElseThrow(() -> new IllegalStateException("Default role ROLE_GUEST not found in database"));
            user.setRoles(Set.of(guestRole));
        }

        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Transactional(readOnly = true)
    public User getByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    @Transactional(readOnly = true)
    public Set<String> getRoleNamesByUsername(String username) {
        return getByUsername(username).getRoles().stream()
                .map(Role::getName)
                .collect(java.util.stream.Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }
}