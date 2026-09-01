package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.Product;

import java.util.UUID;

public record ProductResponse(
    UUID id,
    String sku,
    String name,
    String category
) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(
            product.getId(),
            product.getSku(),
            product.getName(),
            product.getCategory()
        );
    }
}
