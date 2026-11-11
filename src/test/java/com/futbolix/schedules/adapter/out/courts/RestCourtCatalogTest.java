package com.futbolix.schedules.adapter.out.courts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.futbolix.schedules.adapter.in.web.CorrelationIdFilter;
import com.futbolix.schedules.application.CourtCatalogUnavailableException;
import com.futbolix.schedules.domain.CourtInfo;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RestCourtCatalogTest {

    static final UUID COURT_1 = UUID.fromString("c0000000-0000-4000-8000-000000000001");
    static final String URL = "http://courts.test/api/v1/courts/" + COURT_1;

    MockRestServiceServer courtsApi;
    RestCourtCatalog catalog;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://courts.test");
        courtsApi = MockRestServiceServer.bindTo(builder).build();
        catalog = new RestCourtCatalog(builder.build());
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void readsStatusAndPriceAndIgnoresTheRestOfTheCourt() {
        MDC.put(CorrelationIdFilter.MDC_KEY, "trace-42");
        courtsApi.expect(requestTo(URL))
                .andExpect(header(CorrelationIdFilter.HEADER, "trace-42"))
                .andRespond(withSuccess("""
                        {"id": "c0000000-0000-4000-8000-000000000001", "name": "Cancha 1", "sport": "FOOTBALL",
                         "capacity": 10, "hourlyPrice": {"amount": 60000, "currency": "COP"}, "status": "ACTIVE",
                         "createdAt": "2026-11-10T12:00:00Z", "updatedAt": "2026-11-10T12:00:00Z"}""",
                        MediaType.APPLICATION_JSON));

        assertThat(catalog.find(COURT_1)).contains(new CourtInfo(COURT_1, "ACTIVE", 60000, "COP"));
        courtsApi.verify();
    }

    @Test
    void anUnknownCourtIsEmpty() {
        courtsApi.expect(requestTo(URL)).andRespond(withResourceNotFound());
        assertThat(catalog.find(COURT_1)).isEmpty();
    }

    @Test
    void aFailingCourtsServiceIsUnavailableNotEmpty() {
        courtsApi.expect(requestTo(URL)).andRespond(withServerError());
        assertThatThrownBy(() -> catalog.find(COURT_1)).isInstanceOf(CourtCatalogUnavailableException.class);
    }
}
