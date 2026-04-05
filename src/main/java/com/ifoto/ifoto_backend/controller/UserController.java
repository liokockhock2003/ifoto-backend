package com.ifoto.ifoto_backend.controller;

import com.ifoto.ifoto_backend.dto.UserDTO.UserListItemResponse;
import com.ifoto.ifoto_backend.dto.UserDTO.UserRolesResponse;
import com.ifoto.ifoto_backend.dto.UserDTO.UserUpdateRequest;
import com.ifoto.ifoto_backend.dto.UserDTO.UserUpdateResponse;
import com.ifoto.ifoto_backend.model.User;
import com.ifoto.ifoto_backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
        return ResponseEntity.ok(userService.listUsers(search, role, page, size));
    }

    @GetMapping("/{username}/roles")
    public ResponseEntity<UserRolesResponse> getUserRoles(@PathVariable String username) {
        return ResponseEntity.ok(toRolesResponse(userService.getByUsername(username)));
    }

    @PatchMapping("/{username}")
    public ResponseEntity<UserUpdateResponse> updateUser(
            @PathVariable String username,
            @Valid @RequestBody UserUpdateRequest request) {
        return ResponseEntity.ok(toUserUpdateResponse(userService.updateUser(username, request.roles(), request.locked())));
    }

    @DeleteMapping("/{username}")
    public ResponseEntity<UserUpdateResponse> deleteUser(@PathVariable String username) {
        return ResponseEntity.ok(userService.deleteUserByUsername(username));
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
                user.isLocked());
    }
}
