package com.tenxengage.app.controller;

import com.tenxengage.app.dto.request.CreateActivityCategoryRequest;
import com.tenxengage.app.dto.request.UpdateActivityCategoryRequest;
import com.tenxengage.app.dto.response.ActivityCategoryResponse;
import com.tenxengage.app.security.RequiresPermission;
import com.tenxengage.app.service.ActivityCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/v1/activity-categories")
@Tag(name = "Activity Categories", description = "Activity category management")
public class ActivityCategoryController {

    private final ActivityCategoryService activityCategoryService;

    public ActivityCategoryController(ActivityCategoryService activityCategoryService) {
        this.activityCategoryService = activityCategoryService;
    }

    @GetMapping
    @Operation(summary = "List activity categories for current tenant")
    @RequiresPermission("action.incentive.view")
    public ResponseEntity<List<ActivityCategoryResponse>> getCategories() {
        return ResponseEntity.ok(activityCategoryService.getCategories());
    }

    @PostMapping
    @Operation(summary = "Create an activity category")
    @RequiresPermission("action.builder.manage")
    public ResponseEntity<ActivityCategoryResponse> createCategory(
            @Valid @RequestBody CreateActivityCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(activityCategoryService.createCategory(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an activity category")
    @RequiresPermission("action.builder.manage")
    public ResponseEntity<ActivityCategoryResponse> updateCategory(
            @PathVariable UUID id, @Valid @RequestBody UpdateActivityCategoryRequest request) {
        return ResponseEntity.ok(activityCategoryService.updateCategory(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an activity category")
    @RequiresPermission("action.builder.manage")
    public ResponseEntity<Void> deleteCategory(@PathVariable UUID id) {
        activityCategoryService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }
}
