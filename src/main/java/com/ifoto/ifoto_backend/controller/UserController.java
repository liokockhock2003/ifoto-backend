package com.ifoto.ifoto_backend.controller;

import com.ifoto.ifoto_backend.dto.UserDTO.UserListItemResponse;
import com.ifoto.ifoto_backend.dto.UserDTO.ActiveRoleSwitchRequest;
import com.ifoto.ifoto_backend.dto.UserDTO.UserRolesResponse;
import com.ifoto.ifoto_backend.dto.UserDTO.UserUpdateRequest;
import com.ifoto.ifoto_backend.dto.UserDTO.UserUpdateResponse;
import com.ifoto.ifoto_backend.model.User;
import com.ifoto.ifoto_backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<Page<UserListItemResponse>> listUsers(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "") String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            return ResponseEntity.ok(userService.listUsers(search, role, page, size));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(BAD_REQUEST, ex.getMessage());
        }
    }

    @GetMapping("/{username}/roles")
    public ResponseEntity<UserRolesResponse> getUserRoles(@PathVariable String username) {
        try {
            User user = userService.getByUsername(username);
            return ResponseEntity.ok(toRolesResponse(user));
        } catch (UsernameNotFoundException ex) {
            throw new ResponseStatusException(NOT_FOUND, ex.getMessage());
        }
    }

    @PatchMapping("/{username}")
    public ResponseEntity<UserUpdateResponse> updateUser(
            @PathVariable String username,
            @Valid @RequestBody UserUpdateRequest request) {
        try {
            User updatedUser = userService.updateUser(username, request.roles(), request.locked());
            return ResponseEntity.ok(toUserUpdateResponse(updatedUser));
        } catch (UsernameNotFoundException ex) {
            throw new ResponseStatusException(NOT_FOUND, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(BAD_REQUEST, ex.getMessage());
        }
    }

    @PatchMapping("/{username}/active-role")
    public ResponseEntity<UserUpdateResponse> switchActiveRole(
            @PathVariable String username,
            Authentication authentication,
            @Valid @RequestBody ActiveRoleSwitchRequest request) {
        try {
            if (authentication == null || !username.equals(authentication.getName())) {
                throw new ResponseStatusException(FORBIDDEN, "You can only switch your own active role");
            }

            User updatedUser = userService.switchActiveRole(username, request.roleName());
            return ResponseEntity.ok(toUserUpdateResponse(updatedUser));
        } catch (UsernameNotFoundException ex) {
            throw new ResponseStatusException(NOT_FOUND, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(BAD_REQUEST, ex.getMessage());
        }
    }

    @DeleteMapping("/{username}")
    public ResponseEntity<UserUpdateResponse> deleteUser(@PathVariable String username) {
        try {
            UserUpdateResponse deletedUser = userService.deleteUserByUsername(username);
            return ResponseEntity.ok(deletedUser);
        } catch (UsernameNotFoundException ex) {
            throw new ResponseStatusException(NOT_FOUND, ex.getMessage());
        }
    }

    private UserRolesResponse toRolesResponse(User user) {
        return new UserRolesResponse(
                user.getId(),
                user.getUsername(),
                user.getRoles().stream().map(role -> role.getName()).collect(java.util.stream.Collectors.toSet()));
    }

    private UserUpdateResponse toUserUpdateResponse(User user) {
        return new UserUpdateResponse(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getRoles().stream().map(role -> role.getName()).collect(java.util.stream.Collectors.toSet()),
                user.getActiveRole() != null ? user.getActiveRole().getName() : null,
                user.isLocked());
    }
}
