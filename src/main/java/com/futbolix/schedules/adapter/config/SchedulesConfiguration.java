package com.futbolix.schedules.adapter.config;

import com.futbolix.schedules.adapter.out.courts.RestCourtCatalog;
import com.futbolix.schedules.application.ScheduleService;
import com.futbolix.schedules.application.port.in.GetBookableSlots;
import com.futbolix.schedules.application.port.in.GetWeeklySchedule;
import com.futbolix.schedules.application.port.in.SaveSchedule;
import com.futbolix.schedules.application.port.out.CourtCatalog;
import com.futbolix.schedules.application.port.out.ScheduleAuditTrail;
import com.futbolix.schedules.application.port.out.ScheduleRepository;
import com.futbolix.schedules.domain.BookableSlots;
import com.futbolix.schedules.domain.CourtSchedule;
import com.futbolix.schedules.domain.DayCode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

/** Wires the framework-free ScheduleService and the client of ftx-courts-api. */
@Configuration
public class SchedulesConfiguration {

    @Bean
    CourtCatalog courtCatalog(RestClient.Builder builder,
                              @Value("${courts-api.url}") String baseUrl,
                              @Value("${courts-api.timeout}") Duration timeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return new RestCourtCatalog(builder.baseUrl(baseUrl).requestFactory(factory).build());
    }

    @Bean
    ScheduleUseCases scheduleUseCases(ScheduleRepository schedules, CourtCatalog courts, ScheduleAuditTrail audit,
                                      TransactionTemplate transactions) {
        return new ScheduleUseCases(new ScheduleService(schedules, courts, audit), transactions);
    }

    /** Writes run in one transaction: the "before" read for the audit and the write see the same row. */
    static final class ScheduleUseCases implements GetWeeklySchedule, GetBookableSlots, SaveSchedule {

        private final ScheduleService service;
        private final TransactionTemplate transactions;

        ScheduleUseCases(ScheduleService service, TransactionTemplate transactions) {
            this.service = service;
            this.transactions = transactions;
        }

        @Override
        public List<CourtSchedule> weeklySchedule(UUID courtId) {
            return service.weeklySchedule(courtId);
        }

        @Override
        public BookableSlots bookableSlots(UUID courtId, LocalDate date) {
            return service.bookableSlots(courtId, date);
        }

        @Override
        public CourtSchedule putSchedule(UUID courtId, DayCode day, LocalTime openingTime, LocalTime closingTime,
                                         String administratorId) {
            return transactions.execute(status ->
                    service.putSchedule(courtId, day, openingTime, closingTime, administratorId));
        }

        @Override
        public void deleteSchedule(UUID courtId, DayCode day, String administratorId) {
            transactions.executeWithoutResult(status -> service.deleteSchedule(courtId, day, administratorId));
        }
    }
}
