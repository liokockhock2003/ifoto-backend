package com.ifoto.ifoto_backend.service;

import com.ifoto.ifoto_backend.dto.UserDTO.UserListItemResponse;
import com.ifoto.ifoto_backend.dto.UserDTO.UserUpdateResponse;
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

        user.setPasswordHash(passwordEncoder.encode(validateRawPassword(user.getPasswordHash())));

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
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
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

    @Transactional
    public User updatePassword(User user, String rawPassword) {
        user.setPasswordHash(passwordEncoder.encode(validateRawPassword(rawPassword)));
        return userRepository.save(user);
    }

    private String validateRawPassword(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }

        return rawPassword;
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
    public User updateUser(String username, Set<String> roleNames, Boolean locked) {
        if (roleNames == null && locked == null) {
            throw new IllegalArgumentException("At least one update field must be provided");
        }

        User user = getByUsername(username);

        if (roleNames != null) {
            Set<Role> resolvedRoles = roleNames.stream()
                    .map(this::normalizeRoleName)
                    .map(this::findRoleByName)
                    .collect(java.util.stream.Collectors.toCollection(HashSet::new));
            resolvedRoles = applyRoleConstraints(resolvedRoles);
            user.setRoles(resolvedRoles);
        }

        if (locked != null) {
            user.setLocked(locked);
        }

        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
    }

    @Transactional
    public void addRoleToUser(Long userId, String roleName) {
        User user = getUserById(userId);
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException("Role not found: " + roleName));
        if (user.getRoles().stream().noneMatch(r -> r.getName().equals(roleName))) {
            user.getRoles().add(role);
            userRepository.save(user);
        }
    }

    @Transactional
    public void removeRoleFromUser(Long userId, String roleName) {
        User user = getUserById(userId);
        boolean removed = user.getRoles().removeIf(r -> r.getName().equals(roleName));
        if (removed) {
            userRepository.save(user);
        }
    }

    @Transactional
    public UserUpdateResponse deleteUserByUsername(String username) {
        User user = getByUsername(username);

        UserUpdateResponse response = new UserUpdateResponse(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getRoles().stream().map(Role::getName).collect(java.util.stream.Collectors.toSet()),
                user.isLocked());

        userRepository.delete(user);
        return response;
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

    private Set<Role> applyRoleConstraints(Set<Role> resolvedRoles) {
        Set<String> names = resolvedRoles.stream()
                .map(Role::getName)
                .collect(java.util.stream.Collectors.toSet());

        // Rule 1: ADMIN, HIGH_COMMITTEE, EQUIPMENT_COMMITTEE always imply CLUB_MEMBER
        boolean needsClubMember = names.contains("ROLE_ADMIN")
                || names.contains("ROLE_HIGH_COMMITTEE")
                || names.contains("ROLE_EQUIPMENT_COMMITTEE");

        if (needsClubMember && !names.contains("ROLE_CLUB_MEMBER")) {
            Role clubMember = roleRepository.findByName("ROLE_CLUB_MEMBER")
                    .orElseThrow(() -> new IllegalStateException("Role Club Member not found in database"));
            resolvedRoles = new HashSet<>(resolvedRoles);
            resolvedRoles.add(clubMember);
            names = resolvedRoles.stream().map(Role::getName).collect(java.util.stream.Collectors.toSet());
        }

        // Rule 2: CLUB_MEMBER and GUEST are mutually exclusive
        if (names.contains("ROLE_CLUB_MEMBER") && names.contains("ROLE_GUEST")) {
            throw new IllegalArgumentException(
                    "Role Club Member and Role Guest cannot be assigned to the same user");
        }

        // Rule 3: ADMIN and HIGH_COMMITTEE are mutually exclusive
        if (names.contains("ROLE_ADMIN") && names.contains("ROLE_HIGH_COMMITTEE")) {
            throw new IllegalArgumentException(
                    "Role Admin and Role High Committee cannot be assigned to the same user");
        }

        // Rule 5: EVENT_COMMITTEE must declare a base membership type
        if (names.contains("ROLE_EVENT_COMMITTEE")) {
            if (!names.contains("ROLE_CLUB_MEMBER") && !names.contains("ROLE_GUEST")) {
                throw new IllegalArgumentException(
                        "Role Event Committee requires either Role Club Member or Role Guest to also be present");
            }
        }

        return resolvedRoles;
    }

}
