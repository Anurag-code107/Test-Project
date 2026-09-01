package com.tenxengage.app.service;

import com.tenxengage.app.dto.request.CreateWhistleblowerReportRequest;
import com.tenxengage.app.entity.WhistleblowerCaseUpdate;
import com.tenxengage.app.entity.WhistleblowerReport;
import com.tenxengage.app.entity.enums.WhistleblowerReportType;
import com.tenxengage.app.entity.enums.WhistleblowerStatus;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.repository.WhistleblowerCaseUpdateRepository;
import com.tenxengage.app.repository.WhistleblowerReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class WhistleblowerService {

    private static final Logger log = LoggerFactory.getLogger(WhistleblowerService.class);
    private static final int TRACKING_NUMBER_LENGTH = 8;
    private static final String TRACKING_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int RESOLUTION_DEADLINE_MONTHS = 3;

    private final WhistleblowerReportRepository reportRepository;
    private final WhistleblowerCaseUpdateRepository caseUpdateRepository;
    private final SecureRandom secureRandom;

    public WhistleblowerService(WhistleblowerReportRepository reportRepository,
                                 WhistleblowerCaseUpdateRepository caseUpdateRepository) {
        this.reportRepository = reportRepository;
        this.caseUpdateRepository = caseUpdateRepository;
        this.secureRandom = new SecureRandom();
    }

    @Transactional
    public WhistleblowerReport submitReport(CreateWhistleblowerReportRequest request) {
        WhistleblowerReportType reportType;
        try {
            reportType = WhistleblowerReportType.valueOf(request.reportType());
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("Invalid report type: " + request.reportType()
                    + ". Valid types: SUSPICIOUS_INCENTIVE, POTENTIAL_KICKBACK, POLICY_VIOLATION, "
                    + "DATA_PRIVACY_CONCERN, OTHER");
        }

        String trackingNumber = generateUniqueTrackingNumber();

        WhistleblowerReport report = WhistleblowerReport.builder()
                .clientId(request.clientId())
                .reportType(reportType)
                .description(request.description())
                .evidenceUrl(request.evidenceUrl())
                .reporterEmail(request.anonymous() ? null : request.reporterEmail())
                .reporterName(request.anonymous() ? null : request.reporterName())
                .anonymous(request.anonymous())
                .trackingNumber(trackingNumber)
                .status(WhistleblowerStatus.NEW)
                .build();

        WhistleblowerReport saved = reportRepository.save(report);
        log.info("Whistleblower report submitted: trackingNumber={}, type={}, anonymous={}",
                trackingNumber, reportType, request.anonymous());
        return saved;
    }

    @Transactional
    public WhistleblowerReport acknowledgeReport(UUID reportId) {
        WhistleblowerReport report = findReportOrThrow(reportId);

        if (report.getStatus() != WhistleblowerStatus.NEW) {
            throw new BusinessRuleException(
                    "Report can only be acknowledged when in NEW status. Current status: "
                            + report.getStatus());
        }

        report.setStatus(WhistleblowerStatus.ACKNOWLEDGED);
        report.setAcknowledgedAt(Instant.now());
        report.setResolutionDeadline(
                Instant.now().plus(RESOLUTION_DEADLINE_MONTHS * 30L, ChronoUnit.DAYS));
        report.setUpdatedAt(Instant.now());

        WhistleblowerReport saved = reportRepository.save(report);
        log.info("Whistleblower report acknowledged: id={}, deadline={}",
                reportId, saved.getResolutionDeadline());
        return saved;
    }

    @Transactional
    public WhistleblowerCaseUpdate addCaseUpdate(UUID reportId, String updateText,
                                                  UUID updatedByUserId) {
        WhistleblowerReport report = findReportOrThrow(reportId);

        if (report.getStatus() == WhistleblowerStatus.RESOLVED
                || report.getStatus() == WhistleblowerStatus.DISMISSED) {
            throw new BusinessRuleException(
                    "Cannot add updates to a resolved or dismissed report");
        }

        if (report.getStatus() == WhistleblowerStatus.ACKNOWLEDGED) {
            report.setStatus(WhistleblowerStatus.UNDER_INVESTIGATION);
            report.setUpdatedAt(Instant.now());
            reportRepository.save(report);
        }

        WhistleblowerCaseUpdate update = WhistleblowerCaseUpdate.builder()
                .reportId(reportId)
                .updateText(updateText)
                .updatedBy(updatedByUserId)
                .build();

        WhistleblowerCaseUpdate saved = caseUpdateRepository.save(update);
        log.info("Case update added: reportId={}, updatedBy={}", reportId, updatedByUserId);
        return saved;
    }

    @Transactional
    public WhistleblowerReport resolveReport(UUID reportId, UUID resolvedByUserId, String notes) {
        WhistleblowerReport report = findReportOrThrow(reportId);

        if (report.getStatus() == WhistleblowerStatus.RESOLVED
                || report.getStatus() == WhistleblowerStatus.DISMISSED) {
            throw new BusinessRuleException("Report is already resolved or dismissed");
        }

        report.setStatus(WhistleblowerStatus.RESOLVED);
        report.setResolvedAt(Instant.now());
        report.setResolvedBy(resolvedByUserId);
        report.setResolutionNotes(notes);
        report.setUpdatedAt(Instant.now());

        WhistleblowerReport saved = reportRepository.save(report);
        log.info("Whistleblower report resolved: id={}, resolvedBy={}", reportId, resolvedByUserId);
        return saved;
    }

    @Transactional(readOnly = true)
    public WhistleblowerReport getReportByTrackingNumber(String trackingNumber) {
        return reportRepository.findByTrackingNumber(trackingNumber)
                .orElseThrow(() -> new BusinessRuleException(
                        "No report found for tracking number: " + trackingNumber));
    }

    @Transactional(readOnly = true)
    public List<WhistleblowerCaseUpdate> getCaseUpdates(UUID reportId) {
        return caseUpdateRepository.findByReportIdOrderByCreatedAtDesc(reportId);
    }

    @Transactional(readOnly = true)
    public List<WhistleblowerReport> getActiveReports() {
        return reportRepository.findByStatusIn(List.of(
                WhistleblowerStatus.NEW,
                WhistleblowerStatus.ACKNOWLEDGED,
                WhistleblowerStatus.UNDER_INVESTIGATION));
    }

    @Transactional(readOnly = true)
    public List<WhistleblowerReport> getReportsByClient(UUID clientId) {
        return reportRepository.findByClientId(clientId);
    }

    private WhistleblowerReport findReportOrThrow(UUID reportId) {
        return reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessRuleException(
                        "Whistleblower report not found: " + reportId));
    }

    private String generateUniqueTrackingNumber() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String candidate = generateTrackingNumber();
            if (reportRepository.findByTrackingNumber(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new BusinessRuleException(
                "Failed to generate a unique tracking number after 10 attempts");
    }

    private String generateTrackingNumber() {
        StringBuilder sb = new StringBuilder(TRACKING_NUMBER_LENGTH);
        for (int i = 0; i < TRACKING_NUMBER_LENGTH; i++) {
            sb.append(TRACKING_CHARS.charAt(secureRandom.nextInt(TRACKING_CHARS.length())));
        }
        return sb.toString();
    }
}
