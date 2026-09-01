package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.LocationBudgetAllocation;
import com.tenxengage.app.entity.LocationLevel;
import com.tenxengage.app.entity.LocationValue;

import java.util.UUID;

/**
 * One per-LocationValue budget allocation, denormalized so the client can
 * render the allocation tree without a separate hierarchy lookup.
 */
public record LocationAllocationResponse(
    UUID locationValueId,
    UUID locationLevelId,
    String locationValueName,
    String levelName,
    String amount
) {

    public static LocationAllocationResponse from(LocationBudgetAllocation alloc) {
        if (alloc == null) return null;
        LocationValue lv = alloc.getLocationValue();
        LocationLevel level = lv != null ? lv.getLevel() : null;
        return new LocationAllocationResponse(
            lv != null ? lv.getId() : null,
            level != null ? level.getId() : null,
            lv != null ? lv.getName() : null,
            level != null ? level.getName() : null,
            alloc.getAmount() != null ? alloc.getAmount().toPlainString() : null
        );
    }
}
