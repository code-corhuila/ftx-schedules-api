package com.futbolix.schedules.domain;

/** The closing time is not after the opening time (422 INVALID_SCHEDULE in the contract). */
public class InvalidScheduleException extends RuntimeException {
    public InvalidScheduleException() {
        super("The closing time must be after the opening time");
    }
}
