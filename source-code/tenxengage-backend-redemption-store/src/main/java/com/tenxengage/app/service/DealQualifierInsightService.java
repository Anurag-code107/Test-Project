package com.tenxengage.app.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenxengage.app.dto.request.DealQualifierRequest;
import com.tenxengage.app.dto.response.QualifiedIncentiveResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.lang.Nullable;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DealQualifierInsightService {

    private static final Logger log = LoggerFactory.getLogger(DealQualifierInsightService.class);

    private final AnthropicClient client;
    private final ObjectMapper objectMapper;
    private final String model;
    private final String systemPrompt;

    public DealQualifierInsightService(@Autowired(required = false) @Nullable AnthropicClient client,
                                        ObjectMapper objectMapper,
                                        @Value("${app.ai.model}") String model) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.model = model;
        this.systemPrompt = loadSystemPrompt();
    }

    public void streamInsight(SseEmitter emitter, DealQualifierRequest dealInput,
                               String partnerRegion,
                               QualifiedIncentiveResult matchResult,
                               List<QualifiedIncentiveResult> otherResults) {
        SecurityContext securityContext = SecurityContextHolder.getContext();
        Thread.startVirtualThread(() -> {
            SecurityContextHolder.setContext(securityContext);
            try {
                String insightText = generateInsight(dealInput, partnerRegion, matchResult, otherResults);

                emitter.send(SseEmitter.event()
                        .name("insight")
                        .data(insightText));
                emitter.send(SseEmitter.event().name("done").data(""));
                emitter.complete();
            } catch (Exception e) {
                log.error("Error streaming deal qualifier insight: {}", e.getMessage(), e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("Failed to generate insight"));
                    emitter.complete();
                } catch (IOException ioe) {
                    emitter.completeWithError(ioe);
                }
            } finally {
                SecurityContextHolder.clearContext();
            }
        });
    }

    private String generateInsight(DealQualifierRequest dealInput,
                                    String partnerRegion,
                                    QualifiedIncentiveResult matchResult,
                                    List<QualifiedIncentiveResult> otherResults) {
        Map<String, Object> context = buildContext(dealInput, partnerRegion, matchResult, otherResults);

        if (client == null) {
            log.warn("Claude API unavailable — using fallback insight");
            return buildFallbackInsight(matchResult);
        }

        try {
            String contextJson = objectMapper.writeValueAsString(context);

            MessageCreateParams params = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(512L)
                    .system(systemPrompt)
                    .addMessage(MessageParam.builder()
                            .role(MessageParam.Role.USER)
                            .content(MessageParam.Content.ofString(contextJson))
                            .build())
                    .build();

            Message response = client.messages().create(params);

            String responseText = response.content().stream()
                    .filter(block -> block.isText())
                    .map(block -> block.asText().text())
                    .reduce("", String::concat);

            return responseText.isEmpty()
                    ? buildFallbackInsight(matchResult)
                    : responseText;

        } catch (Exception e) {
            log.warn("Claude deal qualifier insight failed, using fallback: {}", e.getMessage());
            return buildFallbackInsight(matchResult);
        }
    }

    private Map<String, Object> buildContext(DealQualifierRequest dealInput,
                                              String partnerRegion,
                                              QualifiedIncentiveResult matchResult,
                                              List<QualifiedIncentiveResult> otherResults) {
        Map<String, Object> context = new HashMap<>();

        // Deal input. The partner's region is supplied by the caller (derived from
        // their location assignments by DealQualifierService), not pulled from the
        // request — partner sellers can only qualify deals in their own region, and
        // the region selector was removed from the form for that reason.
        Map<String, Object> deal = new HashMap<>();
        deal.put("dealValue", dealInput.dealValue());
        deal.put("productSkus", dealInput.productSkus());
        deal.put("customerSegment", dealInput.customerSegment());
        deal.put("region", partnerRegion);
        deal.put("closeDate", dealInput.closeDate() != null ? dealInput.closeDate().toString() : null);
        context.put("dealInput", deal);

        // Match result
        Map<String, Object> match = new HashMap<>();
        match.put("incentiveName", matchResult.incentiveName());
        match.put("matchPercentage", matchResult.matchPercentage());
        match.put("estimatedReward", matchResult.estimatedReward());
        match.put("rewardCurrency", matchResult.rewardCurrency());
        match.put("startDate", matchResult.startDate() != null ? matchResult.startDate().toString() : null);
        match.put("endDate", matchResult.endDate() != null ? matchResult.endDate().toString() : null);
        match.put("metCriteria", matchResult.metCriteria());
        match.put("unmetCriteria", matchResult.unmetCriteria());
        context.put("matchResult", match);

        // Payout breakdown
        if (matchResult.payoutBreakdown() != null) {
            context.put("payoutBreakdown", matchResult.payoutBreakdown());
        }

        // Other qualifying incentives summary
        if (otherResults != null && !otherResults.isEmpty()) {
            List<Map<String, Object>> others = otherResults.stream()
                    .filter(r -> !r.incentiveId().equals(matchResult.incentiveId()))
                    .limit(5)
                    .map(r -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("incentiveName", r.incentiveName());
                        m.put("matchPercentage", r.matchPercentage());
                        m.put("estimatedReward", r.estimatedReward());
                        m.put("rewardCurrency", r.rewardCurrency());
                        return m;
                    })
                    .toList();
            context.put("otherQualifyingIncentives", others);
        }

        // Unmet product SKUs (for product addition suggestions)
        List<String> unmetProductSkus = matchResult.unmetCriteria().stream()
                .filter(c -> "PRODUCTS".equals(c.ruleType()) && c.hint() != null)
                .map(c -> c.hint())
                .toList();
        if (!unmetProductSkus.isEmpty()) {
            context.put("unmetProductHints", unmetProductSkus);
        }

        return context;
    }

    private String buildFallbackInsight(QualifiedIncentiveResult matchResult) {
        StringBuilder sb = new StringBuilder();

        if (matchResult.payoutBreakdown() != null) {
            var breakdown = matchResult.payoutBreakdown();
            if (breakdown.gapToNextTier() != null && breakdown.nextTierPayoutValue() != null) {
                sb.append("- Increasing the deal value by $")
                        .append(breakdown.gapToNextTier().toPlainString())
                        .append(" would move you to the next payout tier\n");
            }
            if (breakdown.currentTierPayoutValue() != null) {
                sb.append("- Current payout tier: ")
                        .append(breakdown.currentTierPayoutValue().toPlainString());
                if ("PERCENTAGE".equals(breakdown.currentTierPayoutType())) {
                    sb.append("% of deal value");
                }
                sb.append("\n");
            }
        }

        if (matchResult.estimatedReward() != null) {
            sb.append("- Estimated reward: $")
                    .append(matchResult.estimatedReward().toPlainString())
                    .append(" (").append(matchResult.rewardCurrency()).append(")\n");
        }

        if (!matchResult.unmetCriteria().isEmpty()) {
            sb.append("- ").append(matchResult.unmetCriteria().size())
                    .append(" requirement(s) not yet met — review the unmet criteria for details\n");
        }

        return sb.isEmpty()
                ? "- This deal has a " + matchResult.matchPercentage() + "% match with this incentive"
                : sb.toString().trim();
    }

    private String loadSystemPrompt() {
        try {
            ClassPathResource resource = new ClassPathResource(
                    "prompts/deal-qualifier-insight-system.txt");
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Could not load deal qualifier insight prompt: {}", e.getMessage());
            return "Generate 3-5 actionable bullet points for deal optimization.";
        }
    }
}
