package com.futbolix.schedules.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.futbolix.schedules.domain.CourtSchedule;
import com.futbolix.schedules.domain.DayCode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Runs against a real PostgreSQL with the real migrations of the sibling ftx-schedules-db clone
 * (system property schedules.db.dir), connected as schedules_app with only the rights
 * ftx-schedules-db grants it. Database: FTX_IT_JDBC_URL / FTX_IT_ADMIN_USER / FTX_IT_ADMIN_PASSWORD
 * when set, otherwise a Testcontainers PostgreSQL 16 (CI).
 */
public class JdbcScheduleRepositoryIntegrationTest {

    static final UUID COURT_1 = UUID.fromString("c0000000-0000-4000-8000-000000000001");

    static PostgreSQLContainer<?> container;
    static JdbcClient admin;
    static JdbcClient app;
    static JdbcScheduleRepository repository;
    /** Shared with SchedulesApiApplicationTest, which reuses this database preparation. */
    public static String url;
    public static String appPassword;

    /** Fresh schedules schema from the real ftx-schedules-db migrations and a schedules_app user. */
    @BeforeAll
    public static void migrateRealSchema() {
        Path migrations = Path.of(System.getProperty("schedules.db.dir", "../ftx-schedules-db"));
        Assumptions.assumeTrue(Files.isDirectory(migrations.resolve("01_ddl")),
                "ftx-schedules-db not found at " + migrations.toAbsolutePath());

        url = System.getenv("FTX_IT_JDBC_URL");
        String adminUser = System.getenv("FTX_IT_ADMIN_USER");
        String adminPassword = System.getenv("FTX_IT_ADMIN_PASSWORD");
        if (url == null) {
            Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                    "no FTX_IT_JDBC_URL and no Docker: skipped");
            container = new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("ftx");
            container.start();
            url = container.getJdbcUrl();
            adminUser = container.getUsername();
            adminPassword = container.getPassword();
        }

        admin = JdbcClient.create(dataSource(url, adminUser, adminPassword));
        admin.sql("DROP SCHEMA IF EXISTS schedules CASCADE").update();
        admin.sql("DROP ROLE IF EXISTS schedules_writer").update();
        admin.sql("DROP ROLE IF EXISTS schedules_reader").update();
        appPassword = UUID.randomUUID().toString();
        boolean exists = admin.sql("SELECT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'schedules_app')")
                .query(Boolean.class).single();
        admin.sql((exists ? "ALTER" : "CREATE") + " ROLE schedules_app LOGIN PASSWORD '" + appPassword + "'").update();
        admin.sql("SELECT format('GRANT CONNECT ON DATABASE %I TO schedules_app', current_database())")
                .query(String.class).optional().ifPresent(grant -> admin.sql(grant).update());

        Flyway.configure()
                .dataSource(url, adminUser, adminPassword)
                .locations("filesystem:" + migrations.resolve("01_ddl"),
                        "filesystem:" + migrations.resolve("02_dml"),
                        "filesystem:" + migrations.resolve("03_dcl"))
                .schemas("schedules").defaultSchema("schedules")
                .load().migrate();

        app = JdbcClient.create(dataSource(url, "schedules_app", appPassword));
        repository = new JdbcScheduleRepository(app);
    }

    @AfterAll
    static void stop() {
        if (container != null) {
            container.stop();
        }
    }

    static PGSimpleDataSource dataSource(String url, String user, String password) {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(url);
        ds.setUser(user);
        ds.setPassword(password);
        return ds;
    }

    @Test
    void readsTheSeededWeekOfACourt() {
        assertThat(repository.findByCourt(COURT_1)).hasSize(7)
                .allSatisfy(s -> {
                    assertThat(s.openingTime()).isEqualTo(LocalTime.of(6, 0));
                    assertThat(s.closingTime()).isEqualTo(LocalTime.of(22, 0));
                });
        assertThat(repository.findByCourtAndDay(COURT_1, DayCode.SUN)).isPresent();
    }

    @Test
    void saveCreatesThenReplacesTheSameDay() {
        UUID court = UUID.randomUUID();
        CourtSchedule created = repository.save(CourtSchedule.open(court, DayCode.SAT, LocalTime.of(8, 0), LocalTime.of(12, 0)));
        CourtSchedule replaced = repository.save(CourtSchedule.open(court, DayCode.SAT, LocalTime.of(9, 0), LocalTime.of(23, 0)));

        assertThat(replaced.id()).isEqualTo(created.id());
        assertThat(repository.findByCourt(court)).singleElement()
                .satisfies(s -> assertThat(s.closingTime()).isEqualTo(LocalTime.of(23, 0)));
    }

    @Test
    void deleteRemovesTheDayAndReportsAMissingOne() {
        UUID court = UUID.randomUUID();
        repository.save(CourtSchedule.open(court, DayCode.WED, LocalTime.of(6, 0), LocalTime.of(22, 0)));
        assertThat(repository.delete(court, DayCode.WED)).isTrue();
        assertThat(repository.delete(court, DayCode.WED)).isFalse();
        assertThat(repository.findByCourtAndDay(court, DayCode.WED)).isEmpty();
    }

    @Test
    void theApplicationUserCannotChangeTheStructureNorReadTheMigrationHistory() {
        assertThatThrownBy(() -> app.sql("CREATE TABLE schedules.extra (id int)").update())
                .isInstanceOf(DataAccessException.class)
                .rootCause().isInstanceOf(java.sql.SQLException.class)
                .extracting(e -> ((java.sql.SQLException) e).getSQLState()).isEqualTo("42501"); // insufficient_privilege, any server language
        assertThatThrownBy(() -> app.sql("SELECT count(*) FROM schedules.flyway_schema_history").query(Long.class).single())
                .isInstanceOf(DataAccessException.class)
                .rootCause().isInstanceOf(java.sql.SQLException.class)
                .extracting(e -> ((java.sql.SQLException) e).getSQLState()).isEqualTo("42501"); // insufficient_privilege, any server language
    }
}
