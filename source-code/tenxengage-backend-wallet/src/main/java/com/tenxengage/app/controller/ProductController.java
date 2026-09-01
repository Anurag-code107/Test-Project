package com.tenxengage.app.controller;

import com.tenxengage.app.audit.Audited;
import com.tenxengage.app.dto.request.CreateProductRequest;
import com.tenxengage.app.dto.response.ProductResponse;
import com.tenxengage.app.dto.response.ProductUploadResponse;
import com.tenxengage.app.security.RequiresPermission;
import com.tenxengage.app.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Products", description = "Product catalog")
@Validated
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    @Operation(summary = "List products", description = "Optionally filter by category or search query")
    @RequiresPermission("action.product.view")
    public ResponseEntity<List<ProductResponse>> getProducts(
            @RequestParam(required = false) @Size(max = 255) String category,
            @RequestParam(required = false) @Size(max = 255) String search) {
        return ResponseEntity.ok(productService.getProducts(category, search));
    }

    @GetMapping("/categories")
    @Operation(summary = "List product categories")
    @RequiresPermission("action.product.view")
    public ResponseEntity<List<String>> getCategories() {
        return ResponseEntity.ok(productService.getCategories());
    }

    @PostMapping
    @Operation(summary = "Create a product", description = "SKU is auto-generated from category")
    @RequiresPermission("action.product.manage")
    @Audited(action = "Created", resourceType = "PRODUCT", resourceName = "#result.body.name", resourceId = "#result.body.id.toString()")
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody CreateProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.createProduct(request));
    }

    @PostMapping("/upload")
    @Operation(summary = "Upload products from CSV", description = "CSV columns: name (required), category (optional)")
    @RequiresPermission("action.product.manage")
    @Audited(action = "Uploaded", resourceType = "PRODUCT", description = "Uploaded products from file")
    public ResponseEntity<ProductUploadResponse> uploadProducts(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(productService.uploadProducts(file));
    }
}
