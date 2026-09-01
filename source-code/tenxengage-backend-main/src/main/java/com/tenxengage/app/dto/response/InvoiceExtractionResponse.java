package com.tenxengage.app.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record InvoiceExtractionResponse(
        List<ExtractedLineItem> lineItems,
        BigDecimal totalValue,
        String customerName,
        String customerSegment,
        String invoiceDate,
        List<SkuMapping> skuMappings
) {
    public record ExtractedLineItem(
            String productName,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal
    ) {}

    public record SkuMapping(
            String extractedName,
            String matchedSku,
            String matchedProductName,
            double confidence
    ) {}
}
