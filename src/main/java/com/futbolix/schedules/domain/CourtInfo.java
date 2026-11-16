package com.futbolix.schedules.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * What this domain needs to know about a court of the courts domain: its status and current
 * price. It is read from ftx-courts-api, never from the courts schema.
 */
public record CourtInfo(UUID courtId, String status, long hourlyPriceAmount, String currency) {

    public static final String ACTIVE = "ACTIVE";

    public CourtInfo {
        Objects.requireNonNull(courtId, "courtId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(currency, "currency");
    }

    public boolean isBookable() {
        return ACTIVE.equals(status);
    }
}
