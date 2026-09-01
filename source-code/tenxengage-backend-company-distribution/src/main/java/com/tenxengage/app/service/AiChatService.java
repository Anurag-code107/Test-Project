package com.tenxengage.app.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlock;
import com.anthropic.models.messages.ToolUseBlockParam;
import com.anthropic.core.JsonValue;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.time.LocalDate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.tenxengage.app.dto.response.BuilderConfigResponse;
import com.tenxengage.app.dto.response.BuilderFieldConfigResponse;
import com.tenxengage.app.dto.response.BuilderSectionConfigResponse;
import com.tenxengage.app.dto.response.LocationFilterOptionsResponse;
import com.tenxengage.app.dto.response.RuleFieldResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);
    private static final int MAX_TOOL_ROUNDS = 10;

    private final AnthropicClient client;
    private final ObjectMapper objectMapper;
    private final ProductService productService;
    private final LmsCourseService lmsCourseService;
    private final DataObjectService dataObjectService;
    private final FiscalYearConfigService fiscalYearConfigService;
    private final LocationService locationService;
    private final BuilderConfigService builderConfigService;
    private final String model;
    private final long maxTokens;
    private final String systemPromptTemplate;

    public AiChatService(@Autowired(required = false) @Nullable AnthropicClient client,
                         ObjectMapper objectMapper,
                         ProductService productService,
                         LmsCourseService lmsCourseService,
                         DataObjectService dataObjectService,
                         FiscalYearConfigService fiscalYearConfigService,
                         LocationService locationService,
                         BuilderConfigService builderConfigService,
                         @Value("${app.ai.model}") String model,
                         @Value("${app.ai.max-tokens}") long maxTokens) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.productService = productService;
        this.lmsCourseService = lmsCourseService;
        this.dataObjectService = dataObjectService;
        this.fiscalYearConfigService = fiscalYearConfigService;
        this.locationService = locationService;
        this.builderConfigService = builderConfigService;
        this.model = model;
        this.maxTokens = maxTokens;
        this.systemPromptTemplate = loadSystemPrompt();
    }

    public boolean isAvailable() {
        return client != null;
    }

    private static final String SCRIPT_PROMPT =
            "You are a document analyst. Read the document below and produce a numbered list of " +
            "simple, direct instructions that a copilot can follow one-by-one to fill out an " +
            "incentive builder form. ONLY include information EXPLICITLY stated in the document " +
            "— NEVER invent names, emails, or amounts.\n\n" +
            "Each instruction should be a short, natural sentence — the kind a user would type " +
            "into a chatbot. Group related fields into one instruction where it makes sense.\n\n" +
            "Use this order:\n" +
            "1. Name and description\n" +
            "2. Timeline (dates, fiscal year, fiscal quarter)\n" +
            "3. Regions and audience (roles, partner types)\n" +
            "4. Budget (currencies, amounts — specify global vs per-region, include reward message)\n" +
            "5. For EACH sales requirement: one instruction that names it, lists its eligible " +
            "products, and describes its payout tiers/bands with exact amounts. IMPORTANT: " +
            "tiers (Tier 1, Tier 2, Tier 3) are payout bands WITHIN a single requirement, " +
            "NOT separate requirements.\n\n" +
            "Skip any section where the document has no relevant data. Do NOT include approval " +
            "information (that step is disabled).\n\n" +
            "Example output:\n" +
            "1. Set the incentive name to \"Q3 Server Push\" with description \"Drive server sales " +
            "across all regions with tiered payouts.\"\n" +
            "2. Set the timeline to Q3 FY2026, start date 2026-07-01, end date 2026-09-30.\n" +
            "3. Set regions to AMERICAS and EMEAR. User roles: Partner Seller.\n" +
            "4. Set reward currencies to cash. Global budget: $500,000 cash. Reward message: " +
            "\"Earn up to 3% of total booking amount per deal.\"\n" +
            "5. Create a requirement called \"Server Deals\" for products: PowerEdge R760, " +
            "Catalyst 8500. Payout: percentage of total booking. Bands: $0-$50,000 at 1%, " +
            "$50,000-$100,000 at 2%, over $100,000 at 3%.\n\n" +
            "--- DOCUMENT ---\n";

    /**
     * Pre-processing step: reads the raw document and produces a numbered list of
     * simple copilot instructions. Each instruction is a natural-language prompt
     * that the copilot can handle one at a time (the same way a user would type them).
     */
    public List<String> distillToScript(String rawText) {
        if (client == null) return List.of();

        try {
            log.info("Distilling document to script: {} chars of raw text", rawText.length());
            long start = System.currentTimeMillis();

            Message response = client.messages().create(MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(2048)
                    .system("You convert documents into step-by-step instructions. Be precise with all " +
                            "numbers, dates, and names. NEVER invent information not in the document.")
                    .addUserMessage(SCRIPT_PROMPT + rawText)
                    .build());

            String output = response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(textBlock -> textBlock.text())
                    .collect(Collectors.joining("\n"));

            // Parse numbered lines into individual prompts
            List<String> prompts = new ArrayList<>();
            for (String line : output.split("\n")) {
                String trimmed = line.strip();
                // Match lines starting with "1." "2." etc.
                if (trimmed.matches("^\\d+\\.\\s+.+")) {
                    prompts.add(trimmed.replaceFirst("^\\d+\\.\\s+", ""));
                }
            }

            log.info("Document distilled to {} prompts in {}ms",
                    prompts.size(), System.currentTimeMillis() - start);
            return prompts;
        } catch (Exception e) {
            log.warn("Document script distillation failed", e);
            return List.of();
        }
    }

    public void streamChat(SseEmitter emitter,
                           MessageCreateParams.Builder paramsBuilder,
                           Map<String, Object> currentState,
                           String incentiveType) {
        streamChat(emitter, paramsBuilder, currentState, incentiveType, null);
    }

    // Short labels for progress updates sent to the user during script execution
    private static final String[] PROGRESS_LABELS = {
            "Setting up the basics",
            "Configuring the timeline",
            "Setting up audience and regions",
            "Configuring budget and rewards",
            "Building incentive criteria",
            "Finishing up",
    };

    /**
     * Executes a list of pre-generated prompts (from distillToScript) against the copilot,
     * one at a time, within a single conversation. Intermediate copilot chatter is hidden;
     * the user sees progress labels and tool-call actions filling in the builder.
     */
    public void streamDocumentScript(SseEmitter emitter,
                                      List<String> prompts,
                                      Map<String, Object> currentState,
                                      String incentiveType) {
        try {
            String systemPrompt = buildSystemPrompt(currentState, incentiveType);

            MessageCreateParams.Builder paramsBuilder = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(maxTokens)
                    .system(systemPrompt);
            for (Tool tool : buildTools()) {
                paramsBuilder.addTool(tool);
            }

            // Feed each prompt as a user message and run a mini tool loop
            for (int i = 0; i < prompts.size(); i++) {
                String prompt = prompts.get(i);
                log.info("Document script: executing prompt {}/{}: {}", i + 1, prompts.size(),
                        prompt.length() > 80 ? prompt.substring(0, 80) + "..." : prompt);

                // Send a visible progress update so the user knows what's happening
                String label = i < PROGRESS_LABELS.length ? PROGRESS_LABELS[i] : "Processing step " + (i + 1);
                emitter.send(SseEmitter.event().name("text_delta").data(
                        Map.of("text", label + "... ")));

                paramsBuilder.addUserMessage(prompt);

                // Mini tool loop for this prompt (max 5 rounds per prompt)
                for (int round = 0; round < 5; round++) {
                    Message response = client.messages().create(paramsBuilder.build());

                    // Actions (tool calls) are always forwarded; text is hidden
                    boolean hasToolUse = processContentBlocks(emitter, response, true);

                    StopReason stopReason = response.stopReason().orElse(null);
                    log.debug("Script prompt {}, round {}: stopReason={}, hasToolUse={}",
                            i + 1, round, stopReason, hasToolUse);

                    if (StopReason.MAX_TOKENS.equals(stopReason)) {
                        paramsBuilder.addMessage(response);
                        paramsBuilder.addUserMessage("Continue where you left off.");
                        continue;
                    }

                    if (!hasToolUse) {
                        // Add response to conversation history so next prompt has context
                        paramsBuilder.addMessage(response);
                        break;
                    }

                    // Process tool results
                    paramsBuilder.addMessage(response);
                    List<ContentBlockParam> toolResults = new ArrayList<>();
                    for (ContentBlock block : response.content()) {
                        if (block.toolUse().isEmpty()) continue;
                        ToolUseBlock toolUse = block.toolUse().get();
                        String resultContent = buildToolResult(toolUse);
                        toolResults.add(ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                                .toolUseId(toolUse.id())
                                .content(resultContent)
                                .build()));
                    }
                    paramsBuilder.addUserMessageOfBlockParams(toolResults);
                }

                // Mark this step done
                emitter.send(SseEmitter.event().name("text_delta").data(
                        Map.of("text", "\u2713\n")));
            }

            // Final visible prompt: ask for a summary of what was filled
            emitter.send(SseEmitter.event().name("text_delta").data(
                    Map.of("text", "\n")));

            paramsBuilder.addUserMessage(
                    "All done. Provide a brief summary of what you filled from the document and " +
                    "what the user still needs to provide. Keep it concise.");

            Message summaryResponse = client.messages().create(paramsBuilder.build());
            processContentBlocks(emitter, summaryResponse, false); // Text IS visible

            emitter.send(SseEmitter.event().name("done").data("{}"));
            emitter.complete();
        } catch (Exception e) {
            log.error("Document script execution error", e);
            try {
                emitter.send(SseEmitter.event().name("error").data(
                        Map.of("message", "An error occurred while processing the document")));
                emitter.complete();
            } catch (IOException ignored) {}
        }
    }

    /**
     * @param distilledContent when non-null, enables phased document extraction and
     *        embeds this content in each phase prompt so Claude always has it in focus.
     */
    public void streamChat(SseEmitter emitter,
                           MessageCreateParams.Builder paramsBuilder,
                           Map<String, Object> currentState,
                           String incentiveType,
                           @Nullable String distilledContent) {
        try {
            String systemPrompt = buildSystemPrompt(currentState, incentiveType);

            paramsBuilder
                    .model(model)
                    .maxTokens(maxTokens)
                    .system(systemPrompt);
            for (Tool tool : buildTools()) {
                paramsBuilder.addTool(tool);
            }

            // Focused extraction phases for document uploads. Each phase narrows
            // Claude's task to 1-2 builder steps so it produces smaller, more
            // reliable responses. The distilled content is embedded in each phase
            // prompt so Claude doesn't lose sight of it as the conversation grows.
            // For regular chat the array is empty and the loop runs normally.
            final boolean isDocumentExtraction = distilledContent != null;
            final String docRef = isDocumentExtraction
                    ? "\n\nHere is the extracted document data for reference:\n---\n" + distilledContent + "\n---\n"
                    : "";
            // Track which UPDATE_* tool calls Claude has dispatched so we can skip
            // phases whose work is already done (e.g., Claude fills budget in Phase 1).
            java.util.Set<String> dispatchedActions = new java.util.HashSet<>();
            boolean currentPhaseHadToolUse = false;
            boolean hasRetriedCurrentPhase = false;

            // Phase definitions with a "skip condition" key — if all listed actions
            // have already been dispatched, this phase is skipped entirely.
            record Phase(String prompt, java.util.Set<String> skipIfDispatched) {}
            final Phase[] extractionPhases = isDocumentExtraction ? new Phase[] {
                new Phase(
                    "[Phase 2: Budget] Focus ONLY on Budget. Dispatch UPDATE_BUDGET with: " +
                    "selectedCurrencies, budgetMode, globalBudgets or regionBudgets, rewardMessage, " +
                    "and any max-per-partner/max-per-user limits. Use exact values from the data below. " +
                    "Keep your text response to 1-2 sentences." + docRef,
                    java.util.Set.of("UPDATE_BUDGET")),
                new Phase(
                    "[Phase 3a: Product Search] Look at the ELIGIBLE PRODUCTS and REQUIREMENTS " +
                    "sections in the data below. Call search_products for each product name or " +
                    "category mentioned. You may make multiple search_products calls in one " +
                    "response. Do NOT dispatch UPDATE_CRITERIA yet — just search. " +
                    "Keep text to 1 sentence." + docRef,
                    java.util.Set.of()),  // Never skip — always search
                new Phase(
                    "[Phase 3b: Build Criteria] Now dispatch UPDATE_CRITERIA. You have the " +
                    "product SKUs from the search results above. Build the salesRequirements " +
                    "array using this exact structure for EACH requirement in the document:\n" +
                    "{\n" +
                    "  \"name\": \"<requirement name from document>\",\n" +
                    "  \"eligibilityGroups\": [{\"rules\": [{\"ruleType\": \"<UUID from AVAILABLE RULE FIELDS>\", ...}]}],\n" +
                    "  \"payouts\": [{\n" +
                    "    \"currencyId\": \"<from selected currencies>\",\n" +
                    "    \"payoutType\": \"PERCENTAGE\" or \"FLAT\",\n" +
                    "    \"against\": \"TOTAL_BOOKING\" or \"ELIGIBLE_PRODUCTS\",\n" +
                    "    \"bands\": [{\"minAmount\": \"<from document>\", \"maxAmount\": \"<from document>\", \"payoutValue\": \"<from document>\"}]\n" +
                    "  }]\n" +
                    "}\n" +
                    "Remember: tiers are bands INSIDE one requirement, NOT separate requirements. " +
                    "All numeric values must be strings. You MUST call the update_builder tool — " +
                    "do not just describe what you plan to do." + docRef,
                    java.util.Set.of("UPDATE_CRITERIA")),
                new Phase(
                    "[Phase 4: Summary] Provide your COMPLETE summary of all steps: what was " +
                    "filled and what the user still needs to provide. Do NOT dispatch any " +
                    "approval-related actions — that step is currently disabled." + docRef,
                    java.util.Set.of()) // Summary never skipped
            } : new Phase[0];
            int nextPhase = 0;

            for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
                Message response = client.messages().create(paramsBuilder.build());

                boolean hasToolUse = processContentBlocks(emitter, response);

                StopReason stopReason = response.stopReason().orElse(null);
                log.info("AI round {}: stopReason={}, hasToolUse={}, contentBlocks={}",
                        round, stopReason, hasToolUse, response.content().size());

                // If the response was truncated mid-generation, continue the conversation
                // so Claude can finish its thought (e.g., a large criteria payload).
                if (StopReason.MAX_TOKENS.equals(stopReason)) {
                    log.warn("AI response truncated at round {} (max_tokens={}). Continuing...",
                            round, maxTokens);
                    paramsBuilder.addMessage(response);
                    paramsBuilder.addUserMessage("Continue where you left off.");
                    continue;
                }

                if (!hasToolUse) {
                    // Find the next phase that hasn't already been satisfied by
                    // earlier tool dispatches (e.g., skip Budget if UPDATE_BUDGET
                    // was already dispatched during Phase 1).
                    while (nextPhase < extractionPhases.length) {
                        Phase candidate = extractionPhases[nextPhase];
                        if (!candidate.skipIfDispatched().isEmpty()
                                && dispatchedActions.containsAll(candidate.skipIfDispatched())) {
                            log.info("Document extraction: skipping phase {} (already dispatched: {})",
                                    nextPhase + 1, candidate.skipIfDispatched());
                            nextPhase++;
                            continue;
                        }
                        break;
                    }

                    if (nextPhase < extractionPhases.length) {
                        // If the current phase didn't produce any tool calls and we haven't
                        // retried yet, nudge Claude to actually dispatch the action.
                        if (!currentPhaseHadToolUse && !hasRetriedCurrentPhase && nextPhase > 0) {
                            hasRetriedCurrentPhase = true;
                            log.info("Document extraction: phase produced no tool calls, retrying at round {}", round);
                            paramsBuilder.addMessage(response);
                            paramsBuilder.addUserMessage(
                                    "You described what you plan to do but didn't dispatch any tool calls. " +
                                    "Please call the update_builder tool now with the data. " +
                                    "Do not explain — just dispatch the action.");
                            continue;
                        }

                        log.info("Document extraction: advancing to phase {} at round {}",
                                nextPhase + 1, round);
                        paramsBuilder.addMessage(response);
                        paramsBuilder.addUserMessage(extractionPhases[nextPhase].prompt());
                        nextPhase++;
                        currentPhaseHadToolUse = false;
                        hasRetriedCurrentPhase = false;
                        continue;
                    }
                    break; // All phases done or regular chat — we're done
                }

                currentPhaseHadToolUse = true;

                // Add assistant response and tool results to continue the conversation
                paramsBuilder.addMessage(response);

                // Build tool results for each tool use block and track dispatched actions
                List<ContentBlockParam> toolResults = new ArrayList<>();
                for (ContentBlock block : response.content()) {
                    if (block.toolUse().isEmpty()) continue;
                    ToolUseBlock toolUse = block.toolUse().get();

                    // Track update_builder action types for phase-skip logic
                    if ("update_builder".equals(toolUse.name())) {
                        try {
                            JsonNode input = toolUse._input().convert(JsonNode.class);
                            if (input.has("action_type")) {
                                dispatchedActions.add(input.get("action_type").asText());
                            }
                        } catch (Exception ignored) {}
                    }
                    String resultContent = buildToolResult(toolUse);
                    toolResults.add(ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                            .toolUseId(toolUse.id())
                            .content(resultContent)
                            .build()));
                }

                paramsBuilder.addUserMessageOfBlockParams(toolResults);
            }

            emitter.send(SseEmitter.event().name("done").data("{}"));
            emitter.complete();
        } catch (Exception e) {
            log.error("AI chat error", e);
            try {
                emitter.send(SseEmitter.event().name("error").data(
                        Map.of("message", "An error occurred while processing your request")));
                emitter.complete();
            } catch (IOException ignored) {
                // Client disconnected
            }
        }
    }

    /**
     * Process content blocks from a Claude response, emitting SSE events.
     * Returns true if the response contained tool use blocks.
     */
    private boolean processContentBlocks(SseEmitter emitter, Message response) throws IOException {
        return processContentBlocks(emitter, response, false);
    }

    /** Strip leaked tool-call XML that Claude sometimes emits as plain text. */
    private static String sanitizeToolLeaks(String text) {
        String cleaned = text.replaceAll("</?invoke[^>]*>", "");
        cleaned = cleaned.replaceAll("</?parameter[^>]*>", "");
        cleaned = cleaned.replaceAll("\\[\"[^\"]*\"(?:,\\s*\"[^\"]*\")*\\]", "");
        return cleaned.strip();
    }

    /**
     * Process content blocks from a Claude response.
     * @param suppressText when true, text_delta events are NOT sent (used during scripted
     *        document extraction so intermediate chatter is hidden from the user).
     *        Action events from tool calls are always sent regardless of this flag.
     */
    private boolean processContentBlocks(SseEmitter emitter, Message response, boolean suppressText) throws IOException {
        boolean hasToolUse = false;

        for (ContentBlock block : response.content()) {
            // Handle text blocks
            if (!suppressText) {
                block.text().ifPresent(textBlock -> {
                    String text = sanitizeToolLeaks(textBlock.text());
                    if (!text.isBlank()) {
                        try {
                            emitter.send(SseEmitter.event().name("text_delta").data(
                                    Map.of("text", text)));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
            }

            // Handle tool use blocks — always processed regardless of suppressText
            if (block.toolUse().isPresent()) {
                hasToolUse = true;
                ToolUseBlock toolUse = block.toolUse().get();
                processToolUse(emitter, toolUse);
            }
        }

        return hasToolUse;
    }

    private void processToolUse(SseEmitter emitter, ToolUseBlock toolUse) throws IOException {
        String toolName = toolUse.name();
        JsonNode inputNode;
        try {
            inputNode = toolUse._input().convert(JsonNode.class);
        } catch (Exception e) {
            log.warn("Failed to parse tool input for {}", toolName, e);
            return;
        }

        if ("update_builder".equals(toolName)) {
            String actionType = inputNode.has("action_type")
                    ? inputNode.get("action_type").asText() : "";
            JsonNode payload = inputNode.has("payload")
                    ? inputNode.get("payload") : objectMapper.createObjectNode();

            emitter.send(SseEmitter.event().name("action").data(
                    Map.of("type", actionType, "payload", payload)));
        } else if ("suggest_actions".equals(toolName)) {
            List<String> suggestions = new ArrayList<>();
            if (inputNode.has("suggestions") && inputNode.get("suggestions").isArray()) {
                for (JsonNode s : inputNode.get("suggestions")) {
                    suggestions.add(s.asText());
                }
            }
            emitter.send(SseEmitter.event().name("suggestions").data(
                    Map.of("suggestions", suggestions)));
        } else if ("search_products".equals(toolName)) {
            // Handled in tool results loop — no SSE event needed
        } else if ("search_courses".equals(toolName)) {
            // Handled in tool results loop — no SSE event needed
        }
    }

    /**
     * Build a MessageCreateParams.Builder from raw frontend conversation history.
     * Applies a sliding window of the last 20 messages.
     */
    public MessageCreateParams.Builder buildParamsFromHistory(List<Map<String, String>> rawHistory) {
        MessageCreateParams.Builder builder = MessageCreateParams.builder();

        // Sliding window: last 20 messages
        int start = Math.max(0, rawHistory.size() - 20);
        List<Map<String, String>> trimmed = rawHistory.subList(start, rawHistory.size());

        // Ensure conversation starts with a user message (Anthropic requirement)
        boolean foundFirstUser = false;
        for (Map<String, String> entry : trimmed) {
            String role = entry.get("role");
            String content = entry.get("content");
            if (content == null || content.isBlank()) continue;

            if (!foundFirstUser) {
                if (!"user".equals(role)) continue;
                foundFirstUser = true;
            }

            if ("user".equals(role)) {
                builder.addUserMessage(content);
            } else {
                builder.addAssistantMessage(content);
            }
        }

        return builder;
    }

    private String buildToolResult(ToolUseBlock toolUse) {
        if ("search_products".equals(toolUse.name())) {
            try {
                JsonNode input = toolUse._input().convert(JsonNode.class);
                String search = input.has("search") ? input.get("search").asText(null) : null;
                String category = input.has("category") ? input.get("category").asText(null) : null;
                var products = productService.getProducts(category, search);
                return objectMapper.writeValueAsString(products);
            } catch (Exception e) {
                log.warn("Failed to search products", e);
                return "Error searching products.";
            }
        }
        if ("search_courses".equals(toolUse.name())) {
            try {
                JsonNode input = toolUse._input().convert(JsonNode.class);
                String search = input.has("search") ? input.get("search").asText(null) : null;
                String category = input.has("category") ? input.get("category").asText(null) : null;
                var courses = lmsCourseService.getCourses(category, search);
                return objectMapper.writeValueAsString(courses);
            } catch (Exception e) {
                log.warn("Failed to search courses", e);
                return "Error searching courses.";
            }
        }
        return "Action dispatched successfully.";
    }

    private String buildSystemPrompt(Map<String, Object> currentState, String incentiveType) {
        String prompt = systemPromptTemplate.replace("{{currentDate}}", LocalDate.now().toString());

        // Replace fiscal year placeholders with client-specific data
        prompt = replaceFiscalPlaceholders(prompt);

        StringBuilder sb = new StringBuilder(prompt);

        // Inject available rule fields so the AI knows the field UUIDs for eligibility rules
        try {
            List<RuleFieldResponse> ruleFields = dataObjectService.getRuleFields(null, "Sales Data");
            if (!ruleFields.isEmpty()) {
                sb.append("\n\n=== AVAILABLE RULE FIELDS (Sales Data) ===\n");
                sb.append("Use the field UUID as the `ruleType` value when building eligibility rules.\n\n");
                for (RuleFieldResponse f : ruleFields) {
                    sb.append("- **").append(f.ruleLabel() != null ? f.ruleLabel() : f.name()).append("**\n");
                    sb.append("  UUID: `").append(f.id()).append("`\n");
                    sb.append("  dataType: ").append(f.dataType()).append("\n");
                    if (f.ruleWidget() != null) {
                        sb.append("  ruleWidget: ").append(f.ruleWidget()).append("\n");
                    }
                    if (f.sampleValues() != null && !f.sampleValues().isEmpty()) {
                        sb.append("  sampleValues: ").append(f.sampleValues()).append("\n");
                    }
                    sb.append("\n");
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load rule fields for AI prompt", e);
        }

        // Inject the tenant's location hierarchy so the AI can dispatch the hierarchy-aware
        // `locationSelections` shape against real level UUIDs + value names. Builder-options
        // is the right view here: it returns only levels the admin has marked useInBuilder
        // (the same levels Step 2 Participant Eligibility actually renders), with each
        // level's values and their parent relationships intact. useInFilters is a different
        // flag used by the /claims and /home filter bars — unrelated to the builder context.
        try {
            LocationFilterOptionsResponse builderOptions = locationService.getBuilderOptions();
            String hierarchyBlock = formatLocationHierarchyBlock(builderOptions);
            if (!hierarchyBlock.isEmpty()) {
                sb.append(hierarchyBlock);
            }
        } catch (Exception e) {
            log.warn("Failed to load location hierarchy for AI prompt", e);
        }

        // Inject the tenant's admin-defined custom audience fields so the AI knows
        // (a) what custom keys exist for this incentive type, (b) which are mandatory,
        // and (c) that the UPDATE_AUDIENCE payload accepts a `dynamicFields` map. The
        // frontend already wires `audience.dynamicFields` end-to-end (Step3Audience
        // writes it, builderRequestMapper serializes it to customFieldValues, the
        // reducer merges any AI-sent patch); this is purely a prompt-context fix
        // (BUG-045). Skip when `incentiveType` is null — Builder Config is keyed by
        // incentive type, so there's nothing to inject before the user picks one.
        if (incentiveType != null) {
            try {
                BuilderConfigResponse builderConfig = builderConfigService.getBuilderConfig(incentiveType);
                String builderConfigBlock = formatBuilderConfigBlock(builderConfig, "audience");
                if (!builderConfigBlock.isEmpty()) {
                    sb.append(builderConfigBlock);
                }
            } catch (Exception e) {
                log.warn("Failed to load builder config for AI prompt", e);
            }
        }

        sb.append("\n\n=== CURRENT BUILDER STATE ===\n");
        sb.append("Incentive Type: ").append(incentiveType != null ? incentiveType : "Not selected").append("\n");
        try {
            sb.append(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(currentState));
        } catch (JsonProcessingException e) {
            sb.append("(Unable to serialize state)");
        }
        return sb.toString();
    }

    /**
     * Format the tenant's location hierarchy for injection into the copilot system
     * prompt. Package-private + static so it can be unit-tested with fixtures
     * without standing up a Spring context or mocking LocationService.
     * Returns an empty string when the tenant has no configured levels so the
     * caller can skip the append entirely.
     */
    static String formatLocationHierarchyBlock(LocationFilterOptionsResponse options) {
        if (options == null || options.levels() == null || options.levels().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n=== LOCATION HIERARCHY ===\n");
        sb.append("The tenant has configured ").append(options.levels().size())
            .append(" location level(s). Use the level UUIDs to key `locationSelections` in ")
            .append("UPDATE_AUDIENCE; use each value's own valueId to key `locationBudgets` ")
            .append("in UPDATE_BUDGET (per-location mode). Value names are case-sensitive.\n\n");
        for (LocationFilterOptionsResponse.LocationFilterLevel level : options.levels()) {
            sb.append("Level: **").append(level.levelName()).append("** (depth ")
                .append(level.depth()).append(")\n");
            sb.append("  levelId: `").append(level.levelId()).append("`\n");
            if (level.values().isEmpty()) {
                sb.append("  (no values configured yet)\n");
            } else {
                sb.append("  Values:\n");
                for (LocationFilterOptionsResponse.LocationFilterValue v : level.values()) {
                    sb.append("    - ").append(v.name())
                        .append(" (valueId: `").append(v.id()).append("`");
                    if (v.parentId() != null) {
                        sb.append(", parent valueId: `").append(v.parentId()).append("`");
                    }
                    sb.append(")\n");
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * Format the tenant's admin-defined custom fields for a single builder section
     * (e.g. "audience") into a system-prompt context block. Mirrors the structure
     * of {@link #formatLocationHierarchyBlock} so the AI gets a uniform shape across
     * tenant-configurable context. Package-private + static so unit tests can drive
     * fixtures without standing up Spring.
     *
     * <p>Excludes system fields (the four built-in audience inputs the prompt already
     * documents) and returns an empty string when no custom fields exist for the
     * section, so the caller can skip the append entirely. {@code sectionKey} matches
     * the same key the frontend's {@code DynamicExtraFields} reads against
     * (e.g. "audience" → Step 3 Participant Eligibility).
     */
    static String formatBuilderConfigBlock(BuilderConfigResponse config, String sectionKey) {
        if (config == null || config.sections() == null || config.sections().isEmpty()) {
            return "";
        }
        BuilderSectionConfigResponse section = config.sections().stream()
                .filter(s -> sectionKey.equals(s.sectionKey()))
                .findFirst()
                .orElse(null);
        if (section == null || section.fields() == null) {
            return "";
        }
        List<BuilderFieldConfigResponse> customFields = section.fields().stream()
                .filter(f -> !f.isSystem())
                .toList();
        if (customFields.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n=== CUSTOM BUILDER FIELDS (").append(sectionKey).append(") ===\n");
        sb.append("The tenant has configured ").append(customFields.size())
            .append(" custom field(s) on this section. Fill them via the `dynamicFields` slot ")
            .append("inside UPDATE_AUDIENCE (or the matching UPDATE_* action for other sections), ")
            .append("keyed by `fieldKey`. Example: `dynamicFields: { \"<fieldKey>\": \"<value>\" }`. ")
            .append("Mandatory fields MUST be filled before MARK_STEP_COMPLETE for this step.\n\n");
        for (BuilderFieldConfigResponse f : customFields) {
            sb.append("- **").append(f.displayName()).append("**\n");
            sb.append("  fieldKey: `").append(f.fieldKey()).append("`\n");
            sb.append("  fieldType: ").append(f.fieldType()).append("\n");
            sb.append("  mandatory: ").append(f.isMandatory()).append("\n");
            if (f.helperText() != null && !f.helperText().isBlank()) {
                sb.append("  helperText: ").append(f.helperText()).append("\n");
            }
            if (f.dataObjectName() != null && f.dataObjectFieldName() != null) {
                sb.append("  valuesFrom: ").append(f.dataObjectName())
                    .append(".").append(f.dataObjectFieldName()).append("\n");
            }
            if (f.valueSource() != null) {
                sb.append("  valueSource: ").append(f.valueSource()).append("\n");
                if (f.valueSourceConfig() != null && !f.valueSourceConfig().isBlank()) {
                    sb.append("  valueSourceConfig: ").append(f.valueSourceConfig()).append("\n");
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String replaceFiscalPlaceholders(String prompt) {
        try {
            var configs = fiscalYearConfigService.listConfigs();
            if (!configs.isEmpty()) {
                // Build fiscal year labels: "FY2024, FY2025, FY2026"
                String labels = configs.stream()
                    .map(c -> c.label())
                    .collect(Collectors.joining(", "));
                prompt = prompt.replace("{{fiscalYearLabels}}", labels);

                // Build quarter date ranges from current fiscal year config
                try {
                    var current = fiscalYearConfigService.getCurrentConfig();
                    String quarterDates = String.format(
                        "  - Q1: %s – %s\n  - Q2: %s – %s\n  - Q3: %s – %s\n  - Q4: %s – %s",
                        current.q1StartDate(), current.q1EndDate(),
                        current.q2StartDate(), current.q2EndDate(),
                        current.q3StartDate(), current.q3EndDate(),
                        current.q4StartDate(), current.q4EndDate()
                    );
                    prompt = prompt.replace("{{fiscalQuarterDates}}", quarterDates);
                } catch (Exception e) {
                    log.debug("No current fiscal config, using default calendar-year quarters");
                    prompt = prompt.replace("{{fiscalQuarterDates}}", buildDefaultQuarterDates());
                }
            } else {
                log.debug("No fiscal year configs found, using default calendar-year quarters");
                int year = java.time.Year.now().getValue();
                prompt = prompt.replace("{{fiscalYearLabels}}", "FY" + year);
                prompt = prompt.replace("{{fiscalQuarterDates}}", buildDefaultQuarterDates());
            }
        } catch (Exception e) {
            log.debug("Could not load fiscal configs for AI prompt: {}", e.getMessage());
            int year = java.time.Year.now().getValue();
            prompt = prompt.replace("{{fiscalYearLabels}}", "FY" + year);
            prompt = prompt.replace("{{fiscalQuarterDates}}", buildDefaultQuarterDates());
        }
        return prompt;
    }

    private String buildDefaultQuarterDates() {
        int year = java.time.Year.now().getValue();
        return String.format(
            "  - Q1: %d-01-01 – %d-03-31\n  - Q2: %d-04-01 – %d-06-30\n  - Q3: %d-07-01 – %d-09-30\n  - Q4: %d-10-01 – %d-12-31",
            year, year, year, year, year, year, year, year
        );
    }

    private List<Tool> buildTools() {
        Tool updateBuilder = Tool.builder()
                .name("update_builder")
                .description("Update a field or step in the incentive builder. Use this when the user provides values for form fields.")
                .inputSchema(JsonValue.from(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "action_type", Map.of(
                                        "type", "string",
                                        "enum", List.of(
                                                "UPDATE_BASICS", "UPDATE_SCHEDULE", "UPDATE_AUDIENCE",
                                                "UPDATE_BUDGET", "UPDATE_CRITERIA",
                                                "SET_ACTIVE_STEP", "MARK_STEP_COMPLETE", "SHOW_FORECASTING",
                                                "CONFIRM_CREATE"
                                        ),
                                        "description", "The builder action type to dispatch"
                                ),
                                "payload", Map.of(
                                        "type", "object",
                                        "description", "The action payload. For UPDATE_* actions: partial field updates. For SET_ACTIVE_STEP/MARK_STEP_COMPLETE: { \"step\": \"<step_name>\" }"
                                )
                        ),
                        "required", List.of("action_type", "payload")
                )))
                .build();

        Tool suggestActions = Tool.builder()
                .name("suggest_actions")
                .description("Suggest 2-3 short next actions for the user, displayed as clickable chips.")
                .inputSchema(JsonValue.from(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "suggestions", Map.of(
                                        "type", "array",
                                        "items", Map.of("type", "string"),
                                        "minItems", 2,
                                        "maxItems", 3,
                                        "description", "Short suggestion labels for the user's next actions"
                                )
                        ),
                        "required", List.of("suggestions")
                )))
                .build();

        Tool searchProducts = Tool.builder()
                .name("search_products")
                .description("Search the product catalog by keyword or category. Returns matching products with their SKU codes. Use the SKU as the product ID when dispatching UPDATE_CRITERIA with selectedProducts.")
                .inputSchema(JsonValue.from(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "search", Map.of(
                                        "type", "string",
                                        "description", "Search keyword to match against product name or SKU"
                                ),
                                "category", Map.of(
                                        "type", "string",
                                        "description", "Filter by product category (e.g. Servers, Routers, Switches, Storage, Security, Software & Licensing)"
                                )
                        )
                )))
                .build();

        Tool searchCourses = Tool.builder()
                .name("search_courses")
                .description("Search the LMS course catalog by keyword or category. Returns matching courses with their IDs. Use the course ID when dispatching UPDATE_CRITERIA with trainingCourses.")
                .inputSchema(JsonValue.from(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "search", Map.of(
                                        "type", "string",
                                        "description", "Search keyword to match against course name, description, or provider"
                                ),
                                "category", Map.of(
                                        "type", "string",
                                        "description", "Filter by course category (e.g. Product Knowledge, Sales Methodology, Technical Certification, Customer Success)"
                                )
                        )
                )))
                .build();

        return List.of(updateBuilder, suggestActions, searchProducts, searchCourses);
    }

    private String loadSystemPrompt() {
        try {
            ClassPathResource resource = new ClassPathResource("prompts/incentive-copilot-system.txt");
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to load system prompt", e);
            return "You are an incentive creation assistant. Help users build incentives step by step.";
        }
    }
}
