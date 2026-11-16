package com.futbolix.schedules.adapter.out.persistence;

import com.futbolix.schedules.application.port.out.ScheduleRepository;
import com.futbolix.schedules.domain.CourtSchedule;
import com.futbolix.schedules.domain.DayCode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** schedules.court_schedule of ftx-schedules-db, as schedules_app (schedules_writer). */
@Repository
public class JdbcScheduleRepository implements ScheduleRepository {

    private static final String COLUMNS = "id, court_id, day_of_week, opening_time, closing_time";

    private final JdbcClient jdbc;

    public JdbcScheduleRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<CourtSchedule> findByCourt(UUID courtId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM schedules.court_schedule WHERE court_id = :court")
                .param("court", courtId)
                .query(JdbcScheduleRepository::toSchedule)
                .list();
    }

    @Override
    public Optional<CourtSchedule> findByCourtAndDay(UUID courtId, DayCode day) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM schedules.court_schedule WHERE court_id = :court AND day_of_week = :day")
                .param("court", courtId)
                .param("day", day.name())
                .query(JdbcScheduleRepository::toSchedule)
                .optional();
    }

    /** One statement on idx_court_schedule_court_id_day_of_week: no race between check and write. */
    @Override
    public CourtSchedule save(CourtSchedule schedule) {
        return jdbc.sql("""
                INSERT INTO schedules.court_schedule (court_id, day_of_week, opening_time, closing_time)
                VALUES (:court, :day, :opening, :closing)
                ON CONFLICT (court_id, day_of_week)
                DO UPDATE SET opening_time = EXCLUDED.opening_time, closing_time = EXCLUDED.closing_time
                RETURNING """ + " " + COLUMNS)
                .param("court", schedule.courtId())
                .param("day", schedule.day().name())
                .param("opening", schedule.openingTime())
                .param("closing", schedule.closingTime())
                .query(JdbcScheduleRepository::toSchedule)
                .single();
    }

    @Override
    public boolean delete(UUID courtId, DayCode day) {
        return jdbc.sql("DELETE FROM schedules.court_schedule WHERE court_id = :court AND day_of_week = :day")
                .param("court", courtId)
                .param("day", day.name())
                .update() > 0;
    }

    private static CourtSchedule toSchedule(ResultSet rs, int rowNum) throws SQLException {
        return new CourtSchedule(
                rs.getObject("id", UUID.class),
                rs.getObject("court_id", UUID.class),
                DayCode.valueOf(rs.getString("day_of_week")),
                rs.getObject("opening_time", LocalTime.class),
                rs.getObject("closing_time", LocalTime.class));
    }
}
