package com.futbolix.schedules.application.port.in;

import com.futbolix.schedules.domain.BookableSlots;
import java.time.LocalDate;
import java.util.UUID;

/** FR-003: whole-hour slots of a court on a date, with its status and current price. */
public interface GetBookableSlots {
    BookableSlots bookableSlots(UUID courtId, LocalDate date);
}
