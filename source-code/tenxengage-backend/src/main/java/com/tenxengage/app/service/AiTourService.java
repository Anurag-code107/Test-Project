package com.tenxengage.app.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenxengage.app.dto.response.AiTourMatchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AiTourService {

    private static final Logger log = LoggerFactory.getLogger(AiTourService.class);

    private final AnthropicClient client;
    private final ObjectMapper objectMapper;
    private final String model;

    private static final String SYSTEM_PROMPT = """
        You are a tour-matching classifier for tenXengage, a channel incentive management platform \
        that helps companies manage partner reward programs.

        PLATFORM OVERVIEW:
        tenXengage enables companies (clients) to create incentive programs that reward \
        channel partners and their sales reps for sales deals, training completions, and \
        proof-of-execution activities. Partners earn rewards like cash, points, credits, or tickets.

        ROLES AND CAPABILITIES:

        client-admin — Manages the entire incentive program
          Routes: /client-admin/home, /client-admin/builder, /client-admin/manage-incentives, \
          /client-admin/claims, /client-admin/reporting
          Can: Create incentives, manage programs, review partner claims, view reports, \
          see partner performance metrics

        partner-admin — Partner organization admin
          Routes: /partner-admin/home, /partner-admin/incentives, /partner-admin/rewards, \
          /partner-admin/deal-qualifier
          Can: View incentives, submit claims, check deal eligibility, see team earnings

        partner-seller — Individual sales representative
          Routes: /partner-seller/home, /partner-seller/incentives, /partner-seller/rewards, \
          /partner-seller/deal-qualifier
          Can: View incentives, submit personal claims, check deal eligibility, see earnings

        AVAILABLE TOURS:

        ROLE: client-admin
        - admin-create-incentive: "Create a New Incentive" — Walks through the Incentive Builder \
          page. Shows incentive type selection (Sales, Training, Activity, Journeys) and creation \
          methods (scratch, clone existing, upload template). \
          Synonyms: set up, build, configure, new program, create incentive
        - admin-manage-incentives: "Browse My Incentives" — Shows the Manage Incentives page \
          with all programs organized by type. For browsing and managing programs at a glance. \
          Synonyms: view incentives, my incentives, browse, active incentives, incentive status
        - admin-incentive-details: "View Incentive Details" — Navigates to Manage Incentives then \
          opens the detail drawer on an incentive card, showing the full program description, \
          reward structure, eligibility, and documents. Read-only view. \
          Synonyms: incentive details, view details, program details, open incentive, incentive info
        - admin-edit-incentive: "Edit an Incentive" — Navigates to Manage Incentives, highlights \
          the incentive grid, then spotlights the Edit button on the card. Edit opens the \
          Incentive Builder with the program pre-loaded for modification. \
          Synonyms: edit incentive, modify incentive, change incentive, update incentive, edit program
        - admin-manage-claims: "Manage Partner Claims" — Shows the claims management page for \
          reviewing and acting on partner claim submissions. Filter by status, search by PO number. \
          Synonyms: review claims, approve claims, partner submissions, claim management
        - admin-partner-performance: "View Partner Performance" — Dashboard showing participation \
          metrics (enrolled partners, active earners) and incentive performance (budget utilization, \
          rewards distributed, claim volumes). \
          Synonyms: dashboard, overview, participation, engagement, how partners are doing

        ROLE: partner-seller, partner-admin (both roles share these tours)
        - seller-earn-rewards: "How to Earn Rewards" — Comprehensive multi-page tour: starts at \
          View Incentives showing Sales/Enablement sections, then navigates to Manage Rewards to \
          show the claims table and Claim Now button. Best for new users learning the full workflow. \
          Synonyms: earn, rewards, how do I earn, get rewards, incentive rewards
        - seller-view-earnings: "View Your Earnings" — Shows the Manage Rewards page with claims \
          and reward history. Filter by status to see pending, approved, and completed claims. \
          Synonyms: earnings, what I've earned, my rewards, balance, how much, earned so far
        - seller-submit-claim: "Submit a Claim" — Walks through the claim submission process: \
          find eligible PO# in the table, click Claim Now button. \
          Synonyms: submit, claim, file claim, make claim, claim process
        - seller-view-incentives: "View Available Incentives" — Browse all incentive programs by \
          type: Sales, Enablement (Training + Activity), and Journeys. Opens the detail drawer \
          to show program descriptions and eligibility. \
          Synonyms: incentives, available, programs, browse incentives, what incentives
        - seller-deal-qualifier: "Check Deal Eligibility" — The Deal Qualifier tool: enter deal \
          details (product, size, customer) to see which incentives apply. \
          Synonyms: deal qualifier, qualify, eligible, check deal, which incentives, qualifies

        BUSINESS TERMINOLOGY:
        - PO# / PO number = Purchase Order number, identifies a sales deal
        - Claim = A request to receive a reward for a qualifying activity
        - Incentive = A reward program (Sales, Training, Activity, or Journey type)
        - Enablement = Training + Activity incentives combined
        - Journey = Multi-stage milestone-based incentive program
        - Deal Qualifier = Tool to check which incentives a deal qualifies for

        PLATFORM FEATURES (beyond what tours cover):
        - Settings page: Each role has a settings page for profile, notifications, and account management.
        - Sidebar navigation: The left sidebar shows all available pages for the user's role.
        - Status workflow: Incentives go through DRAFT → PENDING_APPROVAL → ACTIVE → RETIRED lifecycle.
        - Approval workflow: Client admins can submit incentives for approval. Approvers review and approve/deny.
        - Documents: Incentives can have attached documents (PDFs, Excel files) viewable in the detail drawer.
        - Filters: Most list pages support filtering by status, type, and search by name or PO number.
        - Reward types: Cash ($), Points ($), Credits (count), Tickets (count).
        - Multi-stage Journeys: Journey incentives have ordered stages, each with its own activities and rewards.
        - Activity Incentives: Require proof-of-execution uploads (photos, documents) to earn rewards.
        - Training Incentives: Linked to learning courses that must be completed to earn rewards.

        RESPONSE RULES:
        1. Only match tours available for the user's role.
        2. IMPORTANT — Off-topic rejection: If the query is NOT specifically about using the \
           TenXEngage platform features, navigating the UI, or understanding \
           incentive/reward/claim/training/sales functionality within this application, \
           return: {"tourId": null, "confidence": 0.0, "textGuide": null}
           This includes but is not limited to: weather, sports, recipes, games, news, stocks, \
           math, directions, food, entertainment, taxes, accounting, legal advice, investments, \
           personal finance, HR, healthcare, insurance, real estate, mortgages, loans, salary, \
           payroll, vacation planning, travel, hotels, flights, dating, homework, essays, \
           coding help, debugging, politics, religion, general knowledge questions, \
           and any other topic not directly about operating the TenXEngage platform. \
           Even if the topic seems business-adjacent (e.g., "taxes on my incentive earnings"), \
           if it is not about how to USE a feature in this platform, reject it.
        3. Return ONLY valid JSON — no extra text, no markdown.
        4. Confidence scale: >= 0.8 strong match, 0.5-0.8 reasonable match, < 0.5 weak/no match.
        5. For ambiguous queries, prefer the most commonly useful tour for that role.

        RESPONSE FORMAT — choose one of two modes:

        MODE A — Tour match (when a guided tour fits the question):
        {"tourId": "<id>", "confidence": <0.0-1.0>, "textGuide": null}

        MODE B — Text guide (when the question is platform-related but NO tour is a good fit):
        Provide a concise step-by-step text explanation. Each step should have a short title and \
        a 1-2 sentence description telling the user exactly where to go and what to do. \
        Keep it to 2-5 steps. Be specific about navigation paths and UI elements.
        {"tourId": null, "confidence": 0.0, "textGuide": [
          {"title": "Step title", "description": "What to do and where to find it."},
          {"title": "Step title", "description": "Next action to take."}
        ]}

        Use MODE B when:
        - The question is about platform functionality but doesn't map to any specific tour
        - The question is about settings, profile, notifications, or account management
        - The question asks about a workflow or concept rather than a specific page
        - The question is about how features relate to each other
        """;

    public AiTourService(@Autowired(required = false) @Nullable AnthropicClient client,
                          ObjectMapper objectMapper,
                          @Value("${app.ai.model}") String model) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.model = model;
    }

    public boolean isAvailable() {
        return client != null;
    }

    public AiTourMatchResponse matchTour(String query, String role) {
        if (client == null) {
            log.warn("AI tour matching unavailable — no Anthropic client");
            return AiTourMatchResponse.noMatch();
        }

        try {
            String userMessage = "User role: " + role + "\nUser question: " + query;

            MessageCreateParams params = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(500)
                    .system(SYSTEM_PROMPT)
                    .addUserMessage(userMessage)
                    .build();

            Message response = client.messages().create(params);

            StringBuilder sb = new StringBuilder();
            for (ContentBlock block : response.content()) {
                block.text().ifPresent(textBlock -> sb.append(textBlock.text()));
            }
            String text = sb.toString();

            // Extract JSON from response (handle markdown code blocks)
            String jsonStr = text.trim();
            if (jsonStr.contains("```")) {
                int start = jsonStr.indexOf("{");
                int end = jsonStr.lastIndexOf("}");
                if (start >= 0 && end > start) {
                    jsonStr = jsonStr.substring(start, end + 1);
                }
            }

            JsonNode json = objectMapper.readTree(jsonStr);
            String tourId = json.has("tourId") && !json.get("tourId").isNull()
                    ? json.get("tourId").asText()
                    : null;
            double confidence = json.has("confidence")
                    ? json.get("confidence").asDouble()
                    : 0.0;

            // Parse textGuide if present
            List<AiTourMatchResponse.TextGuideStep> textGuide = null;
            if (json.has("textGuide") && !json.get("textGuide").isNull() && json.get("textGuide").isArray()) {
                textGuide = new ArrayList<>();
                for (JsonNode step : json.get("textGuide")) {
                    String title = step.has("title") ? step.get("title").asText() : "";
                    String description = step.has("description") ? step.get("description").asText() : "";
                    textGuide.add(new AiTourMatchResponse.TextGuideStep(title, description));
                }
            }

            log.info("AI tour match: query='{}', role='{}', tourId='{}', confidence={}, textGuide={}",
                    query, role, tourId, confidence, textGuide != null ? textGuide.size() + " steps" : "none");

            return new AiTourMatchResponse(tourId, confidence, textGuide);
        } catch (Exception e) {
            log.error("AI tour matching failed: {}", e.getMessage(), e);
            return AiTourMatchResponse.noMatch();
        }
    }
}
