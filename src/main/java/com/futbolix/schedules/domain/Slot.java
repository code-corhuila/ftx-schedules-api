package com.futbolix.schedules.domain;

import java.time.LocalTime;

/** A one-hour slot (court-service.yaml, schema Slot). */
public record Slot(LocalTime startTime, LocalTime endTime) {
}
