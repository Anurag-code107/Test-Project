package com.tenxengage.app.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.Base64PdfSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.DocumentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlockParam;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenxengage.app.dto.response.InvoiceExtractionResponse;
import com.tenxengage.app.dto.response.InvoiceExtractionResponse.ExtractedLineItem;
import com.tenxengage.app.dto.response.InvoiceExtractionResponse.SkuMapping;
import com.tenxengage.app.security.TenantValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class InvoiceExtractionService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceExtractionService.class);
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB

    private final AnthropicClient client;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final TenantValidator tenantValidator;
    private final String model;
    private final String systemPrompt;

    public InvoiceExtractionService(@Autowired(required = false) @Nullable AnthropicClient client,
                                     ObjectMapper objectMapper,
                                     JdbcTemplate jdbcTemplate,
                                     TenantValidator tenantValidator,
                                     @Value("${app.ai.model}") String model) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.tenantValidator = tenantValidator;
        this.model = model;
        this.systemPrompt = loadSystemPrompt();
    }

    public InvoiceExtractionResponse extractFromInvoice(MultipartFile file) {
        validateFile(file);

        if (client == null) {
            throw new IllegalStateException("AI service is not available for invoice extraction");
        }

        try {
            byte[] fileBytes = file.getBytes();
            String base64Data = Base64.getEncoder().encodeToString(fileBytes);

            // Build the Claude API request with PDF document
            ContentBlockParam documentBlock = ContentBlockParam.ofDocument(
                    DocumentBlockParam.builder()
                            .source(DocumentBlockParam.Source.ofBase64(
                                    Base64PdfSource.builder()
                                            .data(base64Data)
                                            .build()))
                            .build());

            ContentBlockParam textBlock = ContentBlockParam.ofText(
                    TextBlockParam.builder()
                            .text("Extract the structured data from this invoice document.")
                            .build());

            MessageCreateParams params = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(1024L)
                    .system(systemPrompt)
                    .addUserMessageOfBlockParams(List.of(documentBlock, textBlock))
                    .build();

            Message response = client.messages().create(params);

            String responseText = response.content().stream()
                    .filter(block -> block.isText())
                    .map(block -> block.asText().text())
                    .reduce("", String::concat);

            log.info("Invoice extraction completed, response length: {}", responseText.length());

            return parseExtractionResponse(responseText);

        } catch (IOException e) {
            log.error("Failed to read invoice file: {}", e.getMessage());
            throw new IllegalStateException("Failed to process invoice file", e);
        }
    }

    private InvoiceExtractionResponse parseExtractionResponse(String responseText) {
        try {
            // Strip any markdown code fences if present
            String json = responseText.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("^```\\w*\\n?", "").replaceAll("\\n?```$", "").trim();
            }

            JsonNode root = objectMapper.readTree(json);

            List<ExtractedLineItem> lineItems = new ArrayList<>();
            if (root.has("lineItems") && root.get("lineItems").isArray()) {
                for (JsonNode item : root.get("lineItems")) {
                    lineItems.add(new ExtractedLineItem(
                            getTextOrNull(item, "productName"),
                            item.has("quantity") ? item.get("quantity").asInt(1) : 1,
                            getDecimalOrNull(item, "unitPrice"),
                            getDecimalOrNull(item, "lineTotal")
                    ));
                }
            }

            BigDecimal totalValue = getDecimalOrNull(root, "totalValue");
            String customerName = getTextOrNull(root, "customerName");
            String customerSegment = getTextOrNull(root, "customerSegment");
            String invoiceDate = getTextOrNull(root, "invoiceDate");

            // Fuzzy match extracted product names to real SKUs
            UUID clientId = tenantValidator.getCurrentClientId();
            List<SkuMapping> skuMappings = fuzzyMatchSkus(clientId, lineItems);

            return new InvoiceExtractionResponse(
                    lineItems, totalValue, customerName, customerSegment, invoiceDate, skuMappings);

        } catch (Exception e) {
            log.error("Failed to parse Claude extraction response: {}", e.getMessage());
            throw new IllegalStateException("Failed to parse invoice extraction results", e);
        }
    }

    private List<SkuMapping> fuzzyMatchSkus(UUID clientId, List<ExtractedLineItem> lineItems) {
        // Load all products for this client
        List<Map<String, Object>> products = jdbcTemplate.queryForList(
                "SELECT sku, name, category FROM products WHERE client_id = ?",
                clientId);

        List<SkuMapping> mappings = new ArrayList<>();

        for (ExtractedLineItem item : lineItems) {
            if (item.productName() == null) continue;

            String extractedLower = item.productName().toLowerCase();
            String bestSku = null;
            String bestName = null;
            double bestConfidence = 0.0;

            for (Map<String, Object> product : products) {
                String sku = (String) product.get("sku");
                String name = (String) product.get("name");
                if (name == null) continue;

                String nameLower = name.toLowerCase();

                // Exact match
                if (nameLower.equals(extractedLower)) {
                    bestSku = sku;
                    bestName = name;
                    bestConfidence = 1.0;
                    break;
                }

                // Containment match
                if (nameLower.contains(extractedLower) || extractedLower.contains(nameLower)) {
                    double confidence = 0.8;
                    if (confidence > bestConfidence) {
                        bestSku = sku;
                        bestName = name;
                        bestConfidence = confidence;
                    }
                    continue;
                }

                // Word overlap match
                String[] extractedWords = extractedLower.split("\\s+");
                String[] nameWords = nameLower.split("\\s+");
                int overlap = 0;
                for (String ew : extractedWords) {
                    for (String nw : nameWords) {
                        if (ew.equals(nw) && ew.length() > 2) {
                            overlap++;
                            break;
                        }
                    }
                }
                int maxWords = Math.max(extractedWords.length, nameWords.length);
                double confidence = maxWords > 0 ? (double) overlap / maxWords * 0.7 : 0;
                if (confidence > bestConfidence) {
                    bestSku = sku;
                    bestName = name;
                    bestConfidence = confidence;
                }
            }

            if (bestConfidence >= 0.4) {
                mappings.add(new SkuMapping(item.productName(), bestSku, bestName,
                        Math.round(bestConfidence * 100.0) / 100.0));
            } else {
                mappings.add(new SkuMapping(item.productName(), null, null, 0.0));
            }
        }

        return mappings;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds 10 MB limit");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("Only PDF files are supported");
        }
    }

    private String getTextOrNull(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull()
                ? node.get(field).asText() : null;
    }

    private BigDecimal getDecimalOrNull(JsonNode node, String field) {
        if (node.has(field) && !node.get(field).isNull()) {
            try {
                return new BigDecimal(node.get(field).asText());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private String loadSystemPrompt() {
        try {
            ClassPathResource resource = new ClassPathResource(
                    "prompts/deal-qualifier-extraction-system.txt");
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Could not load extraction system prompt: {}", e.getMessage());
            return "Extract structured data from this invoice as JSON.";
        }
    }
}
