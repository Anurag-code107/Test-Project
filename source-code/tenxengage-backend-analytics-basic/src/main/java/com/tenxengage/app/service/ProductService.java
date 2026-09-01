package com.tenxengage.app.service;

import com.tenxengage.app.dto.request.CreateProductRequest;
import com.tenxengage.app.dto.response.ProductResponse;
import com.tenxengage.app.dto.response.ProductUploadResponse;
import com.tenxengage.app.entity.Product;
import com.tenxengage.app.repository.ProductRepository;
import com.tenxengage.app.security.TenantValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private static final Map<String, String> CATEGORY_ABBREVIATIONS = Map.ofEntries(
            Map.entry("Servers", "srv"),
            Map.entry("Routers", "rtr"),
            Map.entry("Switches", "swt"),
            Map.entry("Storage", "str"),
            Map.entry("Security", "sec"),
            Map.entry("Software & Licensing", "sw"),
            Map.entry("Wireless", "wls"),
            Map.entry("Collaboration", "col"),
            Map.entry("Cloud Services", "cld"),
            Map.entry("Managed Services", "mgs"),
            Map.entry("None", "none")
    );

    private final ProductRepository productRepository;
    private final TenantValidator tenantValidator;

    public ProductService(ProductRepository productRepository, TenantValidator tenantValidator) {
        this.productRepository = productRepository;
        this.tenantValidator = tenantValidator;
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getProducts(String category, String search) {
        UUID clientId = tenantValidator.getCurrentClientId();

        if (category != null && search != null) {
            return productRepository.searchByClientIdAndCategory(clientId, category, search).stream()
                    .map(ProductResponse::from)
                    .toList();
        }
        if (search != null) {
            return productRepository.searchByClientId(clientId, search).stream()
                    .map(ProductResponse::from)
                    .toList();
        }
        if (category != null) {
            return productRepository.findByClientIdAndCategoryOrderByName(clientId, category).stream()
                    .map(ProductResponse::from)
                    .toList();
        }
        return productRepository.findByClientIdOrderByCategoryAscNameAsc(clientId).stream()
                .map(ProductResponse::from)
                .toList();
    }

    @Cacheable(value = "productCategories", key = "T(String).valueOf(#root.target.currentClientId())")
    @Transactional(readOnly = true)
    public List<String> getCategories() {
        UUID clientId = tenantValidator.getCurrentClientId();
        return productRepository.findDistinctCategoriesByClientId(clientId);
    }

    public UUID currentClientId() {
        return tenantValidator.getCurrentClientId();
    }

    @CacheEvict(value = "productCategories", allEntries = true)
    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        UUID clientId = tenantValidator.getCurrentClientId();
        String category = (request.category() != null && !request.category().isBlank())
                ? request.category().trim()
                : "None";

        if (productRepository.existsByClientIdAndName(clientId, request.name().trim())) {
            throw new IllegalArgumentException("A product with name '" + request.name().trim()
                    + "' already exists");
        }

        String sku = generateSku(clientId, category);

        Product product = Product.builder()
                .clientId(clientId)
                .sku(sku)
                .name(request.name().trim())
                .category(category)
                .build();

        product = productRepository.save(product);
        log.info("Created product '{}' with sku '{}' for client {}", product.getName(), sku, clientId);
        return ProductResponse.from(product);
    }

    @CacheEvict(value = "productCategories", allEntries = true)
    @Transactional
    public ProductUploadResponse uploadProducts(MultipartFile file) {
        UUID clientId = tenantValidator.getCurrentClientId();
        Set<String> existingNames = productRepository.findAllNamesByClientId(clientId);

        List<Product> newProducts = new ArrayList<>();
        int skipped = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return new ProductUploadResponse(0, 0, List.of());
            }

            // Parse header to find column indices
            String[] headers = headerLine.split(",");
            int nameIdx = -1;
            int categoryIdx = -1;
            for (int i = 0; i < headers.length; i++) {
                String h = headers[i].trim().toLowerCase();
                if (h.equals("name")) nameIdx = i;
                else if (h.equals("category")) categoryIdx = i;
            }
            if (nameIdx == -1) {
                throw new IllegalArgumentException("CSV must have a 'name' column");
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] cols = line.split(",", -1);
                String name = cols.length > nameIdx ? cols[nameIdx].trim() : "";
                if (name.isEmpty()) continue;

                if (existingNames.contains(name)) {
                    skipped++;
                    continue;
                }

                String category = (categoryIdx >= 0 && cols.length > categoryIdx && !cols[categoryIdx].trim().isEmpty())
                        ? cols[categoryIdx].trim()
                        : "None";

                String sku = generateSku(clientId, category);

                Product product = Product.builder()
                        .clientId(clientId)
                        .sku(sku)
                        .name(name)
                        .category(category)
                        .build();

                newProducts.add(productRepository.save(product));
                existingNames.add(name);
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse CSV file: " + e.getMessage(), e);
        }

        log.info("Product upload for client {}: {} added, {} skipped", clientId, newProducts.size(), skipped);
        List<ProductResponse> responses = newProducts.stream().map(ProductResponse::from).toList();
        return new ProductUploadResponse(newProducts.size(), skipped, responses);
    }

    @Transactional
    public int discoverProductsFromSalesData(UUID clientId) {
        // In the current simulated state, we don't have actual distinct product names
        // from PO lines. This method is a placeholder that returns 0 discovered.
        // When real sales data parsing is implemented, it would query distinct product
        // names from purchase_order_lines and create entries for any not already in the catalog.
        log.info("Product discovery for client {} — no new products found (simulated)", clientId);
        return 0;
    }

    private String generateSku(UUID clientId, String category) {
        String abbr = CATEGORY_ABBREVIATIONS.getOrDefault(category,
                category.length() >= 3 ? category.substring(0, 3).toLowerCase() : category.toLowerCase());
        long count = productRepository.countByClientIdAndCategory(clientId, category);
        return String.format("%s-%03d", abbr, count + 1);
    }
}
