package com.futbolix.schedules.application;

/** ftx-courts-api did not answer in time or failed (503 SERVICE_UNAVAILABLE). */
public class CourtCatalogUnavailableException extends RuntimeException {
    public CourtCatalogUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
