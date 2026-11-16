package com.futbolix.schedules;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.futbolix.schedules.adapter.in.web.AdministratorTokenVerifier;
import com.futbolix.schedules.adapter.in.web.TestTokens;
import com.futbolix.schedules.adapter.out.persistence.JdbcScheduleRepositoryIntegrationTest;
import com.futbolix.schedules.application.port.out.CourtCatalog;
import com.futbolix.schedules.domain.CourtInfo;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The whole application against the real schedules schema, connected as schedules_app. The courts
 * service is replaced by a mock of its port: its HTTP client has its own test.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SchedulesApiApplicationTest {

    // Each test works on its own court, so the order JUnit picks cannot change the result.
    static final UUID COURT_2 = UUID.fromString("c0000000-0000-4000-8000-000000000002");
    static final UUID COURT_3 = UUID.fromString("c0000000-0000-4000-8000-000000000003");
    static final String ADMIN = "/api/v1/admin/courts/" + COURT_3 + "/schedules/";

    @Autowired MockMvc mvc;

    /** Trusts the key of TestTokens instead of fetching the JWKS of ftx-customers-api. */
    @TestBean AdministratorTokenVerifier administratorTokenVerifier;

    static AdministratorTokenVerifier administratorTokenVerifier() {
        return TestTokens.verifier();
    }
    @MockitoBean CourtCatalog courts;

    @BeforeAll
    static void database() {
        JdbcScheduleRepositoryIntegrationTest.migrateRealSchema();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> JdbcScheduleRepositoryIntegrationTest.url);
        registry.add("spring.datasource.username", () -> "schedules_app");
        registry.add("spring.datasource.password", () -> JdbcScheduleRepositoryIntegrationTest.appPassword);
    }

    @BeforeEach
    void activeCourt() {
        when(courts.find(any())).thenReturn(Optional.empty());
        when(courts.find(COURT_2)).thenReturn(Optional.of(new CourtInfo(COURT_2, "ACTIVE", 60000, "COP")));
        when(courts.find(COURT_3)).thenReturn(Optional.of(new CourtInfo(COURT_3, "ACTIVE", 90000, "COP")));
    }

    @Test
    void readsTheWeekAndTheSlotsOfADate() throws Exception {
        mvc.perform(get("/api/v1/courts/{id}/schedules", COURT_2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(7))
                .andExpect(jsonPath("$.data[0].dayOfWeek").value("MON"));
        mvc.perform(get("/api/v1/courts/{id}/bookable-slots", COURT_2).param("date", "2026-11-18"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots.length()").value(16))
                .andExpect(jsonPath("$.slots[0].startTime").value("06:00:00"));
        mvc.perform(get("/api/v1/courts/{id}/schedules", UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void anAdministratorChangesAndClosesADay() throws Exception {
        mvc.perform(put(ADMIN + "THU").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + TestTokens.administrator("admin-1"))
                        .content("{\"openingTime\": \"10:00:00\", \"closingTime\": \"14:00:00\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/courts/{id}/bookable-slots", COURT_3).param("date", "2026-11-19"))
                .andExpect(jsonPath("$.slots.length()").value(4));

        mvc.perform(delete(ADMIN + "FRI").header("Authorization", "Bearer " + TestTokens.administrator("admin-1")))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/courts/{id}/bookable-slots", COURT_3).param("date", "2026-11-20"))
                .andExpect(jsonPath("$.slots.length()").value(0));
        mvc.perform(delete(ADMIN + "FRI").header("Authorization", "Bearer " + TestTokens.administrator("admin-1")))
                .andExpect(status().isNotFound());
    }

    @Test
    void healthReportsTheDatabase() throws Exception {
        mvc.perform(get("/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        // The contract health of court-service.yaml.
        mvc.perform(get("/api/v1/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.dependencies.database").value("ok"));
    }
}
