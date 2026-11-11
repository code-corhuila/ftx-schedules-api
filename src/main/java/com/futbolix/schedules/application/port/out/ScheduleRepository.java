package com.futbolix.schedules.application.port.out;

import com.futbolix.schedules.domain.CourtSchedule;
import com.futbolix.schedules.domain.DayCode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScheduleRepository {

    List<CourtSchedule> findByCourt(UUID courtId);

    Optional<CourtSchedule> findByCourtAndDay(UUID courtId, DayCode day);

    /** Creates or replaces the schedule of that court and day; returns it as stored. */
    CourtSchedule save(CourtSchedule schedule);

    /** Removes the schedule of that court and day; false when there was none. */
    boolean delete(UUID courtId, DayCode day);
}
