package com.futbolix.schedules.application.port.out;

import com.futbolix.schedules.domain.CourtSchedule;
import com.futbolix.schedules.domain.DayCode;
import java.util.Optional;
import java.util.UUID;

/** FR-036: every schedule change is recorded with who made it and the values before and after. */
public interface ScheduleAuditTrail {
    void scheduleChanged(String administratorId, UUID courtId, DayCode day,
                         Optional<CourtSchedule> before, Optional<CourtSchedule> after);
}
