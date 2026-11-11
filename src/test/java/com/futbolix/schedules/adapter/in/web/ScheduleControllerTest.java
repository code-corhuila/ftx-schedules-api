package com.futbolix.schedules.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.futbolix.schedules.application.CourtCatalogUnavailableException;
import com.futbolix.schedules.application.CourtNotFoundException;
import com.futbolix.schedules.application.ScheduleNotFoundException;
import com.futbolix.schedules.application.port.in.GetBookableSlots;
import com.futbolix.schedules.application.port.in.GetWeeklySchedule;
import com.futbolix.schedules.application.port.in.SaveSchedule;
import com.futbolix.schedules.domain.BookableSlots;
import com.futbolix.schedules.domain.CourtInfo;
import com.futbolix.schedules.domain.CourtSchedule;
import com.futbolix.schedules.domain.DayCode;
import com.futbolix.schedules.domain.InvalidScheduleException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {ScheduleController.class, AdminScheduleController.class})
@Import({ApiExceptionHandler.class, CorrelationIdFilter.class, AdministratorInterceptor.class, WebConfiguration.class,
        TestVerifierConfiguration.class})
class ScheduleControllerTest {

    static final UUID COURT_1 = UUID.fromString("c0000000-0000-4000-8000-000000000001");
    static final String ADMIN_URL = "/api/v1/admin/courts/" + COURT_1 + "/schedules/MON";

    @Autowired MockMvc mvc;
    @MockitoBean GetWeeklySchedule weeklySchedule;
    @MockitoBean GetBookableSlots bookableSlots;
    @MockitoBean SaveSchedule saveSchedule;

    static CourtSchedule monday(int open, int close) {
        return new CourtSchedule(UUID.randomUUID(), COURT_1, DayCode.MON, LocalTime.of(open, 0), LocalTime.of(close, 0));
    }

    @Test
    void weeklyScheduleUsesTheContractTimeFormat() throws Exception {
        when(weeklySchedule.weeklySchedule(COURT_1)).thenReturn(List.of(monday(6, 22)));
        mvc.perform(get("/api/v1/courts/{id}/schedules", COURT_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].courtId").value(COURT_1.toString()))
                .andExpect(jsonPath("$.data[0].dayOfWeek").value("MON"))
                .andExpect(jsonPath("$.data[0].openingTime").value("06:00:00"))
                .andExpect(jsonPath("$.data[0].closingTime").value("22:00:00"));
    }

    @Test
    void bookableSlotsCarryStatusPriceAndSlots() throws Exception {
        LocalDate date = LocalDate.of(2026, 11, 16);
        when(bookableSlots.bookableSlots(COURT_1, date)).thenReturn(BookableSlots.on(date,
                new CourtInfo(COURT_1, "ACTIVE", 60000, "COP"), Optional.of(monday(18, 20))));
        mvc.perform(get("/api/v1/courts/{id}/bookable-slots", COURT_1).param("date", "2026-11-16"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-11-16"))
                .andExpect(jsonPath("$.courtStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.hourlyPrice.amount").value(60000))
                .andExpect(jsonPath("$.slots.length()").value(2))
                .andExpect(jsonPath("$.slots[1].startTime").value("19:00:00"))
                .andExpect(jsonPath("$.slots[1].endTime").value("20:00:00"));
    }

    @Test
    void bookableSlotsNeedAValidDate() throws Exception {
        mvc.perform(get("/api/v1/courts/{id}/bookable-slots", COURT_1))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details[0].field").value("date"));
        mvc.perform(get("/api/v1/courts/{id}/bookable-slots", COURT_1).param("date", "16/11/2026"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details[0].field").value("date"));
    }

    @Test
    void unknownCourtIs404AndCourtsDownIs503() throws Exception {
        when(weeklySchedule.weeklySchedule(any())).thenThrow(new CourtNotFoundException(COURT_1));
        mvc.perform(get("/api/v1/courts/{id}/schedules", COURT_1))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("NOT_FOUND"));

        when(bookableSlots.bookableSlots(any(), any())).thenThrow(new CourtCatalogUnavailableException("down", null));
        mvc.perform(get("/api/v1/courts/{id}/bookable-slots", COURT_1).param("date", "2026-11-16"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    void anAdministratorSavesADay() throws Exception {
        when(saveSchedule.putSchedule(eq(COURT_1), eq(DayCode.MON), eq(LocalTime.of(8, 0)), eq(LocalTime.of(20, 0)), eq("admin-7")))
                .thenReturn(monday(8, 20));
        mvc.perform(put(ADMIN_URL).contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + TestTokens.administrator("admin-7"))
                        .content("{\"openingTime\": \"08:00:00\", \"closingTime\": \"20:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openingTime").value("08:00:00"));
    }

    // M1: the same change, two identities. Only the administrator gets through.
    @Test
    void anotherRoleOrNoIdentityChangesNothing() throws Exception {
        mvc.perform(put(ADMIN_URL).contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + TestTokens.withRole("user-2", "CUSTOMER"))
                        .content("{\"openingTime\": \"08:00:00\", \"closingTime\": \"20:00:00\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete(ADMIN_URL)).andExpect(status().isUnauthorized());
        verify(saveSchedule, never()).putSchedule(any(), any(), any(), any(), any());
        verify(saveSchedule, never()).deleteSchedule(any(), any(), any());
    }

    /**
     * Regression: the API used to trust X-User-Id / X-User-Role, and its port is published on the
     * host, so anyone could change the opening hours by sending those two headers.
     */
    @Test
    void spoofedHeadersAndBadTokensChangeNothing() throws Exception {
        mvc.perform(put(ADMIN_URL).contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "attacker").header("X-User-Role", "ADMINISTRATOR")
                        .content("{\"openingTime\": \"08:00:00\", \"closingTime\": \"20:00:00\"}"))
                .andExpect(status().isUnauthorized());
        for (String token : new String[] {TestTokens.expired("admin-7"), TestTokens.fromIssuer("admin-7", "someone-else"),
                TestTokens.signedByAnotherKey("admin-7"), "not-a-jwt"}) {
            mvc.perform(delete(ADMIN_URL).header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        }
        verify(saveSchedule, never()).putSchedule(any(), any(), any(), any(), any());
        verify(saveSchedule, never()).deleteSchedule(any(), any(), any());
    }

    @Test
    void badInputIs400AndAnInvertedScheduleIs422() throws Exception {
        mvc.perform(put(ADMIN_URL).contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + TestTokens.administrator("admin-7"))
                        .content("{\"openingTime\": \"8:00\", \"closingTime\": \"20:00:00\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details[0].field").value("openingTime"));
        mvc.perform(put("/api/v1/admin/courts/" + COURT_1 + "/schedules/MONDAY").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + TestTokens.administrator("admin-7"))
                        .content("{\"openingTime\": \"08:00:00\", \"closingTime\": \"20:00:00\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details[0].field").value("dayOfWeek"));
        mvc.perform(put(ADMIN_URL).contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + TestTokens.administrator("admin-7"))
                        .content("{\"openingTime\": \"08:00:00\", \"closingTime\": \"20:00:00\", \"note\": \"x\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details[0].field").value("note"));

        when(saveSchedule.putSchedule(any(), any(), any(), any(), any())).thenThrow(new InvalidScheduleException());
        mvc.perform(put(ADMIN_URL).contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + TestTokens.administrator("admin-7"))
                        .content("{\"openingTime\": \"22:00:00\", \"closingTime\": \"06:00:00\"}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error").value("INVALID_SCHEDULE"));
    }

    @Test
    void deletingIs204AndAMissingDayIs404() throws Exception {
        mvc.perform(delete(ADMIN_URL).header("Authorization", "Bearer " + TestTokens.administrator("admin-7")))
                .andExpect(status().isNoContent());
        org.mockito.Mockito.doThrow(new ScheduleNotFoundException()).when(saveSchedule).deleteSchedule(any(), any(), any());
        mvc.perform(delete(ADMIN_URL).header("Authorization", "Bearer " + TestTokens.administrator("admin-7")))
                .andExpect(status().isNotFound());
    }
}
