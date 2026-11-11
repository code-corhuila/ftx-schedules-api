package com.futbolix.schedules.adapter.out.courts;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.futbolix.schedules.adapter.in.web.CorrelationIdFilter;
import com.futbolix.schedules.application.CourtCatalogUnavailableException;
import com.futbolix.schedules.application.port.out.CourtCatalog;
import com.futbolix.schedules.domain.CourtInfo;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Reads a court from ftx-courts-api (GET /api/v1/courts/{id}) inside the platform network.
 * The schedules domain never reads the courts schema: the database forbids it, and the court's
 * status and price belong to the courts domain. The request carries the caller's X-Correlation-Id.
 */
public class RestCourtCatalog implements CourtCatalog {

    private final RestClient courtsApi;

    public RestCourtCatalog(RestClient courtsApi) {
        this.courtsApi = courtsApi;
    }

    @Override
    public Optional<CourtInfo> find(UUID courtId) {
        try {
            return courtsApi.get()
                    .uri("/api/v1/courts/{id}", courtId)
                    .headers(headers -> {
                        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
                        if (correlationId != null) {
                            headers.set(CorrelationIdFilter.HEADER, correlationId);
                        }
                    })
                    .exchange((request, response) -> {
                        if (response.getStatusCode().value() == 404) {
                            return Optional.<CourtInfo>empty();
                        }
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new CourtCatalogUnavailableException("courts api answered " + response.getStatusCode(), null);
                        }
                        CourtView court = response.bodyTo(CourtView.class);
                        if (court == null || court.hourlyPrice() == null) {
                            throw new CourtCatalogUnavailableException("courts api answered an empty court", null);
                        }
                        return Optional.of(new CourtInfo(court.id(), court.status(),
                                court.hourlyPrice().amount(), court.hourlyPrice().currency()));
                    });
        } catch (ResourceAccessException e) {
            throw new CourtCatalogUnavailableException("courts api unreachable", e);
        }
    }

    /** Only the fields this domain uses; the rest of the Court schema is ignored (tolerant reader). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record CourtView(UUID id, String status, Price hourlyPrice) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Price(long amount, String currency) {
        }
    }
}
