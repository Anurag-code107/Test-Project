package com.tenxengage.app.dto.response;

import java.util.List;

public record BuilderConfigResponse(
    String incentiveType,
    List<BuilderSectionConfigResponse> sections
) {}
