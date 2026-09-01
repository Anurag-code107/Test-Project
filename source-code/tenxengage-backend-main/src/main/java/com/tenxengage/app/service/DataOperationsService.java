package com.tenxengage.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenxengage.app.dto.request.UpdateSyncScheduleRequest;
import com.tenxengage.app.dto.response.DataUploadResponse;
import com.tenxengage.app.dto.response.SyncScheduleResponse;
import com.tenxengage.app.dto.response.TaggingJobResponse;
import com.tenxengage.app.entity.DataObject;
import com.tenxengage.app.entity.DataObjectField;
import com.tenxengage.app.entity.DataUpload;
import com.tenxengage.app.entity.SyncSchedule;
import com.tenxengage.app.entity.TaggingJob;
import com.tenxengage.app.entity.enums.DataUploadSource;
import com.tenxengage.app.entity.enums.DataUploadStatus;
import com.tenxengage.app.entity.enums.SyncCadence;
import com.tenxengage.app.entity.enums.TaggingJobStatus;
import com.tenxengage.app.repository.DataObjectRepository;
import com.tenxengage.app.repository.DataUploadRepository;
import com.tenxengage.app.repository.SyncScheduleRepository;
import com.tenxengage.app.repository.TaggingJobRepository;
import com.tenxengage.app.entity.UserCourseCompletion;
import com.tenxengage.app.event.NotificationEvent;
import com.tenxengage.app.event.NotificationEventProducer;
import com.tenxengage.app.event.TrainingSyncEvent;
import com.tenxengage.app.event.TrainingSyncEventProducer;
import com.tenxengage.app.repository.UserCourseCompletionRepository;
import com.tenxengage.app.security.TenantValidator;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.jdbc.core.JdbcTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

@Service
public class DataOperationsService {

    private static final Logger log = LoggerFactory.getLogger(DataOperationsService.class);

    private static final Set<String> PARTNER_DATA_KNOWN_FIELDS = Set.of(
            "Partner Name", "Partner ID", "Region");

    private static final Set<String> PARTNER_USER_DATA_KNOWN_FIELDS = Set.of(
            "User ID", "Partner ID", "First Name", "Last Name", "Email");

    private static final Set<String> TRAINING_COMPLETION_KNOWN_FIELDS = Set.of(
            "User ID", "Course ID", "Completion Date");

    private static final Set<String> SALES_DATA_KNOWN_FIELDS = Set.of(
            "Transaction ID", "PO#", "Booking Date", "Partner ID",
            "Product SKU", "Quantity", "Net Bookings");

    private final DataUploadRepository uploadRepository;
    private final TaggingJobRepository taggingJobRepository;
    private final SyncScheduleRepository syncScheduleRepository;
    private final DataObjectRepository dataObjectRepository;
    private final TenantValidator tenantValidator;
    private final ProductService productService;
    private final TaggingEngineService taggingEngine;
    private final JdbcTemplate jdbc;
    private final NotificationEventProducer notificationEventProducer;
    private final TrainingSyncEventProducer trainingSyncEventProducer;
    private final UserCourseCompletionRepository userCourseCompletionRepository;
    private final ObjectMapper objectMapper;

    public DataOperationsService(DataUploadRepository uploadRepository,
                                 TaggingJobRepository taggingJobRepository,
                                 SyncScheduleRepository syncScheduleRepository,
                                 DataObjectRepository dataObjectRepository,
                                 TenantValidator tenantValidator,
                                 ProductService productService,
                                 TaggingEngineService taggingEngine,
                                 JdbcTemplate jdbc,
                                 NotificationEventProducer notificationEventProducer,
                                 TrainingSyncEventProducer trainingSyncEventProducer,
                                 UserCourseCompletionRepository userCourseCompletionRepository,
                                 ObjectMapper objectMapper) {
        this.uploadRepository = uploadRepository;
        this.taggingJobRepository = taggingJobRepository;
        this.syncScheduleRepository = syncScheduleRepository;
        this.dataObjectRepository = dataObjectRepository;
        this.tenantValidator = tenantValidator;
        this.productService = productService;
        this.taggingEngine = taggingEngine;
        this.jdbc = jdbc;
        this.notificationEventProducer = notificationEventProducer;
        this.trainingSyncEventProducer = trainingSyncEventProducer;
        this.userCourseCompletionRepository = userCourseCompletionRepository;
        this.objectMapper = objectMapper;
    }

    // --- Upload History ---

    @Transactional(readOnly = true)
    public List<DataUploadResponse> getUploadHistory(UUID dataObjectId) {
        UUID clientId = tenantValidator.getCurrentClientId();
        validateDataObject(dataObjectId, clientId);
        return uploadRepository.findByClientIdAndDataObjectIdOrderByCreatedAtDesc(clientId, dataObjectId)
                .stream()
                .map(DataUploadResponse::from)
                .toList();
    }

    // --- Manual File Upload ---

    private static final Set<String> ALLOWED_CSV_CONTENT_TYPES = Set.of(
            "text/csv", "text/plain", "application/csv",
            "application/vnd.ms-excel", "application/octet-stream"
    );

    @Transactional
    public DataUploadResponse uploadFile(UUID dataObjectId, MultipartFile file) {
        UUID clientId = tenantValidator.getCurrentClientId();
        DataObject dataObject = validateDataObject(dataObjectId, clientId);

        // Validate file type
        String fileName = file.getOriginalFilename() != null
                ? file.getOriginalFilename() : "upload.csv";
        if (!fileName.toLowerCase().endsWith(".csv")) {
            throw new IllegalArgumentException("Only CSV files are supported. Received: " + fileName);
        }
        String contentType = file.getContentType();
        if (contentType != null && !ALLOWED_CSV_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("Invalid file content type: " + contentType);
        }
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }

        List<Map<String, String>> rows = parseCsvFile(file, dataObject);

        int newRows = 0;
        int updatedRows = 0;
        int skippedRows = 0;
        Set<UUID> affectedPoIds = new HashSet<>();

        for (Map<String, String> row : rows) {
            ProcessResult result = processTransactionRow(clientId, row);
            switch (result.type) {
                case NEW -> newRows++;
                case UPDATED -> updatedRows++;
                case SKIPPED -> skippedRows++;
            }
            if (result.purchaseOrderId != null) {
                affectedPoIds.add(result.purchaseOrderId);
            }
        }

        for (UUID poId : affectedPoIds) {
            recomputePoTotal(poId);
        }

        DataUpload upload = DataUpload.builder()
                .clientId(clientId)
                .dataObject(dataObject)
                .fileName(fileName)
                .source(DataUploadSource.MANUAL)
                .status(DataUploadStatus.COMPLETED)
                .totalRows(rows.size())
                .newRows(newRows)
                .updatedRows(updatedRows)
                .skippedRows(skippedRows)
                .build();
        upload = uploadRepository.save(upload);

        log.info("File upload completed for data object {}: {} total, {} new, "
                        + "{} updated, {} skipped",
                dataObject.getName(), rows.size(), newRows, updatedRows, skippedRows);

        notificationEventProducer.publish(new NotificationEvent(
            "DATA_UPLOAD_COMPLETED", clientId,
            "Data Upload Completed: " + fileName,
            "Upload completed: " + newRows + " new, " + updatedRows + " updated, " + skippedRows + " skipped.",
            "DATA", upload.getId(), tenantValidator.getCurrentUserId(), null, null));

        if ("Sales Data".equals(dataObject.getName())) {
            triggerTaggingJobAsync(clientId);
        }

        if ("Training User Completion Data".equals(dataObject.getName())) {
            processTrainingCompletionRows(clientId, rows, upload.getId());
        }

        if ("Partner Data".equals(dataObject.getName())) {
            processPartnerDataRows(clientId, rows, dataObject);
        }

        if ("Partner User Data".equals(dataObject.getName())) {
            processPartnerUserDataRows(clientId, rows, dataObject);
        }

        return DataUploadResponse.from(upload);
    }

    /**
     * Processes uploaded User Training Completion CSV rows:
     * 1. Resolves user and course from CSV fields
     * 2. Upserts into user_course_completions table
     * 3. Publishes TrainingSyncEvent to trigger completion evaluation
     */
    private void processTrainingCompletionRows(UUID clientId, List<Map<String, String>> rows, UUID uploadId) {
        Set<UUID> affectedUserIds = new HashSet<>();

        for (Map<String, String> row : rows) {
            String externalUserId = row.get("User ID");
            String courseIdStr = row.get("Course ID");
            String completionDateStr = row.get("Completion Date");

            if (externalUserId == null || courseIdStr == null || completionDateStr == null) {
                log.debug("Skipping training completion row with missing required fields");
                continue;
            }

            // Resolve user by external ID — look up via partner user data
            UUID userId = resolveUserIdByExternalId(clientId, externalUserId.trim());
            if (userId == null) {
                log.debug("Could not resolve user for external ID: {}", externalUserId);
                continue;
            }

            // Resolve course by external ID
            UUID courseId = resolveCourseId(courseIdStr.trim());
            if (courseId == null) {
                log.debug("Could not resolve course for ID: {}", courseIdStr);
                continue;
            }

            // Parse completion date
            Instant completedAt;
            try {
                LocalDate date = LocalDate.parse(completionDateStr.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
                completedAt = date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
            } catch (DateTimeParseException e) {
                try {
                    completedAt = Instant.parse(completionDateStr.trim());
                } catch (DateTimeParseException e2) {
                    log.debug("Could not parse completion date: {}", completionDateStr);
                    continue;
                }
            }

            // Skip if already exists
            if (userCourseCompletionRepository.existsByClientIdAndUserIdAndCourseId(clientId, userId, courseId)) {
                continue;
            }

            String customMetadata = extractCustomMetadata(row, TRAINING_COMPLETION_KNOWN_FIELDS);

            UserCourseCompletion completion = UserCourseCompletion.builder()
                .clientId(clientId)
                .userId(userId)
                .courseId(courseId)
                .completedAt(completedAt)
                .source("MANUAL_UPLOAD")
                .metadata(customMetadata != null ? customMetadata : "{}")
                .build();
            userCourseCompletionRepository.save(completion);

            affectedUserIds.add(userId);
        }

        if (!affectedUserIds.isEmpty()) {
            log.info("Processed {} user training completions, publishing sync event", affectedUserIds.size());

            List<TrainingSyncEvent.TrainingCompletionRecord> records = affectedUserIds.stream()
                .map(uid -> new TrainingSyncEvent.TrainingCompletionRecord(uid.toString()))
                .toList();

            trainingSyncEventProducer.publish(new TrainingSyncEvent(clientId, uploadId, records));
        }
    }

    /**
     * Resolves a partner user's internal UUID from an external user ID string.
     * Tries UUID parse first, then looks up by employee ID in partner user data.
     */
    private UUID resolveUserIdByExternalId(UUID clientId, String externalId) {
        try {
            UUID directId = UUID.fromString(externalId);
            List<Map<String, Object>> result = jdbc.queryForList(
                "SELECT id FROM users WHERE client_id = ? AND id = ? LIMIT 1",
                clientId, directId);
            if (!result.isEmpty()) return directId;
        } catch (IllegalArgumentException e) {
            // Not a UUID — fall through to external_user_id lookup
        }
        List<Map<String, Object>> result = jdbc.queryForList(
            "SELECT id FROM users WHERE client_id = ? AND external_user_id = ? LIMIT 1",
            clientId, externalId);
        return result.isEmpty() ? null : (UUID) result.get(0).get("id");
    }

    /**
     * Resolves an LMS course UUID from a course ID string.
     */
    private UUID resolveCourseId(String courseIdStr) {
        try {
            UUID directId = UUID.fromString(courseIdStr);
            List<Map<String, Object>> result = jdbc.queryForList(
                "SELECT id FROM lms_courses WHERE id = ? LIMIT 1", directId);
            if (!result.isEmpty()) return directId;
        } catch (IllegalArgumentException e) {
            // Not a UUID — fall through to external_course_id lookup
        }
        List<Map<String, Object>> result = jdbc.queryForList(
            "SELECT id FROM lms_courses WHERE external_course_id = ? LIMIT 1", courseIdStr);
        if (!result.isEmpty()) return (UUID) result.get(0).get("id");
        result = jdbc.queryForList(
            "SELECT id FROM lms_courses WHERE name = ? LIMIT 1", courseIdStr);
        return result.isEmpty() ? null : (UUID) result.get(0).get("id");
    }

    // --- Connector Pull ---

    @Transactional
    public DataUploadResponse triggerConnectorPull(UUID dataObjectId) {
        UUID clientId = tenantValidator.getCurrentClientId();
        DataObject dataObject = validateDataObject(dataObjectId, clientId);

        if (dataObject.getConnectorFieldMappings() == null || dataObject.getConnectorFieldMappings().isEmpty()) {
            throw new IllegalStateException("No connector mapping configured for data object: " + dataObject.getName());
        }

        String connectorName = dataObject.getConnectorFieldMappings().get(0).getConnector().getName();

        // Simulate connector pull results
        Random random = new Random();
        int newRows = random.nextInt(200) + 20;
        int updatedRows = random.nextInt(50) + 5;
        int totalRows = newRows + updatedRows;

        DataUpload upload = DataUpload.builder()
                .clientId(clientId)
                .dataObject(dataObject)
                .fileName(connectorName + " sync")
                .source(DataUploadSource.CONNECTOR)
                .status(DataUploadStatus.COMPLETED)
                .totalRows(totalRows)
                .newRows(newRows)
                .updatedRows(updatedRows)
                .skippedRows(0)
                .build();

        upload = uploadRepository.save(upload);
        log.info("Connector pull completed from {} for {}: {} new, {} updated",
                connectorName, dataObject.getName(), newRows, updatedRows);

        return DataUploadResponse.from(upload);
    }

    // --- Tagging Job ---

    @Transactional(readOnly = true)
    public List<TaggingJobResponse> getTaggingHistory() {
        UUID clientId = tenantValidator.getCurrentClientId();
        return taggingJobRepository.findByClientIdOrderByCreatedAtDesc(clientId)
                .stream()
                .map(TaggingJobResponse::from)
                .toList();
    }

    @Transactional
    public TaggingJobResponse triggerTaggingJob() {
        UUID clientId = tenantValidator.getCurrentClientId();

        // Product discovery step — find new products from sales data
        int productsDiscovered = productService.discoverProductsFromSalesData(clientId);

        // Create tagging job via JDBC so it shares the same connection as the tagging engine.
        // Using JPA here caused FK violations because JPA and JdbcTemplate can use different
        // connections within the same @Transactional scope (especially with Spring Batch present).
        UUID jobId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tagging_jobs (id, client_id, status, products_discovered, " +
                        "pos_analyzed, eligible_deals, incentives_matched, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, 0, 0, 0, ?, ?)",
                jobId, clientId, TaggingJobStatus.RUNNING.name(), productsDiscovered, now, now);

        TaggingJobStatus finalStatus;
        int posAnalyzed = 0;
        int eligibleDeals = 0;
        int incentivesMatched = 0;
        String errorMessage = null;

        try {
            // Run real tagging engine — evaluates POs against active incentive eligibility rules
            TaggingEngineService.TaggingResult result = taggingEngine.runTagging(clientId, jobId);
            finalStatus = TaggingJobStatus.COMPLETED;
            posAnalyzed = result.posAnalyzed();
            eligibleDeals = result.eligibleDeals();
            incentivesMatched = result.incentivesMatched();
        } catch (Exception e) {
            log.error("Tagging job failed for client {}", clientId, e);
            finalStatus = TaggingJobStatus.FAILED;
            errorMessage = e.getMessage();
        }

        // Update the job with results via JDBC
        jdbc.update("UPDATE tagging_jobs SET status = ?, pos_analyzed = ?, eligible_deals = ?, " +
                        "incentives_matched = ?, error_message = ?, updated_at = ? WHERE id = ?",
                finalStatus.name(), posAnalyzed, eligibleDeals, incentivesMatched,
                errorMessage, Timestamp.from(Instant.now()), jobId);

        // Eligibility mappings are now written directly during tagging;
        // no separate claim-building step is needed.

        log.info("Tagging job {}: {} POs analyzed, {} eligible deals, {} incentives matched, {} products discovered",
                finalStatus, posAnalyzed, eligibleDeals, incentivesMatched, productsDiscovered);

        if (finalStatus == TaggingJobStatus.COMPLETED) {
            notificationEventProducer.publish(new NotificationEvent(
                "TAGGING_COMPLETED", clientId,
                "Deal Tagging Completed",
                "Tagging completed: " + posAnalyzed + " POs analyzed, " + eligibleDeals + " eligible deals found.",
                "DATA", jobId, tenantValidator.getCurrentUserId(), null, null));

            if (eligibleDeals > 0) {
                notificationEventProducer.publish(new NotificationEvent(
                    "NEW_ELIGIBLE_DEALS", clientId,
                    "New Eligible Deals Available",
                    eligibleDeals + " new eligible deals are available for claiming.",
                    "DATA", jobId, null, null, null));
            }
        } else {
            notificationEventProducer.publish(new NotificationEvent(
                "TAGGING_FAILED", clientId,
                "Deal Tagging Failed",
                "Tagging job failed: " + (errorMessage != null ? errorMessage : "Unknown error"),
                "DATA", jobId, tenantValidator.getCurrentUserId(), null, null));
        }

        // Fetch via JPA for the response mapping
        TaggingJob job = taggingJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("Tagging job not found after creation: " + jobId));
        return TaggingJobResponse.from(job);
    }

    // --- Sync Schedule ---

    @Transactional(readOnly = true)
    public SyncScheduleResponse getSyncSchedule(UUID dataObjectId) {
        UUID clientId = tenantValidator.getCurrentClientId();
        validateDataObject(dataObjectId, clientId);
        return syncScheduleRepository.findByClientIdAndDataObjectId(clientId, dataObjectId)
                .map(SyncScheduleResponse::from)
                .orElse(new SyncScheduleResponse(null, dataObjectId, false, SyncCadence.MANUAL, null, null));
    }

    @Transactional
    public SyncScheduleResponse updateSyncSchedule(UUID dataObjectId, UpdateSyncScheduleRequest request) {
        UUID clientId = tenantValidator.getCurrentClientId();
        DataObject dataObject = validateDataObject(dataObjectId, clientId);

        SyncSchedule schedule = syncScheduleRepository.findByClientIdAndDataObjectId(clientId, dataObjectId)
                .orElseGet(() -> SyncSchedule.builder()
                        .clientId(clientId)
                        .dataObject(dataObject)
                        .build());

        schedule.setEnabled(request.enabled());
        schedule.setCadence(request.cadence());

        if (request.enabled() && request.cadence() != SyncCadence.MANUAL) {
            schedule.setNextRunAt(calculateNextRun(request.cadence()));
        } else {
            schedule.setNextRunAt(null);
        }

        schedule = syncScheduleRepository.save(schedule);
        log.info("Sync schedule updated for {}: enabled={}, cadence={}",
                dataObject.getName(), request.enabled(), request.cadence());

        return SyncScheduleResponse.from(schedule);
    }

    // --- Template Download ---

    public String generateTemplate(UUID dataObjectId) {
        UUID clientId = tenantValidator.getCurrentClientId();
        DataObject dataObject = validateDataObject(dataObjectId, clientId);

        // Generate CSV header from field names
        return dataObject.getFields().stream()
                .map(f -> f.getName())
                .reduce((a, b) -> a + "," + b)
                .orElse("") + "\n";
    }

    // --- Helpers ---

    private DataObject validateDataObject(UUID dataObjectId, UUID clientId) {
        return dataObjectRepository.findByIdAndClientId(dataObjectId, clientId)
                .orElseThrow(() -> new EntityNotFoundException("Data object not found: " + dataObjectId));
    }

    private List<Map<String, String>> parseCsvFile(MultipartFile file, DataObject dataObject) {
        List<String> fieldNames = dataObject.getFields().stream()
                .map(DataObjectField::getName)
                .toList();

        List<Map<String, String>> result = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream()))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IllegalArgumentException("CSV file is empty");
            }
            List<String> headers = parseCsvLine(headerLine);
            Map<String, Integer> headerIndex = new LinkedHashMap<>();
            for (int i = 0; i < headers.size(); i++) {
                headerIndex.put(headers.get(i).trim(), i);
            }
            for (String fieldName : fieldNames) {
                if (!headerIndex.containsKey(fieldName)) {
                    log.warn("CSV is missing expected column: {}", fieldName);
                }
            }

            String line;
            int lineNum = 1;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                if (line.isBlank()) {
                    continue;
                }
                List<String> values = parseCsvLine(line);
                Map<String, String> row = new HashMap<>();
                for (String fieldName : fieldNames) {
                    Integer idx = headerIndex.get(fieldName);
                    if (idx != null && idx < values.size()) {
                        row.put(fieldName, values.get(idx).trim());
                    } else {
                        row.put(fieldName, "");
                    }
                }
                result.add(row);
            }
            log.info("Parsed {} data rows from CSV", result.size());
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to parse CSV file: " + e.getMessage(), e);
        }
        return result;
    }

    private List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    fields.add(current.toString());
                    current.setLength(0);
                } else {
                    current.append(c);
                }
            }
        }
        fields.add(current.toString());
        return fields;
    }

    private ProcessResult processTransactionRow(UUID clientId, Map<String, String> row) {
        String transactionId = row.getOrDefault("Transaction ID", "");
        String poNumber = row.getOrDefault("PO#", "");
        String bookingDateStr = row.getOrDefault("Booking Date", "");
        String partnerId = row.getOrDefault("Partner ID", "");
        String productSku = row.getOrDefault("Product SKU", "");
        int quantity = parseIntSafe(row.getOrDefault("Quantity", "0"));
        BigDecimal netBookings = parseBigDecimalSafe(
                row.getOrDefault("Net Bookings", "0"));
        String salesMetadata = extractCustomMetadata(row, SALES_DATA_KNOWN_FIELDS);

        if (transactionId.isEmpty() || poNumber.isEmpty()) {
            log.warn("Skipping row with missing Transaction ID or PO#");
            return new ProcessResult(ProcessResultType.SKIPPED, null);
        }

        // Look up partner company
        UUID partnerCompanyId = lookupPartnerCompanyId(clientId, partnerId);
        if (partnerCompanyId == null) {
            log.warn("Partner not found for client_id={}, external_partner_id={}; "
                    + "skipping transaction {}", clientId, partnerId, transactionId);
            return new ProcessResult(ProcessResultType.SKIPPED, null);
        }

        // Look up or create product
        UUID productId = lookupOrCreateProduct(clientId, productSku);

        // Parse booking date
        LocalDate orderDate = parseLocalDate(bookingDateStr);

        // Look up or create purchase order
        UUID poId = lookupOrCreatePurchaseOrder(clientId, partnerCompanyId, poNumber, orderDate);

        // Look up existing line by transaction_id
        List<Map<String, Object>> existingLines = jdbc.queryForList(
                "SELECT id, quantity, line_total FROM purchase_order_lines "
                        + "WHERE transaction_id = ?",
                transactionId);

        if (existingLines.isEmpty()) {
            BigDecimal unitPrice = quantity > 0
                    ? netBookings.divide(BigDecimal.valueOf(quantity), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            UUID lineId = UUID.randomUUID();
            Timestamp now = Timestamp.from(Instant.now());
            jdbc.update("INSERT INTO purchase_order_lines "
                            + "(id, purchase_order_id, product_id, quantity, unit_price, "
                            + "line_total, transaction_id, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    lineId, poId, productId, quantity, unitPrice,
                    netBookings, transactionId, now, now);
            jdbc.update("UPDATE purchase_orders SET needs_retagging = TRUE, "
                    + "updated_at = now() WHERE id = ?", poId);
            if (salesMetadata != null) {
                jdbc.update("UPDATE purchase_orders SET metadata = COALESCE(metadata, '{}'::jsonb) || "
                        + "?::jsonb, updated_at = now() WHERE id = ?", salesMetadata, poId);
            }
            return new ProcessResult(ProcessResultType.NEW, poId);
        }

        Map<String, Object> existing = existingLines.get(0);
        int existingQty = ((Number) existing.get("quantity")).intValue();
        BigDecimal existingTotal = (BigDecimal) existing.get("line_total");
        UUID lineId = (UUID) existing.get("id");

        if (existingQty != quantity
                || existingTotal.compareTo(netBookings) != 0) {
            BigDecimal unitPrice = quantity > 0
                    ? netBookings.divide(BigDecimal.valueOf(quantity), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            jdbc.update("UPDATE purchase_order_lines SET quantity = ?, unit_price = ?, "
                            + "line_total = ?, updated_at = now() WHERE id = ?",
                    quantity, unitPrice, netBookings, lineId);
            jdbc.update("UPDATE purchase_orders SET needs_retagging = TRUE, "
                    + "updated_at = now() WHERE id = ?", poId);
            if (salesMetadata != null) {
                jdbc.update("UPDATE purchase_orders SET metadata = COALESCE(metadata, '{}'::jsonb) || "
                        + "?::jsonb, updated_at = now() WHERE id = ?", salesMetadata, poId);
            }
            return new ProcessResult(ProcessResultType.UPDATED, poId);
        }

        // Even for skipped rows (no quantity/total change), persist any new custom metadata
        if (salesMetadata != null) {
            jdbc.update("UPDATE purchase_orders SET metadata = COALESCE(metadata, '{}'::jsonb) || "
                    + "?::jsonb, updated_at = now() WHERE id = ?", salesMetadata, poId);
        }
        return new ProcessResult(ProcessResultType.SKIPPED, poId);
    }

    /**
     * Processes Partner Data CSV rows: updates known PartnerCompany columns
     * and stores any extra CSV columns in the metadata JSONB field.
     */
    private void processPartnerDataRows(UUID clientId, List<Map<String, String>> rows,
                                        DataObject dataObject) {
        int updated = 0;
        int skipped = 0;

        for (Map<String, String> row : rows) {
            String externalPartnerId = row.getOrDefault("Partner ID", "").trim();
            if (externalPartnerId.isEmpty()) {
                log.debug("Skipping partner data row with missing Partner ID");
                skipped++;
                continue;
            }

            // Look up partner company by external_partner_id
            List<Map<String, Object>> results = jdbc.queryForList(
                    "SELECT id FROM partner_companies WHERE client_id = ? AND external_partner_id = ?",
                    clientId, externalPartnerId);
            if (results.isEmpty()) {
                log.debug("Partner company not found for external_partner_id={}", externalPartnerId);
                skipped++;
                continue;
            }
            UUID partnerCompanyId = (UUID) results.get(0).get("id");

            // Update known columns
            String partnerName = row.getOrDefault("Partner Name", "").trim();
            String region = row.getOrDefault("Region", "").trim();

            jdbc.update("UPDATE partner_companies SET "
                            + "name = COALESCE(NULLIF(?, ''), name), "
                            + "region = COALESCE(NULLIF(?, ''), region), "
                            + "updated_at = now() WHERE id = ?",
                    partnerName, region, partnerCompanyId);

            // Collect custom fields and merge into metadata
            String customMetadata = extractCustomMetadata(row, PARTNER_DATA_KNOWN_FIELDS);
            if (customMetadata != null) {
                jdbc.update("UPDATE partner_companies SET metadata = COALESCE(metadata, '{}'::jsonb) || "
                        + "?::jsonb, updated_at = now() WHERE id = ?",
                        customMetadata, partnerCompanyId);
            }
            updated++;
        }

        log.info("Partner Data upload processed: {} updated, {} skipped out of {} rows",
                updated, skipped, rows.size());
    }

    /**
     * Processes Partner User Data CSV rows: updates known User columns
     * and stores any extra CSV columns in the metadata JSONB field.
     */
    private void processPartnerUserDataRows(UUID clientId, List<Map<String, String>> rows,
                                            DataObject dataObject) {
        int updated = 0;
        int skipped = 0;

        for (Map<String, String> row : rows) {
            String email = row.getOrDefault("Email", "").trim();
            if (email.isEmpty()) {
                log.debug("Skipping partner user data row with missing Email");
                skipped++;
                continue;
            }

            // Look up user by email
            List<Map<String, Object>> userResults = jdbc.queryForList(
                    "SELECT id FROM users WHERE client_id = ? AND email = ?",
                    clientId, email);
            if (userResults.isEmpty()) {
                log.debug("User not found for email={}", email);
                skipped++;
                continue;
            }
            UUID userId = (UUID) userResults.get(0).get("id");

            // Resolve partner_company_id from external Partner ID
            String externalPartnerId = row.getOrDefault("Partner ID", "").trim();
            UUID partnerCompanyId = null;
            if (!externalPartnerId.isEmpty()) {
                partnerCompanyId = lookupPartnerCompanyId(clientId, externalPartnerId);
            }

            // Update known columns
            String firstName = row.getOrDefault("First Name", "").trim();
            String lastName = row.getOrDefault("Last Name", "").trim();
            String externalUserId = row.getOrDefault("User ID", "").trim();

            jdbc.update("UPDATE users SET "
                            + "first_name = COALESCE(NULLIF(?, ''), first_name), "
                            + "last_name = COALESCE(NULLIF(?, ''), last_name), "
                            + "partner_company_id = COALESCE(?, partner_company_id), "
                            + "external_user_id = COALESCE(NULLIF(?, ''), external_user_id), "
                            + "updated_at = now() WHERE id = ?",
                    firstName, lastName, partnerCompanyId, externalUserId, userId);

            // Collect custom fields and merge into metadata
            String customMetadata = extractCustomMetadata(row, PARTNER_USER_DATA_KNOWN_FIELDS);
            if (customMetadata != null) {
                jdbc.update("UPDATE users SET metadata = COALESCE(metadata, '{}'::jsonb) || "
                        + "?::jsonb, updated_at = now() WHERE id = ?",
                        customMetadata, userId);
            }
            updated++;
        }

        log.info("Partner User Data upload processed: {} updated, {} skipped out of {} rows",
                updated, skipped, rows.size());
    }

    /**
     * Extracts CSV columns that are not in the known field set, returning them
     * as a JSON string suitable for storing in a JSONB metadata column.
     * Returns null if there are no custom fields.
     */
    private String extractCustomMetadata(Map<String, String> row, Set<String> knownFields) {
        Map<String, String> custom = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : row.entrySet()) {
            if (!knownFields.contains(entry.getKey())
                    && entry.getValue() != null
                    && !entry.getValue().isBlank()) {
                custom.put(entry.getKey(), entry.getValue());
            }
        }
        if (custom.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(custom);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize custom metadata: {}", e.getMessage());
            return null;
        }
    }

    private UUID lookupPartnerCompanyId(UUID clientId, String externalPartnerId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id FROM partner_companies "
                        + "WHERE client_id = ? AND external_partner_id = ?",
                clientId, externalPartnerId);
        if (rows.isEmpty()) {
            return null;
        }
        return (UUID) rows.get(0).get("id");
    }

    private UUID lookupOrCreateProduct(UUID clientId, String sku) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id FROM products WHERE client_id = ? AND sku = ?",
                clientId, sku);
        if (!rows.isEmpty()) {
            return (UUID) rows.get(0).get("id");
        }
        UUID productId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO products (id, client_id, sku, name, category, "
                        + "created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                productId, clientId, sku, sku, "None", now, now);
        log.info("Created new product with sku={} for client={}", sku, clientId);
        return productId;
    }

    private UUID lookupOrCreatePurchaseOrder(UUID clientId, UUID partnerCompanyId,
                                             String orderNumber, LocalDate orderDate) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id FROM purchase_orders "
                        + "WHERE client_id = ? AND order_number = ?",
                clientId, orderNumber);
        if (!rows.isEmpty()) {
            return (UUID) rows.get(0).get("id");
        }
        UUID poId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO purchase_orders "
                        + "(id, client_id, partner_company_id, order_number, order_date, "
                        + "status, total_amount, needs_retagging, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                poId, clientId, partnerCompanyId, orderNumber, orderDate,
                "COMPLETED", BigDecimal.ZERO, true, now, now);
        return poId;
    }

    private void recomputePoTotal(UUID poId) {
        jdbc.update("UPDATE purchase_orders SET total_amount = ("
                + "SELECT COALESCE(SUM(line_total), 0) FROM purchase_order_lines "
                + "WHERE purchase_order_id = ?), updated_at = now() WHERE id = ?",
                poId, poId);
    }

    private void triggerTaggingJobAsync(UUID clientId) {
        try {
            log.info("Auto-triggering tagging job after Sales Data upload "
                    + "for client {}", clientId);
            triggerTaggingJob();
        } catch (Exception e) {
            log.error("Auto-triggered tagging job failed for client {}",
                    clientId, e);
        }
    }

    private static LocalDate parseLocalDate(String value) {
        if (value == null || value.isBlank()) {
            return LocalDate.now();
        }
        DateTimeFormatter[] formatters = {
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("M/d/yyyy"),
                DateTimeFormatter.ofPattern("MM/dd/yyyy"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd")
        };
        for (DateTimeFormatter fmt : formatters) {
            try {
                return LocalDate.parse(value, fmt);
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }
        log.warn("Could not parse date '{}', defaulting to today", value);
        return LocalDate.now();
    }

    private static int parseIntSafe(String value) {
        try {
            return Integer.parseInt(value.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static BigDecimal parseBigDecimalSafe(String value) {
        try {
            return new BigDecimal(value.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private enum ProcessResultType {
        NEW, UPDATED, SKIPPED
    }

    private record ProcessResult(ProcessResultType type, UUID purchaseOrderId) { }

    private Instant calculateNextRun(SyncCadence cadence) {
        Instant now = Instant.now();
        return switch (cadence) {
            case HOURLY -> now.plus(1, ChronoUnit.HOURS);
            case DAILY -> now.plus(1, ChronoUnit.DAYS);
            case WEEKLY -> now.plus(7, ChronoUnit.DAYS);
            case MONTHLY -> now.plus(30, ChronoUnit.DAYS);
            default -> null;
        };
    }
}
