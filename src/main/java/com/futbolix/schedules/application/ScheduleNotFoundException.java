package com.futbolix.schedules.application;

public class ScheduleNotFoundException extends RuntimeException {
    public ScheduleNotFoundException() {
        super("The court has no schedule for that day");
    }
}
