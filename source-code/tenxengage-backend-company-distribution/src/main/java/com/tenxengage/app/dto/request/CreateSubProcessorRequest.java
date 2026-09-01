package com.tenxengage.app.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSubProcessorRequest(
    @NotBlank(message = "Name is required")
    @Size(max = 255)
    String name,

    @NotBlank(message = "Purpose is required")
    @Size(max = 500)
    String purpose,

    @NotBlank(message = "Data processed is required")
    @Size(max = 500)
    String dataProcessed,

    @NotBlank(message = "Location is required")
    @Size(max = 100)
    String location,

    @NotBlank(message = "DPA status is required")
    String dpaStatus,

    @NotBlank(message = "SCC status is required")
    String sccStatus
) {}
