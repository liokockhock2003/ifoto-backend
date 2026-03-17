package com.ifoto.ifoto_backend.controller;

import com.ifoto.ifoto_backend.dto.UserListItemResponse;
import com.ifoto.ifoto_backend.dto.UserRoleRequest;
import com.ifoto.ifoto_backend.dto.UserRolesRequest;
import com.ifoto.ifoto_backend.dto.UserRolesResponse;
import com.ifoto.ifoto_backend.model.User;
import com.ifoto.ifoto_backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
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

    @PostMapping("/{username}/roles")
    public ResponseEntity<UserRolesResponse> addRoleToUser(
            @PathVariable String username,
            @Valid @RequestBody UserRoleRequest request) {
        try {
            User updatedUser = userService.assignRoleToUser(username, request.roleName());
            return ResponseEntity.ok(toRolesResponse(updatedUser));
        } catch (UsernameNotFoundException ex) {
            throw new ResponseStatusException(NOT_FOUND, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(BAD_REQUEST, ex.getMessage());
        }
    }

    @PutMapping("/{username}/roles")
    public ResponseEntity<UserRolesResponse> replaceUserRoles(
            @PathVariable String username,
            @Valid @RequestBody UserRolesRequest request) {
        try {
            User updatedUser = userService.replaceUserRoles(username, request.roles());
            return ResponseEntity.ok(toRolesResponse(updatedUser));
        } catch (UsernameNotFoundException ex) {
            throw new ResponseStatusException(NOT_FOUND, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(BAD_REQUEST, ex.getMessage());
        }
    }

    @DeleteMapping("/{username}/roles/{roleName}")
    public ResponseEntity<UserRolesResponse> removeRoleFromUser(
            @PathVariable String username,
            @PathVariable String roleName) {
        try {
            User updatedUser = userService.removeRoleFromUser(username, roleName);
            return ResponseEntity.ok(toRolesResponse(updatedUser));
        } catch (UsernameNotFoundException ex) {
            throw new ResponseStatusException(NOT_FOUND, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(BAD_REQUEST, ex.getMessage());
        }
    }

    private UserRolesResponse toRolesResponse(User user) {
        return new UserRolesResponse(
                user.getId(),
                user.getUsername(),
                user.getRoles().stream().map(role -> role.getName()).collect(java.util.stream.Collectors.toSet()));
    }
}
