package com.ifoto.ifoto_backend.unit.validation;

import com.ifoto.ifoto_backend.validation.DateRangeValidatable;
import com.ifoto.ifoto_backend.validation.DateRangeValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DateRangeValidatorTest {

    private DateRangeValidator validator;

    @BeforeEach
    void setUp() {
        validator = new DateRangeValidator();
    }

    @Test
    void isValid_nullStartDate_returnsTrue() {
        assertTrue(validator.isValid(range(null, LocalDate.now()), null));
    }

    @Test
    void isValid_nullEndDate_returnsTrue() {
        assertTrue(validator.isValid(range(LocalDate.now(), null), null));
    }

    @Test
    void isValid_startEqualsEnd_returnsTrue() {
        LocalDate today = LocalDate.now();
        assertTrue(validator.isValid(range(today, today), null));
    }

    @Test
    void isValid_startBeforeEnd_returnsTrue() {
        assertTrue(validator.isValid(range(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 5)), null));
    }

    @Test
    void isValid_endBeforeStart_returnsFalse() {
        assertFalse(validator.isValid(range(LocalDate.of(2025, 1, 5), LocalDate.of(2025, 1, 1)), null));
    }

    private DateRangeValidatable range(LocalDate start, LocalDate end) {
        return new DateRangeValidatable() {
            @Override public LocalDate getStartDate() { return start; }
            @Override public LocalDate getEndDate() { return end; }
        };
    }
}
