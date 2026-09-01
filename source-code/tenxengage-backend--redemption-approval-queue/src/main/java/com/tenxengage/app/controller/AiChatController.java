package com.tenxengage.app.controller;

import com.anthropic.models.messages.MessageCreateParams;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenxengage.app.dto.request.AiChatRequest;
import com.tenxengage.app.security.RequiresPermission;
import com.tenxengage.app.security.TenantContext;
import com.tenxengage.app.service.AiChatService;
import com.tenxengage.app.service.DocumentTextExtractor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai")
@Tag(name = "AI Chat", description = "AI Incentive Copilot endpoints")
public class AiChatController {

    private static final Logger log = LoggerFactory.getLogger(AiChatController.class);

    private final AiChatService aiChatService;
    private final DocumentTextExtractor documentTextExtractor;
    private final ObjectMapper objectMapper;

    public AiChatController(AiChatService aiChatService,
                            DocumentTextExtractor documentTextExtractor,
                            ObjectMapper objectMapper) {
        this.aiChatService = aiChatService;
        this.documentTextExtractor = documentTextExtractor;
        this.objectMapper = objectMapper;
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RequiresPermission("action.ai.copilot")
    @Operation(summary = "AI Chat", description = "Stream AI copilot responses for incentive builder")
    public SseEmitter chat(@Valid @RequestBody AiChatRequest request) {
        if (!aiChatService.isAvailable()) {
            return unavailableEmitter();
        }

        log.info("AI chat request: {} messages, type={}", request.conversationHistory().size(), request.incentiveType());

        MessageCreateParams.Builder paramsBuilder = buildParams(request);

        return startStreaming(paramsBuilder, request.currentState(), request.incentiveType());
    }

    @PostMapping(value = "/chat-with-document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RequiresPermission("action.ai.assistant")
    @Operation(summary = "AI Chat with Document", description = "Upload a document (PDF, PPTX) and stream AI copilot responses that extract incentive details from the document")
    public SseEmitter chatWithDocument(
            @RequestPart("file") MultipartFile file,
            @RequestPart("request") String requestJson) {

        if (!aiChatService.isAvailable()) {
            return unavailableEmitter();
        }

        // Parse the JSON request part
        AiChatRequest request;
        try {
            request = objectMapper.readValue(requestJson, AiChatRequest.class);
        } catch (Exception e) {
            log.error("Failed to parse chat request JSON", e);
            SseEmitter emitter = new SseEmitter(0L);
            emitter.completeWithError(new IllegalArgumentException("Invalid request JSON"));
            return emitter;
        }

        // Validate file type
        if (!documentTextExtractor.isSupported(file)) {
            SseEmitter emitter = new SseEmitter(0L);
            emitter.completeWithError(new IllegalArgumentException(
                    "Unsupported file type. Please upload a PDF, PPTX, TXT, or CSV file."));
            return emitter;
        }

        // Extract text from the uploaded document
        String extractedText;
        try {
            extractedText = documentTextExtractor.extract(file);
        } catch (Exception e) {
            log.error("Failed to extract text from uploaded document: {}", file.getOriginalFilename(), e);
            SseEmitter emitter = new SseEmitter(0L);
            emitter.completeWithError(new RuntimeException("Failed to read the uploaded document. Please try a different file."));
            return emitter;
        }

        log.info("AI chat-with-document: file={}, size={}, extractedChars={}, messages={}, type={}",
                file.getOriginalFilename(), file.getSize(), extractedText.length(),
                request.conversationHistory().size(), request.incentiveType());

        // Stage 1: Convert the document into a script of simple copilot prompts.
        // Stage 2: Feed each prompt to the copilot one-by-one (text hidden, actions visible).
        SseEmitter emitter = new SseEmitter(600_000L); // 10 min — script may have many prompts

        SecurityContext securityContext = SecurityContextHolder.getContext();
        // See note in startStreaming: TenantContext is a plain ThreadLocal and must be
        // explicitly propagated to the virtual thread, otherwise tenant-scoped reads
        // return null on the streaming side.
        UUID tenantClientId = TenantContext.getClientId();
        String tenantSubdomain = TenantContext.getSubdomain();
        Thread.startVirtualThread(() -> {
            SecurityContextHolder.setContext(securityContext);
            if (tenantClientId != null) TenantContext.setClientId(tenantClientId);
            if (tenantSubdomain != null) TenantContext.setSubdomain(tenantSubdomain);
            try {
                emitter.send(SseEmitter.event().name("text_delta").data(
                        Map.of("text", "Reading and analyzing your document...\n\n")));

                List<String> prompts = aiChatService.distillToScript(extractedText);

                if (prompts.isEmpty()) {
                    emitter.send(SseEmitter.event().name("text_delta").data(
                            Map.of("text", "I couldn't extract enough information from this document. " +
                                    "Could you try a different file or paste the key details directly?")));
                    emitter.send(SseEmitter.event().name("done").data("{}"));
                    emitter.complete();
                    return;
                }

                emitter.send(SseEmitter.event().name("text_delta").data(
                        Map.of("text", "Found " + prompts.size() + " items to fill. Working on it now...\n\n")));

                aiChatService.streamDocumentScript(emitter, prompts, request.currentState(), request.incentiveType());
            } catch (Exception e) {
                log.error("Document extraction pipeline error", e);
                try {
                    emitter.send(SseEmitter.event().name("error").data(
                            Map.of("message", "Failed to process the document. Please try again.")));
                    emitter.complete();
                } catch (IOException ignored) {}
            } finally {
                SecurityContextHolder.clearContext();
                TenantContext.clear();
            }
        });

        return emitter;
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private MessageCreateParams.Builder buildParams(AiChatRequest request) {
        List<Map<String, String>> rawHistory = request.conversationHistory().stream()
                .map(entry -> Map.of("role", entry.role(), "content", entry.content()))
                .toList();
        return aiChatService.buildParamsFromHistory(rawHistory);
    }

    /**
     * Builds params from history with document context appended to the last user message,
     * ensuring the alternating user/assistant message pattern is maintained.
     */
    private MessageCreateParams.Builder buildParamsWithDocument(AiChatRequest request, String docContext) {
        List<Map<String, String>> rawHistory = new java.util.ArrayList<>(
                request.conversationHistory().stream()
                        .map(entry -> new java.util.HashMap<>(Map.of("role", entry.role(), "content", entry.content())))
                        .toList()
        );
        // Append document context to the last user message
        for (int i = rawHistory.size() - 1; i >= 0; i--) {
            if ("user".equals(rawHistory.get(i).get("role"))) {
                rawHistory.get(i).put("content", rawHistory.get(i).get("content") + docContext);
                break;
            }
        }
        return aiChatService.buildParamsFromHistory(rawHistory);
    }

    private SseEmitter startStreaming(MessageCreateParams.Builder paramsBuilder,
                                      Map<String, Object> currentState,
                                      String incentiveType) {
        // 5 minutes — multi-round tool loops can take 20-30s per round.
        SseEmitter emitter = new SseEmitter(300_000L);

        SecurityContext securityContext = SecurityContextHolder.getContext();
        // TenantContext is a plain ThreadLocal (not InheritableThreadLocal), so capture
        // its values on the request thread and re-set them inside the virtual thread.
        // Without this, downstream services that read TenantContext.getClientId() (e.g.
        // BuilderConfigService) see null on the streaming thread and tenant-scoped
        // queries return empty results.
        UUID tenantClientId = TenantContext.getClientId();
        String tenantSubdomain = TenantContext.getSubdomain();
        Thread.startVirtualThread(() -> {
            SecurityContextHolder.setContext(securityContext);
            if (tenantClientId != null) TenantContext.setClientId(tenantClientId);
            if (tenantSubdomain != null) TenantContext.setSubdomain(tenantSubdomain);
            try {
                aiChatService.streamChat(emitter, paramsBuilder, currentState, incentiveType);
            } finally {
                SecurityContextHolder.clearContext();
                TenantContext.clear();
            }
        });

        return emitter;
    }

    private SseEmitter unavailableEmitter() {
        SseEmitter emitter = new SseEmitter(0L);
        emitter.completeWithError(new IllegalStateException(
                "AI service is not configured. Set ANTHROPIC_API_KEY to enable."));
        return emitter;
    }
}
