package com.futbolix.schedules.adapter.in.web;

import com.futbolix.schedules.domain.BookableSlots;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** BookableSlots schema of court-service.yaml. */
public record BookableSlotsResponse(UUID courtId, LocalDate date, String courtStatus, MoneyDto hourlyPrice,
                                    List<SlotDto> slots) {

    public record SlotDto(String startTime, String endTime) {
    }

    static BookableSlotsResponse from(BookableSlots bookable) {
        return new BookableSlotsResponse(bookable.court().courtId(), bookable.date(), bookable.court().status(),
                new MoneyDto(bookable.court().hourlyPriceAmount(), bookable.court().currency()),
                bookable.slots().stream()
                        .map(s -> new SlotDto(Times.format(s.startTime()), Times.format(s.endTime())))
                        .toList());
    }
}
