package com.tenxengage.app.batch.seed;

import com.tenxengage.app.batch.seed.SeedRecords.ProductRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.tenxengage.app.batch.seed.SeedConstants.CATEGORY_ABBREVIATIONS;
import static com.tenxengage.app.batch.seed.SeedConstants.PRODUCT_CATALOG;

/**
 * Creates the 100-product catalog (10 categories x 10 products).
 * Idempotent: loads existing products if they already exist.
 */
@Component
public class ProductSeeder {

    private static final Logger log = LoggerFactory.getLogger(ProductSeeder.class);

    private final JdbcTemplate jdbc;

    public ProductSeeder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Create all products for the client. Returns the product list. */
    public List<ProductRow> createProducts(UUID clientId) {
        List<ProductRow> products = new ArrayList<>();
        Timestamp now = Timestamp.from(Instant.now());
        List<Object[]> batch = new ArrayList<>();

        for (Map.Entry<String, String[]> entry : PRODUCT_CATALOG.entrySet()) {
            String category = entry.getKey();
            String abbr = CATEGORY_ABBREVIATIONS.get(category);
            String[] names = entry.getValue();
            for (int i = 0; i < names.length; i++) {
                UUID id = UUID.randomUUID();
                String sku = String.format("%s-%03d", abbr, i + 1);
                batch.add(new Object[]{id, clientId, sku, names[i], category, now, now});
                products.add(new ProductRow(id, sku, category));
            }
        }

        jdbc.batchUpdate("INSERT INTO products (id, client_id, sku, name, category, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)", batch);

        log.info("Created {} products", products.size());
        return products;
    }

    /** Load existing products from database (for incremental mode). */
    public List<ProductRow> loadExistingProducts(UUID clientId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, sku, category FROM products WHERE client_id = ? ORDER BY sku", clientId);
        List<ProductRow> products = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            products.add(new ProductRow(
                    (UUID) row.get("id"),
                    (String) row.get("sku"),
                    (String) row.get("category")));
        }
        return products;
    }

    /** Get or create products. Returns existing products if present, creates if not. */
    public List<ProductRow> ensureProducts(UUID clientId) {
        List<ProductRow> existing = loadExistingProducts(clientId);
        if (!existing.isEmpty()) {
            log.info("Loaded {} existing products", existing.size());
            return existing;
        }
        return createProducts(clientId);
    }

    /** Build SKU-by-category lookup map. */
    public static Map<String, List<String>> buildSkusByCategory(List<ProductRow> products) {
        Map<String, List<String>> skusByCategory = new HashMap<>();
        for (ProductRow pr : products) {
            skusByCategory.computeIfAbsent(pr.category(), k -> new ArrayList<>()).add(pr.sku());
        }
        return skusByCategory;
    }
}
