package com.ifoto.ifoto_backend.validation;

import java.time.LocalDateTime;

public interface DateTimeRangeValidatable {
    LocalDateTime getStartDatetime();
    LocalDateTime getEndDatetime();
}
