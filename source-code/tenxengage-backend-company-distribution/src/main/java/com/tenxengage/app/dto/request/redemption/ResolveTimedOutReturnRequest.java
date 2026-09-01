package com.tenxengage.app.dto.request.redemption;

import com.tenxengage.app.entity.enums.ReturnResolution;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for admin manual resolution of a RETURN_TIMED_OUT return.
 * {@code resolution} drives the path: CONFIRM credits the wallet, REJECT does not.
 * {@code notes} is optional admin commentary (max 1000 chars).
 */
public record ResolveTimedOutReturnRequest(
        @NotNull ReturnResolution resolution,
        @Size(max = 1000) String notes
) {
}
