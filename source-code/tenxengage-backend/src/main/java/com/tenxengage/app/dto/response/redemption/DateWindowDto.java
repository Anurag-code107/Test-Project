package com.tenxengage.app.dto.response.redemption;

import java.time.LocalDate;

public record DateWindowDto(
        LocalDate from,
        LocalDate to
) {

    public static DateWindowDto of(LocalDate from, LocalDate to) {
        return new DateWindowDto(from, to);
    }
}
