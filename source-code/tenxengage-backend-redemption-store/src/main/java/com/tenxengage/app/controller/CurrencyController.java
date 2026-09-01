package com.tenxengage.app.controller;

import com.tenxengage.app.audit.Audited;
import com.tenxengage.app.dto.request.SaveCurrencyRequest;
import com.tenxengage.app.dto.response.CurrencyResponse;
import com.tenxengage.app.security.RequiresPermission;
import com.tenxengage.app.service.CurrencyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/currencies")
@Tag(name = "Currencies", description = "Manage reward currencies per tenant")
@Validated
public class CurrencyController {

    private final CurrencyService currencyService;

    public CurrencyController(CurrencyService currencyService) {
        this.currencyService = currencyService;
    }

    @GetMapping
    @Operation(summary = "List all currencies for current tenant")
    @RequiresPermission("action.currency.view")
    public ResponseEntity<List<CurrencyResponse>> listCurrencies() {
        return ResponseEntity.ok(currencyService.listCurrencies());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a currency by ID")
    @RequiresPermission("action.currency.view")
    public ResponseEntity<CurrencyResponse> getCurrency(@PathVariable UUID id) {
        return ResponseEntity.ok(currencyService.getCurrency(id));
    }

    @PostMapping
    @Operation(summary = "Create a new currency")
    @RequiresPermission("action.currency.manage")
    @Audited(action = "Created", resourceType = "CURRENCY",
             resourceName = "#result.body.code", resourceId = "#result.body.id.toString()")
    public ResponseEntity<CurrencyResponse> createCurrency(
            @Valid @RequestBody SaveCurrencyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(currencyService.createCurrency(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a currency")
    @RequiresPermission("action.currency.manage")
    @Audited(action = "Edited", resourceType = "CURRENCY",
             resourceName = "#result.body.code", resourceId = "#result.body.id.toString()")
    public ResponseEntity<CurrencyResponse> updateCurrency(
            @PathVariable UUID id,
            @Valid @RequestBody SaveCurrencyRequest request) {
        return ResponseEntity.ok(currencyService.updateCurrency(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a currency")
    @RequiresPermission("action.currency.manage")
    @Audited(action = "Deleted", resourceType = "CURRENCY",
             resourceId = "#id.toString()", description = "Deleted currency")
    public ResponseEntity<Void> deleteCurrency(@PathVariable UUID id) {
        currencyService.deleteCurrency(id);
        return ResponseEntity.noContent().build();
    }
}
