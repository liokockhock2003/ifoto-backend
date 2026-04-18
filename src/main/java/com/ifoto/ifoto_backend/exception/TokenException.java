package com.ifoto.ifoto_backend.exception;

public class TokenException extends RuntimeException {

    public enum Reason { MISSING, INVALID, ALREADY_USED, EXPIRED }

    private final Reason reason;

    public TokenException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
