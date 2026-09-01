package com.tenxengage.app.controller;

import com.tenxengage.app.dto.request.AiTourMatchRequest;
import com.tenxengage.app.dto.response.AiTourMatchResponse;
import com.tenxengage.app.service.AiTourService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/ai")
@Tag(name = "AI Tour", description = "AI-powered guided tour matching")
public class AiTourController {

    private final AiTourService aiTourService;

    public AiTourController(AiTourService aiTourService) {
        this.aiTourService = aiTourService;
    }

    @PostMapping("/tour-match")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Match query to tour", description = "Uses AI to match a natural language query to a guided tour")
    public ResponseEntity<AiTourMatchResponse> matchTour(@Valid @RequestBody AiTourMatchRequest request) {
        if (!aiTourService.isAvailable()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "AI service is not available.");
        }

        AiTourMatchResponse response = aiTourService.matchTour(request.query(), request.role());
        return ResponseEntity.ok(response);
    }
}
