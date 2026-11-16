package com.futbolix.schedules.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.futbolix.schedules.application.port.out.CourtCatalog;
import com.futbolix.schedules.application.port.out.ScheduleAuditTrail;
import com.futbolix.schedules.application.port.out.ScheduleRepository;
import com.futbolix.schedules.domain.BookableSlots;
import com.futbolix.schedules.domain.CourtInfo;
import com.futbolix.schedules.domain.CourtSchedule;
import com.futbolix.schedules.domain.DayCode;
import com.futbolix.schedules.domain.InvalidScheduleException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScheduleServiceTest {

    static final UUID COURT_1 = UUID.fromString("c0000000-0000-4000-8000-000000000001");
    static final UUID COURT_4 = UUID.fromString("c0000000-0000-4000-8000-000000000004");
    static final LocalDate MONDAY = LocalDate.of(2026, 11, 16);

    final InMemorySchedules schedules = new InMemorySchedules();
    final Map<UUID, CourtInfo> courts = new HashMap<>();
    boolean courtsDown;
    final CourtCatalog catalog = id -> {
        if (courtsDown) {
            throw new CourtCatalogUnavailableException("courts api down", null);
        }
        return Optional.ofNullable(courts.get(id));
    };
    final List<String> audit = new ArrayList<>();
    final ScheduleAuditTrail auditTrail = (admin, court, day, before, after) ->
            audit.add(admin + " " + day + " " + before.map(s -> s.openingTime() + "-" + s.closingTime()).orElse("none")
                    + " -> " + after.map(s -> s.openingTime() + "-" + s.closingTime()).orElse("none"));
    final ScheduleService service = new ScheduleService(schedules, catalog, auditTrail);

    @BeforeEach
    void seed() {
        courts.put(COURT_1, new CourtInfo(COURT_1, "ACTIVE", 60000, "COP"));
        courts.put(COURT_4, new CourtInfo(COURT_4, "UNDER_MAINTENANCE", 90000, "COP"));
        for (DayCode day : new DayCode[] {DayCode.SUN, DayCode.MON, DayCode.TUE}) {
            schedules.save(CourtSchedule.open(COURT_1, day, LocalTime.of(6, 0), LocalTime.of(22, 0)));
        }
        schedules.save(CourtSchedule.open(COURT_4, DayCode.MON, LocalTime.of(6, 0), LocalTime.of(22, 0)));
    }

    @Test
    void weeklyScheduleIsOrderedMondayFirst() {
        assertThat(service.weeklySchedule(COURT_1)).extracting(CourtSchedule::day)
                .containsExactly(DayCode.MON, DayCode.TUE, DayCode.SUN);
    }

    @Test
    void anUnknownCourtIsNotFound() {
        assertThatThrownBy(() -> service.weeklySchedule(UUID.randomUUID())).isInstanceOf(CourtNotFoundException.class);
        assertThatThrownBy(() -> service.bookableSlots(UUID.randomUUID(), MONDAY)).isInstanceOf(CourtNotFoundException.class);
    }

    @Test
    void bookableSlotsCarryStatusAndPrice() {
        BookableSlots slots = service.bookableSlots(COURT_1, MONDAY);
        assertThat(slots.slots()).hasSize(16);
        assertThat(slots.court().hourlyPriceAmount()).isEqualTo(60000);

        assertThat(service.bookableSlots(COURT_1, MONDAY.plusDays(2)).slots()).as("wednesday has no schedule").isEmpty();
        BookableSlots maintenance = service.bookableSlots(COURT_4, MONDAY);
        assertThat(maintenance.slots()).isEmpty();
        assertThat(maintenance.court().status()).isEqualTo("UNDER_MAINTENANCE");
    }

    @Test
    void whenTheCourtsServiceIsDownTheCallFailsInsteadOfGuessing() {
        courtsDown = true;
        assertThatThrownBy(() -> service.bookableSlots(COURT_1, MONDAY)).isInstanceOf(CourtCatalogUnavailableException.class);
    }

    @Test
    void putReplacesTheDayAndIsAudited() {
        CourtSchedule saved = service.putSchedule(COURT_1, DayCode.MON, LocalTime.of(8, 0), LocalTime.of(20, 0), "admin-1");
        assertThat(saved.slots()).hasSize(12);
        assertThat(schedules.findByCourtAndDay(COURT_1, DayCode.MON)).get().extracting(CourtSchedule::openingTime)
                .isEqualTo(LocalTime.of(8, 0));
        assertThat(audit).containsExactly("admin-1 MON 06:00-22:00 -> 08:00-20:00");
    }

    @Test
    void anInvertedScheduleIsRejectedBeforeAnythingIsRead() {
        assertThatThrownBy(() -> service.putSchedule(UUID.randomUUID(), DayCode.MON, LocalTime.of(22, 0), LocalTime.of(6, 0), "admin-1"))
                .isInstanceOf(InvalidScheduleException.class);
        assertThat(audit).isEmpty();
    }

    @Test
    void deleteClosesTheDayAndAMissingDayIsNotFound() {
        service.deleteSchedule(COURT_1, DayCode.TUE, "admin-1");
        assertThat(schedules.findByCourtAndDay(COURT_1, DayCode.TUE)).isEmpty();
        assertThat(audit).containsExactly("admin-1 TUE 06:00-22:00 -> none");

        assertThatThrownBy(() -> service.deleteSchedule(COURT_1, DayCode.TUE, "admin-1"))
                .isInstanceOf(ScheduleNotFoundException.class);
        assertThat(audit).hasSize(1);
    }

    static final class InMemorySchedules implements ScheduleRepository {
        private final Map<String, CourtSchedule> rows = new HashMap<>();

        private static String key(UUID court, DayCode day) {
            return court + "/" + day;
        }

        @Override
        public List<CourtSchedule> findByCourt(UUID courtId) {
            return rows.values().stream().filter(s -> s.courtId().equals(courtId)).toList();
        }

        @Override
        public Optional<CourtSchedule> findByCourtAndDay(UUID courtId, DayCode day) {
            return Optional.ofNullable(rows.get(key(courtId, day)));
        }

        @Override
        public CourtSchedule save(CourtSchedule schedule) {
            CourtSchedule stored = new CourtSchedule(UUID.randomUUID(), schedule.courtId(), schedule.day(),
                    schedule.openingTime(), schedule.closingTime());
            rows.put(key(schedule.courtId(), schedule.day()), stored);
            return stored;
        }

        @Override
        public boolean delete(UUID courtId, DayCode day) {
            return rows.remove(key(courtId, day)) != null;
        }
    }
}
