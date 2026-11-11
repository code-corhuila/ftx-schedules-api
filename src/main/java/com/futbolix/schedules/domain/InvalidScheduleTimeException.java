package com.futbolix.schedules.domain;

/** A time that is not a whole hour (reservations use whole-hour slots, HU-RES-001). */
public class InvalidScheduleTimeException extends RuntimeException {

    private final String field;

    public InvalidScheduleTimeException(String field) {
        super("Use whole hours, for example 08:00:00");
        this.field = field;
    }

    public String field() {
        return field;
    }
}
