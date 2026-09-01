package com.tenxengage.app.controller;

import com.tenxengage.app.dto.response.LmsCourseResponse;
import com.tenxengage.app.security.RequiresPermission;
import com.tenxengage.app.service.LmsCourseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/lms-courses")
@Tag(name = "LMS Courses", description = "LMS course catalog")
@Validated
public class LmsCourseController {

    private final LmsCourseService lmsCourseService;

    public LmsCourseController(LmsCourseService lmsCourseService) {
        this.lmsCourseService = lmsCourseService;
    }

    @GetMapping
    @Operation(summary = "List courses", description = "Optionally filter by category or search query")
    @RequiresPermission("action.data.view")
    public ResponseEntity<List<LmsCourseResponse>> getCourses(
            @RequestParam(required = false) @Size(max = 255) String category,
            @RequestParam(required = false) @Size(max = 255) String search) {
        return ResponseEntity.ok(lmsCourseService.getCourses(category, search));
    }

    @GetMapping("/categories")
    @Operation(summary = "List course categories")
    @RequiresPermission("action.data.view")
    public ResponseEntity<List<String>> getCategories() {
        return ResponseEntity.ok(lmsCourseService.getCategories());
    }
}
