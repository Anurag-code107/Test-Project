package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.ClientBranding;

public record BrandingResponse(
    String primary,
    String primaryLight,
    String secondary,
    String accent,
    String success,
    String warning,
    String destructive,
    String background,
    String foreground,
    String muted,
    String mutedForeground,
    String card,
    String cardForeground,
    String border,
    String headingFont,
    String bodyFont,
    String logoUrl
) {

    public static BrandingResponse from(ClientBranding entity) {
        return new BrandingResponse(
            entity.getPrimaryHsl(),
            entity.getPrimaryLightHsl(),
            entity.getSecondaryHsl(),
            entity.getAccentHsl(),
            entity.getSuccessHsl(),
            entity.getWarningHsl(),
            entity.getDestructiveHsl(),
            entity.getBackgroundHsl(),
            entity.getForegroundHsl(),
            entity.getMutedHsl(),
            entity.getMutedForegroundHsl(),
            entity.getCardHsl(),
            entity.getCardForegroundHsl(),
            entity.getBorderHsl(),
            entity.getHeadingFont(),
            entity.getBodyFont(),
            entity.getLogoUrl()
        );
    }

    public static BrandingResponse defaults() {
        return new BrandingResponse(
            "221 94% 56%",
            "217 91% 60%",
            "210 40% 96%",
            "210 40% 96%",
            "142 76% 36%",
            "38 92% 50%",
            "0 84% 60%",
            "0 0% 100%",
            "222 47% 11%",
            "210 40% 96%",
            "215 16% 47%",
            "0 0% 100%",
            "222 47% 11%",
            "214 32% 91%",
            "Inter",
            "Inter",
            null
        );
    }
}
