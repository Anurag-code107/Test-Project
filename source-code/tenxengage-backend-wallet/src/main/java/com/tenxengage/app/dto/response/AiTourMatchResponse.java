package com.tenxengage.app.dto.response;

import java.util.List;

public record AiTourMatchResponse(
    String tourId,
    double confidence,
    List<TextGuideStep> textGuide
) {
    public record TextGuideStep(
        String title,
        String description
    ) {}

    public static AiTourMatchResponse noMatch() {
        return new AiTourMatchResponse(null, 0.0, null);
    }
}
