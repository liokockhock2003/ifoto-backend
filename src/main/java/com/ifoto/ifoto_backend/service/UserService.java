package com.ifoto.ifoto_backend.service;

import com.ifoto.ifoto_backend.dto.UserListItemResponse;
import com.ifoto.ifoto_backend.model.Role;
import com.ifoto.ifoto_backend.model.User;
import com.ifoto.ifoto_backend.repository.RoleRepository;
import com.ifoto.ifoto_backend.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.HashSet;
import java.util.Locale;
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

    @Transactional(readOnly = true)
    public Page<UserListItemResponse> listUsers(String search, String role, int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("Page must be >= 0");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("Size must be between 1 and 100");
        }

        Pageable pageable = PageRequest.of(page, size);
        String normalizedSearch = search == null ? "" : search.trim();
        String normalizedRole = normalizeRoleFilter(role);

        return userRepository.searchUsers(normalizedSearch, normalizedRole, pageable)
                .map(user -> new UserListItemResponse(
                        user.getId(),
                        user.getUsername(),
                        user.getEmail(),
                        user.getFullName(),
                        user.isActive(),
                        user.isLocked(),
                        user.getRoles().stream().map(Role::getName).collect(java.util.stream.Collectors.toSet())));
    }

    @Transactional(readOnly = true)
    public Set<String> getRolesForUser(String username) {
        return getByUsername(username).getRoles().stream()
                .map(Role::getName)
                .collect(java.util.stream.Collectors.toSet());
    }

    private String normalizeRoleFilter(String role) {
        if (role == null) {
            return "";
        }

        String normalized = role.trim();
        if (normalized.isEmpty() || normalized.equalsIgnoreCase("ALL")) {
            return "";
        }

        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!normalized.startsWith("ROLE_")) {
            normalized = "ROLE_" + normalized;
        }

        return normalized;
    }

    @Transactional
    public User assignRoleToUser(String username, String roleName) {
        User user = getByUsername(username);
        String normalizedRoleName = normalizeRoleName(roleName);
        Role role = findRoleByName(normalizedRoleName);

        boolean hasRole = user.getRoles().stream()
                .anyMatch(existingRole -> existingRole.getName().equals(role.getName()));

        if (hasRole) {
            return user;
        }

        user.getRoles().add(role);
        return userRepository.save(user);
    }

    @Transactional
    public User replaceUserRoles(String username, Set<String> roleNames) {
        if (roleNames == null) {
            throw new IllegalArgumentException("Role list must not be null");
        }

        User user = getByUsername(username);

        Set<Role> resolvedRoles = roleNames.stream()
                .map(this::normalizeRoleName)
                .map(this::findRoleByName)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));

        user.setRoles(resolvedRoles);
        return userRepository.save(user);
    }

    @Transactional
    public User removeRoleFromUser(String username, String roleName) {
        User user = getByUsername(username);
        String normalizedRoleName = normalizeRoleName(roleName);

        boolean removed = user.getRoles().removeIf(role -> role.getName().equals(normalizedRoleName));
        if (!removed) {
            throw new IllegalArgumentException("User does not have role: " + normalizedRoleName);
        }

        return userRepository.save(user);
    }

    private Role findRoleByName(String roleName) {
        return roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleName));
    }

    private String normalizeRoleName(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            throw new IllegalArgumentException("Role name must not be blank");
        }

        String normalized = roleName.trim().toUpperCase(Locale.ROOT);
        if (!normalized.startsWith("ROLE_")) {
            normalized = "ROLE_" + normalized;
        }

        return normalized;
    }
}