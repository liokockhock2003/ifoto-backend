package com.ifoto.ifoto_backend.dto.UserDTO;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO for user registration requests. Frontend should send plain password in
 * the `password` field; the service will hash it before persisting.
 */
public record RegisterRequest(
	@NotBlank @Size(min = 3, max = 50) String username,
	@NotBlank @Email @Size(max = 255) String email,
	@NotBlank @Size(min = 8) String password,
	@Size(max = 100) String fullName,
	@Size(max = 20) String phoneNumber,
	String profilePicture
) {
}
