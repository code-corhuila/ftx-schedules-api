package com.futbolix.schedules.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.futbolix.schedules.application.port.in.GetBookableSlots;
import com.futbolix.schedules.application.port.in.GetWeeklySchedule;
import com.futbolix.schedules.application.port.in.SaveSchedule;
import com.nimbusds.jose.KeySourceException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** ftx-customers-api is down: administrator requests get 503, public reads keep working. */
@WebMvcTest(controllers = {ScheduleController.class, AdminScheduleController.class})
@Import({ApiExceptionHandler.class, CorrelationIdFilter.class, AdministratorInterceptor.class, WebConfiguration.class,
        AdminScheduleKeysUnavailableTest.KeysDown.class})
class AdminScheduleKeysUnavailableTest {

    @TestConfiguration
    static class KeysDown {
        @Bean
        AdministratorTokenVerifier administratorTokenVerifier() {
            return new AdministratorTokenVerifier((selector, context) -> {
                throw new KeySourceException("connection refused");
            }, TestTokens.ISSUER);
        }
    }

    @Autowired MockMvc mvc;
    @MockitoBean GetWeeklySchedule weeklySchedule;
    @MockitoBean GetBookableSlots bookableSlots;
    @MockitoBean SaveSchedule saveSchedule;

    @Test
    void administratorRequestsAre503AndNothingChanges() throws Exception {
        mvc.perform(delete(ScheduleControllerTest.ADMIN_URL)
                        .header("Authorization", "Bearer " + TestTokens.administrator("admin-1")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("SERVICE_UNAVAILABLE"));
        verify(saveSchedule, never()).deleteSchedule(any(), any(), any());
    }

    @Test
    void publicReadsDoNotNeedTheKeys() throws Exception {
        Mockito.when(weeklySchedule.weeklySchedule(any())).thenReturn(List.of());
        mvc.perform(get("/api/v1/courts/{id}/schedules", ScheduleControllerTest.COURT_1)).andExpect(status().isOk());
    }
}
