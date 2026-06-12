package com.ifoto.ifoto_backend.unit.service;

import com.ifoto.ifoto_backend.config.GlobalExceptionHandler;
import com.ifoto.ifoto_backend.dto.ErrorResponse;
import com.ifoto.ifoto_backend.exception.TokenException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.HttpStatus.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleBadRequest_returns400WithMessage() {
        ResponseEntity<ErrorResponse> response =
                handler.handleBadRequest(new IllegalArgumentException("bad input"));
        assertEquals(400, response.getStatusCode().value());
        assertEquals("bad input", response.getBody().message());
    }

    @Test
    void handleNotFound_returns404WithMessage() {
        ResponseEntity<ErrorResponse> response =
                handler.handleNotFound(new UsernameNotFoundException("user not found"));
        assertEquals(404, response.getStatusCode().value());
        assertEquals("user not found", response.getBody().message());
    }

    @Test
    void handleServerError_returns500WithGenericMessage() {
        ResponseEntity<ErrorResponse> response =
                handler.handleServerError(new IllegalStateException("internal boom"));
        assertEquals(500, response.getStatusCode().value());
        assertEquals("An unexpected error occurred", response.getBody().message());
    }

    @Test
    void handleBadCredentials_returns401WithGenericMessage() {
        ResponseEntity<ErrorResponse> response =
                handler.handleBadCredentials(new BadCredentialsException("wrong password"));
        assertEquals(401, response.getStatusCode().value());
        assertEquals("Invalid username or password", response.getBody().message());
    }

    @Test
    void handleUnauthorized_returns401WithAuthFailedMessage() {
        ResponseEntity<ErrorResponse> response =
                handler.handleUnauthorized(new AuthenticationException("expired") {});
        assertEquals(401, response.getStatusCode().value());
        assertEquals("Authentication failed", response.getBody().message());
    }

    @Test
    void handleToken_missingReason_returns400WithActualMessage() {
        TokenException ex = new TokenException(TokenException.Reason.MISSING, "Token is missing");
        ResponseEntity<ErrorResponse> response = handler.handleToken(ex);
        assertEquals(400, response.getStatusCode().value());
        assertEquals("Token is missing", response.getBody().message());
    }

    @Test
    void handleToken_invalidReason_returns400WithGenericMessage() {
        TokenException ex = new TokenException(TokenException.Reason.INVALID, "bad token");
        ResponseEntity<ErrorResponse> response = handler.handleToken(ex);
        assertEquals(400, response.getStatusCode().value());
        assertEquals("Invalid or expired verification token", response.getBody().message());
    }

    @Test
    void handleToken_alreadyUsedReason_returns400WithGenericMessage() {
        TokenException ex = new TokenException(TokenException.Reason.ALREADY_USED, "already used");
        ResponseEntity<ErrorResponse> response = handler.handleToken(ex);
        assertEquals(400, response.getStatusCode().value());
        assertEquals("Invalid or expired verification token", response.getBody().message());
    }

    @Test
    void handleToken_expiredReason_returns400WithGenericMessage() {
        TokenException ex = new TokenException(TokenException.Reason.EXPIRED, "expired");
        ResponseEntity<ErrorResponse> response = handler.handleToken(ex);
        assertEquals(400, response.getStatusCode().value());
        assertEquals("Invalid or expired verification token", response.getBody().message());
    }

    @Test
    void handleResponseStatus_passthroughStatusAndReason() {
        ResponseStatusException ex = new ResponseStatusException(NOT_FOUND, "resource missing");
        ResponseEntity<ErrorResponse> response = handler.handleResponseStatus(ex);
        assertEquals(404, response.getStatusCode().value());
        assertEquals("resource missing", response.getBody().message());
    }

    @Test
    void handleUnexpected_returns500WithGenericMessage() {
        ResponseEntity<ErrorResponse> response =
                handler.handleUnexpected(new RuntimeException("unexpected"));
        assertEquals(500, response.getStatusCode().value());
        assertEquals("An unexpected error occurred", response.getBody().message());
    }
}
