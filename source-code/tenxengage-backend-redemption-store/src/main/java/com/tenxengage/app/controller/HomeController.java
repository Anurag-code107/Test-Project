package com.tenxengage.app.controller;

import com.tenxengage.app.dto.response.IncentivePerformanceResponse;
import com.tenxengage.app.dto.response.ParticipationMetricsResponse;
import com.tenxengage.app.dto.response.ProgramPerformanceResponse;
import com.tenxengage.app.entity.enums.HomeDateFilter;
import com.tenxengage.app.entity.enums.HomeIncentiveTypeFilter;
import com.tenxengage.app.security.RequiresPermission;
import com.tenxengage.app.service.HomeMetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/home")
@Tag(name = "Home", description = "Client Admin home page dashboard metrics")
public class HomeController {

    private final HomeMetricsService homeMetricsService;

    public HomeController(HomeMetricsService homeMetricsService) {
        this.homeMetricsService = homeMetricsService;
    }

    @GetMapping("/participation")
    @RequiresPermission("action.home.view_participation")
    @Operation(summary = "Participation performance metrics",
        description = "Returns enrollment and reward participation metrics. Pass partnerCompanyId for partner-specific view.")
    public ResponseEntity<ParticipationMetricsResponse> getParticipationMetrics(
            @RequestParam(defaultValue = "LAST_30_DAYS") HomeDateFilter dateFilter,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "GLOBAL") String region,
            @RequestParam(required = false) UUID partnerCompanyId) {
        return ResponseEntity.ok(homeMetricsService.getParticipationMetrics(
            dateFilter, startDate, endDate, region, partnerCompanyId));
    }

    @GetMapping("/incentive-performance")
    @RequiresPermission("action.home.view_performance")
    @Operation(summary = "Incentive performance metrics",
        description = "Returns rewards earned, budget utilization, and user participation with optional incentive type filter.")
    public ResponseEntity<IncentivePerformanceResponse> getIncentivePerformance(
            @RequestParam(defaultValue = "LAST_30_DAYS") HomeDateFilter dateFilter,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "GLOBAL") String region,
            @RequestParam(required = false) UUID partnerCompanyId,
            @RequestParam(defaultValue = "ALL") HomeIncentiveTypeFilter incentiveType) {
        return ResponseEntity.ok(homeMetricsService.getIncentivePerformance(
            dateFilter, startDate, endDate, region, partnerCompanyId, incentiveType));
    }

    @GetMapping("/program-performance")
    @RequiresPermission("action.home.view_performance")
    @Operation(summary = "Program performance metrics for current fiscal quarter",
        description = "Returns combined incentive and participation metrics for the current fiscal quarter with quarterly trend data.")
    public ResponseEntity<ProgramPerformanceResponse> getProgramPerformance(
            @RequestParam(defaultValue = "GLOBAL") String region,
            @RequestParam(required = false) UUID partnerCompanyId) {
        return ResponseEntity.ok(homeMetricsService.getProgramPerformance(region, partnerCompanyId));
    }
}
