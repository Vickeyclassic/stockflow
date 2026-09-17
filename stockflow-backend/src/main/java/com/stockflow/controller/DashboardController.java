package com.stockflow.controller;

import com.stockflow.dto.DashboardResponse.*;
import com.stockflow.service.DashboardService;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;

@RestController
@RequestMapping("/api")
public class DashboardController {
    private final DashboardService service;
    public DashboardController(DashboardService service) { this.service = service; }
    @GetMapping("/dashboard")
    public Metrics dashboard() { return service.metrics(); }
    @GetMapping("/reports")
    public Report reports(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return service.report(dateFrom, dateTo);
    }
}
