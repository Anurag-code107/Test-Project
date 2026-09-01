package com.tenxengage.app.controller;

import com.tenxengage.app.service.ApprovalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/approvals")
@Tag(name = "Approvals", description = "Public approval decision endpoints (no auth required)")
@Validated
public class ApprovalController {

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @GetMapping("/incentive")
    @Operation(summary = "Get incentive details for approval review",
               description = "Public endpoint — returns incentive data from an approval token. Returns 409 if already decided or token is from an old round.")
    public ResponseEntity<?> getIncentiveForApproval(@RequestParam @NotBlank @Size(max = 2048) String token) {
        ApprovalService.ApprovalReviewResult result = approvalService.getIncentiveForApproval(token);

        if (!result.valid()) {
            if ("expired".equals(result.rejectReason()) || "already_decided".equals(result.rejectReason())) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "alreadyDecided", true,
                    "decision", result.priorDecision() != null ? result.priorDecision() : "expired"
                ));
            }
            return ResponseEntity.badRequest().build();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("incentive", result.incentiveDetail());
        response.put("approverEmail", result.approverEmail());
        response.put("approverCategory", result.approverCategory());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/decide")
    @Operation(summary = "Process approval decision (JSON)", description = "Called by frontend to process approve/reject")
    public ResponseEntity<Map<String, Object>> processDecisionJson(
            @RequestParam @NotBlank @Size(max = 2048) String token,
            @RequestParam @NotBlank @Pattern(regexp = "^(APPROVED|REJECTED|approved|rejected)$") String action,
            @RequestParam(required = false) @Size(max = 1000) String comment) {
        ApprovalService.ApprovalResult result = approvalService.processApproval(token, action, comment);
        return ResponseEntity.ok(Map.of(
            "success", result.success(),
            "message", result.message(),
            "action", result.action() != null ? result.action() : ""
        ));
    }

    @PostMapping(value = "/decide/browser", produces = MediaType.TEXT_HTML_VALUE)
    @Operation(summary = "Process approval decision (HTML)", description = "Browser form submission for approve/reject")
    public ResponseEntity<String> processDecisionHtml(
            @RequestParam @NotBlank @Size(max = 2048) String token,
            @RequestParam @NotBlank @Pattern(regexp = "^(APPROVED|REJECTED|approved|rejected)$") String action) {
        ApprovalService.ApprovalResult result = approvalService.processApproval(token, action, null);
        String html = buildResponseHtml(result);
        return ResponseEntity.ok(html);
    }

    private String buildResponseHtml(ApprovalService.ApprovalResult result) {
        String icon;
        String color;
        String title;

        if (!result.success()) {
            icon = "&#10060;";
            color = "#ef4444";
            title = "Error";
        } else if ("approved".equals(result.action())) {
            icon = "&#9989;";
            color = "#059669";
            title = "Approved";
        } else {
            icon = "&#10060;";
            color = "#dc2626";
            title = "Rejected";
        }

        String safeMessage = escapeHtml(result.message());

        return """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
              <title>Approval Decision - RewardsCloud</title>
            </head>
            <body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; \
            display: flex; justify-content: center; align-items: center; min-height: 100vh; \
            margin: 0; background-color: #f9fafb;">
              <div style="text-align: center; background: white; border-radius: 16px; padding: 48px; \
            box-shadow: 0 4px 6px rgba(0,0,0,0.07); max-width: 440px; width: 90%%;">
                <div style="font-size: 64px; margin-bottom: 16px;">%s</div>
                <h1 style="color: %s; margin: 0 0 12px; font-size: 28px;">%s</h1>
                <p style="color: #6b7280; font-size: 16px; line-height: 1.6; margin: 0;">%s</p>
                <p style="color: #9ca3af; font-size: 13px; margin-top: 24px;">You may close this window.</p>
              </div>
            </body>
            </html>
            """.formatted(icon, color, title, safeMessage);
    }

    private static String escapeHtml(String input) {
        if (input == null) return "";
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;");
    }
}
