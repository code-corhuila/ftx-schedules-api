package com.futbolix.schedules.adapter.in.web;

import com.futbolix.schedules.application.port.in.SaveSchedule;
import com.futbolix.schedules.domain.DayCode;
import java.time.LocalTime;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** PUT and DELETE /admin/courts/{id}/schedules/{dayOfWeek} of court-service.yaml (FR-029). */
@RestController
@RequestMapping("/api/v1/admin/courts/{id}/schedules/{dayOfWeek}")
public class AdminScheduleController {

    private final SaveSchedule saveSchedule;

    public AdminScheduleController(SaveSchedule saveSchedule) {
        this.saveSchedule = saveSchedule;
    }

    @PutMapping
    public ScheduleResponse put(@PathVariable UUID id, @PathVariable DayCode dayOfWeek, @RequestBody ScheduleRequest body,
                                @RequestAttribute(AdministratorInterceptor.ADMINISTRATOR_ID_ATTRIBUTE) String administratorId) {
        LocalTime opening = Times.parse(body.openingTime());
        if (opening == null) {
            throw new InvalidTimeFormatException("openingTime");
        }
        LocalTime closing = Times.parse(body.closingTime());
        if (closing == null) {
            throw new InvalidTimeFormatException("closingTime");
        }
        return ScheduleResponse.from(saveSchedule.putSchedule(id, dayOfWeek, opening, closing, administratorId));
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(@PathVariable UUID id, @PathVariable DayCode dayOfWeek,
                                       @RequestAttribute(AdministratorInterceptor.ADMINISTRATOR_ID_ATTRIBUTE) String administratorId) {
        saveSchedule.deleteSchedule(id, dayOfWeek, administratorId);
        return ResponseEntity.noContent().build();
    }
}
