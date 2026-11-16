package com.futbolix.schedules.adapter.in.web;

import com.futbolix.schedules.application.port.in.GetBookableSlots;
import com.futbolix.schedules.application.port.in.GetWeeklySchedule;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public reads of court-service.yaml owned by the schedules domain (FR-002, FR-003). */
@RestController
@RequestMapping("/api/v1/courts/{id}")
public class ScheduleController {

    private final GetWeeklySchedule weeklySchedule;
    private final GetBookableSlots bookableSlots;

    public ScheduleController(GetWeeklySchedule weeklySchedule, GetBookableSlots bookableSlots) {
        this.weeklySchedule = weeklySchedule;
        this.bookableSlots = bookableSlots;
    }

    @GetMapping("/schedules")
    public Map<String, List<ScheduleResponse>> schedules(@PathVariable UUID id) {
        return Map.of("data", weeklySchedule.weeklySchedule(id).stream().map(ScheduleResponse::from).toList());
    }

    @GetMapping("/bookable-slots")
    public BookableSlotsResponse bookableSlots(@PathVariable UUID id,
                                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return BookableSlotsResponse.from(bookableSlots.bookableSlots(id, date));
    }
}
