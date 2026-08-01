package com.keenti.finances.domain.port.out;

import java.time.LocalDate;
import java.time.ZoneId;

public interface UserTimeZoneProvider {
    ZoneId getTimeZone();

    default LocalDate today() {
        return LocalDate.now(getTimeZone());
    }
}
