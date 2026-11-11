package com.futbolix.schedules.domain;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Opening hours of one court on one day of the week. A day without schedule means the court is
 * closed that day. The court itself belongs to the courts domain: only its id lives here.
 */
public record CourtSchedule(UUID id, UUID courtId, DayCode day, LocalTime openingTime, LocalTime closingTime) {

    public CourtSchedule {
        Objects.requireNonNull(courtId, "courtId");
        Objects.requireNonNull(day, "day");
        requireWholeHour(openingTime, "openingTime");
        requireWholeHour(closingTime, "closingTime");
        if (!closingTime.isAfter(openingTime)) {
            throw new InvalidScheduleException();
        }
    }

    /** A schedule that is not stored yet: the database assigns its id. */
    public static CourtSchedule open(UUID courtId, DayCode day, LocalTime openingTime, LocalTime closingTime) {
        return new CourtSchedule(null, courtId, day, openingTime, closingTime);
    }

    /** The whole-hour slots between opening and closing, in order (BR-004). */
    public List<Slot> slots() {
        List<Slot> slots = new ArrayList<>();
        for (LocalTime start = openingTime; start.isBefore(closingTime); start = start.plusHours(1)) {
            slots.add(new Slot(start, start.plusHours(1)));
        }
        return slots;
    }

    private static void requireWholeHour(LocalTime time, String field) {
        Objects.requireNonNull(time, field);
        if (time.getMinute() != 0 || time.getSecond() != 0 || time.getNano() != 0) {
            throw new InvalidScheduleTimeException(field);
        }
    }
}
