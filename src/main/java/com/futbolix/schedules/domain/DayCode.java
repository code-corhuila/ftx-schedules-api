package com.futbolix.schedules.domain;

import java.time.DayOfWeek;

/** Day of the week as the contract and schedules.court_schedule.day_of_week write it. */
public enum DayCode {
    MON, TUE, WED, THU, FRI, SAT, SUN;

    public static DayCode of(DayOfWeek day) {
        return values()[day.getValue() - 1];
    }
}
