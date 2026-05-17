package com.ifoto.ifoto_backend.validation;

import java.time.LocalDate;

public interface DateRangeValidatable {
    LocalDate getStartDate();
    LocalDate getEndDate();
}
