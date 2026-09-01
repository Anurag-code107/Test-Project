package com.tenxengage.app.controller;

import com.tenxengage.app.dto.request.DealQualifierRequest;
import com.tenxengage.app.dto.response.DealQualifierResponse;
import com.tenxengage.app.dto.response.InvoiceExtractionResponse;
import com.tenxengage.app.dto.response.PartnerContextResponse;
import com.tenxengage.app.dto.response.QualifiedIncentiveResult;
import com.tenxengage.app.security.RequiresPermission;
import com.tenxengage.app.service.DealQualifierInsightService;
import com.tenxengage.app.service.DealQualifierService;
import com.tenxengage.app.service.InvoiceExtractionService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/deal-qualifier")
public class DealQualifierController {

    private static final Logger log = LoggerFactory.getLogger(DealQualifierController.class);

    private final DealQualifierService dealQualifierService;
    private final InvoiceExtractionService invoiceExtractionService;
    private final DealQualifierInsightService insightService;

    public DealQualifierController(DealQualifierService dealQualifierService,
                                    InvoiceExtractionService invoiceExtractionService,
                                    DealQualifierInsightService insightService) {
        this.dealQualifierService = dealQualifierService;
        this.invoiceExtractionService = invoiceExtractionService;
        this.insightService = insightService;
    }

    @PostMapping("/evaluate")
    @RequiresPermission("action.deal_qualifier.evaluate")
    public ResponseEntity<DealQualifierResponse> evaluateDeal(
            @Valid @RequestBody DealQualifierRequest request) {
        DealQualifierResponse response = dealQualifierService.evaluateDeal(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/upload-invoice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresPermission("action.deal_qualifier.upload")
    public ResponseEntity<InvoiceExtractionResponse> uploadInvoice(
            @RequestParam("file") MultipartFile file) {
        InvoiceExtractionResponse response = invoiceExtractionService.extractFromInvoice(file);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/{incentiveId}/insights", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RequiresPermission("action.deal_qualifier.insights")
    public SseEmitter streamInsights(@PathVariable UUID incentiveId,
                                      @Valid @RequestBody DealQualifierRequest dealInput) {
        // Re-evaluate the deal to get the full results context
        DealQualifierResponse evalResult = dealQualifierService.evaluateDeal(dealInput);

        QualifiedIncentiveResult matchResult = evalResult.results().stream()
                .filter(r -> r.incentiveId().equals(incentiveId))
                .findFirst()
                .orElse(null);

        if (matchResult == null && !evalResult.results().isEmpty()) {
            matchResult = evalResult.results().get(0);
        }

        SseEmitter emitter = new SseEmitter(60_000L);

        if (matchResult == null) {
            try {
                emitter.send(SseEmitter.event()
                        .name("insight")
                        .data("No matching incentive found for the given deal parameters."));
                emitter.send(SseEmitter.event().name("done").data(""));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
            return emitter;
        }

        insightService.streamInsight(emitter, dealInput, evalResult.partnerRegion(),
                matchResult, evalResult.results());
        return emitter;
    }

    @GetMapping("/partner-context")
    @RequiresPermission("action.deal_qualifier.context")
    public ResponseEntity<PartnerContextResponse> getPartnerContext() {
        PartnerContextResponse context = dealQualifierService.getPartnerContext();
        return ResponseEntity.ok(context);
    }
}
