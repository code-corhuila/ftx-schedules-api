package com.futbolix.schedules.application;

import com.futbolix.schedules.application.port.in.GetBookableSlots;
import com.futbolix.schedules.application.port.in.GetWeeklySchedule;
import com.futbolix.schedules.application.port.in.SaveSchedule;
import com.futbolix.schedules.application.port.out.CourtCatalog;
import com.futbolix.schedules.application.port.out.ScheduleAuditTrail;
import com.futbolix.schedules.application.port.out.ScheduleRepository;
import com.futbolix.schedules.domain.BookableSlots;
import com.futbolix.schedules.domain.CourtInfo;
import com.futbolix.schedules.domain.CourtSchedule;
import com.futbolix.schedules.domain.DayCode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Use cases of the schedules domain. No framework here: wiring and transactions live in the adapters. */
public class ScheduleService implements GetWeeklySchedule, GetBookableSlots, SaveSchedule {

    private final ScheduleRepository schedules;
    private final CourtCatalog courts;
    private final ScheduleAuditTrail audit;

    public ScheduleService(ScheduleRepository schedules, CourtCatalog courts, ScheduleAuditTrail audit) {
        this.schedules = schedules;
        this.courts = courts;
        this.audit = audit;
    }

    @Override
    public List<CourtSchedule> weeklySchedule(UUID courtId) {
        requireCourt(courtId);
        return schedules.findByCourt(courtId).stream()
                .sorted(Comparator.comparing(CourtSchedule::day))
                .toList();
    }

    @Override
    public BookableSlots bookableSlots(UUID courtId, LocalDate date) {
        CourtInfo court = requireCourt(courtId);
        return BookableSlots.on(date, court, schedules.findByCourtAndDay(courtId, DayCode.of(date.getDayOfWeek())));
    }

    @Override
    public CourtSchedule putSchedule(UUID courtId, DayCode day, LocalTime openingTime, LocalTime closingTime,
                                     String administratorId) {
        CourtSchedule wanted = CourtSchedule.open(courtId, day, openingTime, closingTime);
        requireCourt(courtId);
        Optional<CourtSchedule> before = schedules.findByCourtAndDay(courtId, day);
        CourtSchedule saved = schedules.save(wanted);
        audit.scheduleChanged(administratorId, courtId, day, before, Optional.of(saved));
        return saved;
    }

    @Override
    public void deleteSchedule(UUID courtId, DayCode day, String administratorId) {
        requireCourt(courtId);
        Optional<CourtSchedule> before = schedules.findByCourtAndDay(courtId, day);
        if (before.isEmpty() || !schedules.delete(courtId, day)) {
            throw new ScheduleNotFoundException();
        }
        audit.scheduleChanged(administratorId, courtId, day, before, Optional.empty());
    }

    private CourtInfo requireCourt(UUID courtId) {
        return courts.find(courtId).orElseThrow(() -> new CourtNotFoundException(courtId));
    }
}
