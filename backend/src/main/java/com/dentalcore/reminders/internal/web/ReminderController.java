package com.dentalcore.reminders.internal.web;

import com.dentalcore.reminders.internal.service.ReminderService;
import com.dentalcore.reminders.internal.service.ReminderService.RecallWorklistRow;
import com.dentalcore.reminders.internal.service.ReminderService.RunSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reminders")
@Tag(name = "Reminders", description = "Appointment and recall reminders")
public class ReminderController {

    private final ReminderService service;

    public ReminderController(ReminderService service) {
        this.service = service;
    }

    @PostMapping("/run")
    @PreAuthorize("hasAuthority('REMINDERS_RUN')")
    @Operation(summary = "Run reminders now (also runs automatically each morning)")
    public RunSummary run() {
        return service.runAll();
    }

    @GetMapping("/recall-worklist")
    @Operation(summary = "Patients due or overdue for recall, with contact info")
    public List<RecallWorklistRow> recallWorklist(
            @RequestParam(defaultValue = "14") int daysAhead) {
        return service.recallWorklist(daysAhead);
    }
}
