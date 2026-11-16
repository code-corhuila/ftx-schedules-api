package com.futbolix.schedules.adapter.in.web;

import com.futbolix.schedules.domain.CourtSchedule;
import java.util.UUID;

/** Schedule schema of court-service.yaml. */
public record ScheduleResponse(UUID id, UUID courtId, String dayOfWeek, String openingTime, String closingTime) {

    static ScheduleResponse from(CourtSchedule schedule) {
        return new ScheduleResponse(schedule.id(), schedule.courtId(), schedule.day().name(),
                Times.format(schedule.openingTime()), Times.format(schedule.closingTime()));
    }
}
