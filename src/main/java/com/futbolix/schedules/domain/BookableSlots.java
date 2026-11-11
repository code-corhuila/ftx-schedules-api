package com.futbolix.schedules.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Whole-hour slots a court offers on a date (FR-003). It does not subtract reservations: that is
 * the reservation-service. A court that is not ACTIVE, or a day without schedule, offers none.
 */
public record BookableSlots(CourtInfo court, LocalDate date, List<Slot> slots) {

    public static BookableSlots on(LocalDate date, CourtInfo court, Optional<CourtSchedule> scheduleOfThatDay) {
        List<Slot> slots = court.isBookable()
                ? scheduleOfThatDay.map(CourtSchedule::slots).orElse(List.of())
                : List.of();
        return new BookableSlots(court, date, slots);
    }
}
