package com.futbolix.schedules.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CourtScheduleTest {

    static final UUID COURT = UUID.fromString("c0000000-0000-4000-8000-000000000001");

    @Test
    void slotsAreWholeHoursFromOpeningToClosing() {
        CourtSchedule schedule = CourtSchedule.open(COURT, DayCode.MON, LocalTime.of(18, 0), LocalTime.of(21, 0));
        assertThat(schedule.slots()).containsExactly(
                new Slot(LocalTime.of(18, 0), LocalTime.of(19, 0)),
                new Slot(LocalTime.of(19, 0), LocalTime.of(20, 0)),
                new Slot(LocalTime.of(20, 0), LocalTime.of(21, 0)));
    }

    @Test
    void closingMustBeAfterOpening() {
        assertThatThrownBy(() -> CourtSchedule.open(COURT, DayCode.MON, LocalTime.of(22, 0), LocalTime.of(6, 0)))
                .isInstanceOf(InvalidScheduleException.class);
        assertThatThrownBy(() -> CourtSchedule.open(COURT, DayCode.MON, LocalTime.of(8, 0), LocalTime.of(8, 0)))
                .isInstanceOf(InvalidScheduleException.class);
    }

    @Test
    void onlyWholeHours() {
        assertThatThrownBy(() -> CourtSchedule.open(COURT, DayCode.MON, LocalTime.of(6, 30), LocalTime.of(22, 0)))
                .isInstanceOf(InvalidScheduleTimeException.class)
                .extracting(e -> ((InvalidScheduleTimeException) e).field()).isEqualTo("openingTime");
    }

    @Test
    void dayCodeFollowsTheCalendar() {
        assertThat(DayCode.of(DayOfWeek.MONDAY)).isEqualTo(DayCode.MON);
        assertThat(DayCode.of(LocalDate.of(2026, 11, 15).getDayOfWeek())).isEqualTo(DayCode.SUN);
    }

    @Test
    void aCourtThatIsNotActiveOffersNoSlots() {
        CourtSchedule schedule = CourtSchedule.open(COURT, DayCode.MON, LocalTime.of(6, 0), LocalTime.of(22, 0));
        CourtInfo maintenance = new CourtInfo(COURT, "UNDER_MAINTENANCE", 60000, "COP");
        CourtInfo active = new CourtInfo(COURT, "ACTIVE", 60000, "COP");
        LocalDate monday = LocalDate.of(2026, 11, 16);

        assertThat(BookableSlots.on(monday, maintenance, Optional.of(schedule)).slots()).isEmpty();
        assertThat(BookableSlots.on(monday, active, Optional.of(schedule)).slots()).hasSize(16);
        assertThat(BookableSlots.on(monday, active, Optional.empty()).slots()).isEmpty();
    }
}
