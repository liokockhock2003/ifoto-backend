package com.ifoto.ifoto_backend.unit.validation;

import com.ifoto.ifoto_backend.validation.DateTimeRangeValidatable;
import com.ifoto.ifoto_backend.validation.DateTimeRangeValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DateTimeRangeValidatorTest {

    private DateTimeRangeValidator validator;

    @BeforeEach
    void setUp() {
        validator = new DateTimeRangeValidator();
    }

    @Test
    void isValid_nullStartDatetime_returnsTrue() {
        assertTrue(validator.isValid(range(null, LocalDateTime.now()), null));
    }

    @Test
    void isValid_nullEndDatetime_returnsTrue() {
        assertTrue(validator.isValid(range(LocalDateTime.now(), null), null));
    }

    @Test
    void isValid_startEqualsEnd_returnsTrue() {
        LocalDateTime now = LocalDateTime.now();
        assertTrue(validator.isValid(range(now, now), null));
    }

    @Test
    void isValid_startBeforeEnd_returnsTrue() {
        assertTrue(validator.isValid(
                range(LocalDateTime.of(2025, 1, 1, 9, 0), LocalDateTime.of(2025, 1, 1, 17, 0)),
                null));
    }

    @Test
    void isValid_endBeforeStart_returnsFalse() {
        assertFalse(validator.isValid(
                range(LocalDateTime.of(2025, 1, 1, 17, 0), LocalDateTime.of(2025, 1, 1, 9, 0)),
                null));
    }

    private DateTimeRangeValidatable range(LocalDateTime start, LocalDateTime end) {
        return new DateTimeRangeValidatable() {
            @Override public LocalDateTime getStartDatetime() { return start; }
            @Override public LocalDateTime getEndDatetime() { return end; }
        };
    }
}
