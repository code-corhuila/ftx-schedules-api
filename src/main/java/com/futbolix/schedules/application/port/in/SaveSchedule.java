package com.futbolix.schedules.application.port.in;

import com.futbolix.schedules.domain.CourtSchedule;
import com.futbolix.schedules.domain.DayCode;
import java.time.LocalTime;
import java.util.UUID;

/** FR-029: an administrator creates or replaces the schedule of one day, or removes it. */
public interface SaveSchedule {

    CourtSchedule putSchedule(UUID courtId, DayCode day, LocalTime openingTime, LocalTime closingTime,
                              String administratorId);

    void deleteSchedule(UUID courtId, DayCode day, String administratorId);
}
