package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateBrandingRequest(
    @NotBlank @Size(max = 32) String primary,
    @NotBlank @Size(max = 32) String primaryLight,
    @NotBlank @Size(max = 32) String secondary,
    @NotBlank @Size(max = 32) String accent,
    @NotBlank @Size(max = 32) String success,
    @NotBlank @Size(max = 32) String warning,
    @NotBlank @Size(max = 32) String destructive,
    @NotBlank @Size(max = 32) String background,
    @NotBlank @Size(max = 32) String foreground,
    @NotBlank @Size(max = 32) String muted,
    @NotBlank @Size(max = 32) String mutedForeground,
    @NotBlank @Size(max = 32) String card,
    @NotBlank @Size(max = 32) String cardForeground,
    @NotBlank @Size(max = 32) String border,
    @NotBlank @Size(max = 64) String headingFont,
    @NotBlank @Size(max = 64) String bodyFont
) {}
