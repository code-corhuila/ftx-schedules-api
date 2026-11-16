package com.futbolix.schedules.adapter.out.audit;

import com.futbolix.schedules.application.port.out.ScheduleAuditTrail;
import com.futbolix.schedules.domain.CourtSchedule;
import com.futbolix.schedules.domain.DayCode;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** FR-036 through the structured log (logger "audit"): who changed which schedule, before and after. */
@Component
public class LoggingScheduleAuditTrail implements ScheduleAuditTrail {

    private static final Logger audit = LoggerFactory.getLogger("audit");

    @Override
    public void scheduleChanged(String administratorId, UUID courtId, DayCode day,
                                Optional<CourtSchedule> before, Optional<CourtSchedule> after) {
        audit.atInfo()
                .addKeyValue("event", after.isPresent() ? "schedule.saved" : "schedule.deleted")
                .addKeyValue("administratorId", administratorId)
                .addKeyValue("courtId", courtId)
                .addKeyValue("dayOfWeek", day)
                .addKeyValue("before", before.map(LoggingScheduleAuditTrail::hours).orElse("closed"))
                .addKeyValue("after", after.map(LoggingScheduleAuditTrail::hours).orElse("closed"))
                .log("schedule changed");
    }

    private static String hours(CourtSchedule schedule) {
        return schedule.openingTime() + "-" + schedule.closingTime();
    }
}
