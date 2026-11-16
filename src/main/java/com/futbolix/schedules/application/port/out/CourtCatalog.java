package com.futbolix.schedules.application.port.out;

import com.futbolix.schedules.domain.CourtInfo;
import java.util.Optional;
import java.util.UUID;

/**
 * The courts domain, as seen from here. Empty when the court does not exist; throws
 * CourtCatalogUnavailableException when the courts service cannot answer.
 */
public interface CourtCatalog {
    Optional<CourtInfo> find(UUID courtId);
}
