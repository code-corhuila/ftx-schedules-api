package com.futbolix.schedules.application.port.in;

import com.futbolix.schedules.domain.CourtSchedule;
import java.util.List;
import java.util.UUID;

/** FR-002: the schedule of a court, MON to SUN. Throws CourtNotFoundException for an unknown court. */
public interface GetWeeklySchedule {
    List<CourtSchedule> weeklySchedule(UUID courtId);
}
