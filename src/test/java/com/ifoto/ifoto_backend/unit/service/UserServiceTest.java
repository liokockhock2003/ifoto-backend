package com.ifoto.ifoto_backend.unit.service;

import com.ifoto.ifoto_backend.dto.UserDTO.BankDetailRequest;
import com.ifoto.ifoto_backend.dto.UserDTO.BankDetailResponse;
import com.ifoto.ifoto_backend.dto.UserDTO.UserListItemResponse;
import com.ifoto.ifoto_backend.dto.UserDTO.UserUpdateResponse;
import com.ifoto.ifoto_backend.model.Role;
import com.ifoto.ifoto_backend.model.User;
import com.ifoto.ifoto_backend.repository.RoleRepository;
import com.ifoto.ifoto_backend.repository.UserRepository;
import com.ifoto.ifoto_backend.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService service;

    // ── register ─────────────────────────────────────────────────────────────

    @Test
    void register_duplicateUsername_throwsIllegalArgument() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        User user = userWith("alice", "alice@other.com", "password123");
        assertThrows(IllegalArgumentException.class, () -> service.register(user));
    }

    @Test
    void register_duplicateEmail_throwsIllegalArgument() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@other.com")).thenReturn(true);

        User user = userWith("alice", "alice@other.com", "password123");
        assertThrows(IllegalArgumentException.class, () -> service.register(user));
    }

    @Test
    void register_utmEmail_assignsStudentRole() {
        when(userRepository.existsByUsername(any())).thenReturn(false);
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        Role studentRole = role(1L, "ROLE_STUDENT");
        when(roleRepository.findByName("ROLE_STUDENT")).thenReturn(Optional.of(studentRole));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        User user = userWith("alice", "alice@graduate.utm.my", "password123");
        User saved = service.register(user);

        assertTrue(saved.getRoles().stream().anyMatch(r -> r.getName().equals("ROLE_STUDENT")));
    }

    @Test
    void register_nonUtmEmail_assignsNonStudentRole() {
        when(userRepository.existsByUsername(any())).thenReturn(false);
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        Role nonStudentRole = role(2L, "ROLE_NON_STUDENT");
        when(roleRepository.findByName("ROLE_NON_STUDENT")).thenReturn(Optional.of(nonStudentRole));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        User user = userWith("bob", "bob@gmail.com", "password123");
        User saved = service.register(user);

        assertTrue(saved.getRoles().stream().anyMatch(r -> r.getName().equals("ROLE_NON_STUDENT")));
    }

    @Test
    void register_shortPassword_throwsIllegalArgument() {
        when(userRepository.existsByUsername(any())).thenReturn(false);
        when(userRepository.existsByEmail(any())).thenReturn(false);

        User user = userWith("alice", "alice@other.com", "short");
        assertThrows(IllegalArgumentException.class, () -> service.register(user));
    }

    // ── applyRoleConstraints (tested via updateUser) ──────────────────────────

    @Test
    void updateUser_adminImpliesStudentAutoAdded() {
        User existingUser = userWith("alice", "alice@utm.my", "hashed");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(existingUser));

        Role adminRole = role(1L, "ROLE_ADMIN");
        Role studentRole = role(2L, "ROLE_STUDENT");
        when(roleRepository.findByName("ROLE_ADMIN")).thenReturn(Optional.of(adminRole));
        when(roleRepository.findByName("ROLE_STUDENT")).thenReturn(Optional.of(studentRole));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        User result = service.updateUser("alice", Set.of("ROLE_ADMIN"), null);

        assertTrue(result.getRoles().stream().anyMatch(r -> r.getName().equals("ROLE_STUDENT")));
        assertTrue(result.getRoles().stream().anyMatch(r -> r.getName().equals("ROLE_ADMIN")));
    }

    @Test
    void updateUser_studentAndNonStudentTogether_throwsIllegalArgument() {
        User existingUser = userWith("alice", "alice@utm.my", "hashed");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(existingUser));

        when(roleRepository.findByName("ROLE_STUDENT")).thenReturn(Optional.of(role(1L, "ROLE_STUDENT")));
        when(roleRepository.findByName("ROLE_NON_STUDENT")).thenReturn(Optional.of(role(2L, "ROLE_NON_STUDENT")));

        assertThrows(IllegalArgumentException.class,
                () -> service.updateUser("alice", Set.of("ROLE_STUDENT", "ROLE_NON_STUDENT"), null));
    }

    @Test
    void updateUser_adminAndHighCommitteeTogether_throwsIllegalArgument() {
        User existingUser = userWith("alice", "alice@utm.my", "hashed");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(existingUser));

        when(roleRepository.findByName("ROLE_ADMIN")).thenReturn(Optional.of(role(1L, "ROLE_ADMIN")));
        when(roleRepository.findByName("ROLE_HIGH_COMMITTEE")).thenReturn(Optional.of(role(2L, "ROLE_HIGH_COMMITTEE")));
        when(roleRepository.findByName("ROLE_STUDENT")).thenReturn(Optional.of(role(3L, "ROLE_STUDENT")));

        assertThrows(IllegalArgumentException.class,
                () -> service.updateUser("alice", Set.of("ROLE_ADMIN", "ROLE_HIGH_COMMITTEE"), null));
    }

    @Test
    void updateUser_eventCommitteeWithoutBaseRole_throwsIllegalArgument() {
        User existingUser = userWith("alice", "alice@utm.my", "hashed");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(existingUser));

        when(roleRepository.findByName("ROLE_EVENT_COMMITTEE")).thenReturn(Optional.of(role(1L, "ROLE_EVENT_COMMITTEE")));

        assertThrows(IllegalArgumentException.class,
                () -> service.updateUser("alice", Set.of("ROLE_EVENT_COMMITTEE"), null));
    }

    @Test
    void updateUser_eventCommitteeWithStudentRole_succeeds() {
        User existingUser = userWith("alice", "alice@utm.my", "hashed");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(existingUser));

        when(roleRepository.findByName("ROLE_EVENT_COMMITTEE")).thenReturn(Optional.of(role(1L, "ROLE_EVENT_COMMITTEE")));
        when(roleRepository.findByName("ROLE_STUDENT")).thenReturn(Optional.of(role(2L, "ROLE_STUDENT")));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        User result = service.updateUser("alice", Set.of("ROLE_EVENT_COMMITTEE", "ROLE_STUDENT"), null);

        assertTrue(result.getRoles().stream().anyMatch(r -> r.getName().equals("ROLE_EVENT_COMMITTEE")));
    }

    @Test
    void updateUser_noFieldsProvided_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> service.updateUser("alice", null, null));
    }

    // ── listUsers ─────────────────────────────────────────────────────────────

    @Test
    void listUsers_negativePageThrowsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> service.listUsers(null, null, -1, 10));
    }

    @Test
    void listUsers_sizeZeroThrowsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> service.listUsers(null, null, 0, 0));
    }

    @Test
    void listUsers_sizeOver100ThrowsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> service.listUsers(null, null, 0, 101));
    }

    @Test
    void listUsers_nullSearchAndRole_normalizedToEmpty_returnsPage() {
        User u = fullUser(1L, "alice", "alice@utm.my", "ROLE_STUDENT");
        Page<User> mockPage = new PageImpl<>(List.of(u));
        when(userRepository.searchUsers(eq(""), eq(""), any())).thenReturn(mockPage);

        Page<UserListItemResponse> result = service.listUsers(null, null, 0, 10);

        assertEquals(1, result.getTotalElements());
        UserListItemResponse item = result.getContent().get(0);
        assertEquals("alice", item.username());
        assertTrue(item.roles().contains("ROLE_STUDENT"));
    }

    @Test
    void listUsers_roleAll_normalizedToEmpty() {
        Page<User> mockPage = new PageImpl<>(List.of());
        when(userRepository.searchUsers(eq(""), eq(""), any())).thenReturn(mockPage);

        Page<UserListItemResponse> result = service.listUsers(null, "ALL", 0, 10);

        verify(userRepository).searchUsers(eq(""), eq(""), any());
        assertTrue(result.isEmpty());
    }

    @Test
    void listUsers_roleLowercase_normalizedWithPrefix() {
        Page<User> mockPage = new PageImpl<>(List.of());
        when(userRepository.searchUsers(eq(""), eq("ROLE_STUDENT"), any())).thenReturn(mockPage);

        service.listUsers(null, "student", 0, 10);

        verify(userRepository).searchUsers(eq(""), eq("ROLE_STUDENT"), any());
    }

    @Test
    void listUsers_roleAlreadyPrefixed_notDoublePrefixed() {
        Page<User> mockPage = new PageImpl<>(List.of());
        when(userRepository.searchUsers(eq(""), eq("ROLE_STUDENT"), any())).thenReturn(mockPage);

        service.listUsers(null, "ROLE_STUDENT", 0, 10);

        verify(userRepository).searchUsers(eq(""), eq("ROLE_STUDENT"), any());
    }

    @Test
    void listUsers_withSearch_trimmedAndPassed() {
        Page<User> mockPage = new PageImpl<>(List.of());
        when(userRepository.searchUsers(eq("alice"), eq(""), any())).thenReturn(mockPage);

        service.listUsers("  alice  ", null, 0, 10);

        verify(userRepository).searchUsers(eq("alice"), eq(""), any());
    }

    // ── getRolesForUser ───────────────────────────────────────────────────────

    @Test
    void getRolesForUser_userNotFound_throwsUsernameNotFound() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> service.getRolesForUser("unknown"));
    }

    @Test
    void getRolesForUser_returnsRoleNames() {
        User u = fullUser(1L, "alice", "alice@utm.my", "ROLE_STUDENT");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(u));

        Set<String> roles = service.getRolesForUser("alice");

        assertEquals(Set.of("ROLE_STUDENT"), roles);
    }

    @Test
    void getRolesForUser_multipleRoles_returnsAll() {
        User u = User.builder().id(1L).username("alice")
                .roles(new HashSet<>(Set.of(role(1L, "ROLE_STUDENT"), role(2L, "ROLE_ADMIN"))))
                .build();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(u));

        Set<String> roles = service.getRolesForUser("alice");

        assertEquals(Set.of("ROLE_STUDENT", "ROLE_ADMIN"), roles);
    }

    // ── addRoleToUser ─────────────────────────────────────────────────────────

    @Test
    void addRoleToUser_userNotFound_throwsIllegalArgument() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.addRoleToUser(99L, "ROLE_STUDENT"));
    }

    @Test
    void addRoleToUser_roleNotFound_throwsIllegalState() {
        User u = userWith("alice", "alice@utm.my", "hashed");
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(roleRepository.findByName("ROLE_GHOST")).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> service.addRoleToUser(1L, "ROLE_GHOST"));
    }

    @Test
    void addRoleToUser_userAlreadyHasRole_doesNotSave() {
        Role student = role(1L, "ROLE_STUDENT");
        User u = User.builder().id(1L).username("alice")
                .roles(new HashSet<>(Set.of(student))).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(roleRepository.findByName("ROLE_STUDENT")).thenReturn(Optional.of(student));

        service.addRoleToUser(1L, "ROLE_STUDENT");

        verify(userRepository, never()).save(any());
    }

    @Test
    void addRoleToUser_newRole_addsAndSaves() {
        Role existing = role(1L, "ROLE_STUDENT");
        Role newRole = role(2L, "ROLE_ADMIN");
        User u = User.builder().id(1L).username("alice")
                .roles(new HashSet<>(Set.of(existing))).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(roleRepository.findByName("ROLE_ADMIN")).thenReturn(Optional.of(newRole));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.addRoleToUser(1L, "ROLE_ADMIN");

        verify(userRepository).save(u);
        assertTrue(u.getRoles().contains(newRole));
    }

    // ── removeRoleFromUser ────────────────────────────────────────────────────

    @Test
    void removeRoleFromUser_roleNotPresent_doesNotSave() {
        User u = User.builder().id(1L).username("alice")
                .roles(new HashSet<>(Set.of(role(1L, "ROLE_STUDENT")))).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));

        service.removeRoleFromUser(1L, "ROLE_ADMIN");

        verify(userRepository, never()).save(any());
    }

    @Test
    void removeRoleFromUser_rolePresent_removesAndSaves() {
        Role student = role(1L, "ROLE_STUDENT");
        User u = User.builder().id(1L).username("alice")
                .roles(new HashSet<>(Set.of(student))).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.removeRoleFromUser(1L, "ROLE_STUDENT");

        verify(userRepository).save(u);
        assertTrue(u.getRoles().isEmpty());
    }

    // ── upsertMyBankDetail ────────────────────────────────────────────────────

    @Test
    void upsertMyBankDetail_setsFieldsAndReturnsMappedResponse() {
        User u = User.builder().id(1L).username("alice").fullName("Alice Tan").build();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(u));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BankDetailRequest req = new BankDetailRequest("Maybank", "1234567890", "base64sig");
        BankDetailResponse response = service.upsertMyBankDetail("alice", req);

        assertEquals("Maybank", response.bankName());
        assertEquals("1234567890", response.accountNo());
        assertEquals("Alice Tan", response.accountName());
        assertEquals("alice", response.username());
        verify(userRepository).save(u);
    }

    @Test
    void upsertMyBankDetail_userNotFound_throwsUsernameNotFound() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> service.upsertMyBankDetail("ghost", new BankDetailRequest("X", "Y", null)));
    }

    // ── deleteUserByUsername ──────────────────────────────────────────────────

    @Test
    void deleteUserByUsername_userNotFound_throwsUsernameNotFound() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> service.deleteUserByUsername("ghost"));
    }

    @Test
    void deleteUserByUsername_deletesAndReturnsResponse() {
        User u = fullUser(1L, "alice", "alice@utm.my", "ROLE_STUDENT");
        u.setFullName("Alice Tan");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(u));

        UserUpdateResponse response = service.deleteUserByUsername("alice");

        verify(userRepository).delete(u);
        assertEquals(1L, response.userId());
        assertEquals("alice", response.username());
        assertEquals("Alice Tan", response.fullName());
        assertTrue(response.roles().contains("ROLE_STUDENT"));
    }

    // ── getMyBankDetail ───────────────────────────────────────────────────────

    @Test
    void getMyBankDetail_noBankDetails_throws404() {
        User u = User.builder().id(1L).username("alice").build(); // bankName is null
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(u));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.getMyBankDetail("alice"));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void getMyBankDetail_withDetails_returnsMappedResponse() {
        User u = User.builder().id(1L).username("alice").fullName("Alice Tan")
                .bankName("CIMB").accountNo("9876543210").signature("sig").build();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(u));

        BankDetailResponse response = service.getMyBankDetail("alice");

        assertEquals("CIMB", response.bankName());
        assertEquals("9876543210", response.accountNo());
        assertEquals("Alice Tan", response.accountName());
        assertEquals("alice", response.username());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private User userWith(String username, String email, String password) {
        return User.builder()
                .username(username)
                .email(email)
                .passwordHash(password)
                .build();
    }

    private User fullUser(Long id, String username, String email, String roleName) {
        return User.builder()
                .id(id).username(username).email(email)
                .roles(new HashSet<>(Set.of(role(1L, roleName))))
                .isActive(true).isLocked(false)
                .build();
    }

    private Role role(Long id, String name) {
        return Role.builder().id(id).name(name).build();
    }
}
