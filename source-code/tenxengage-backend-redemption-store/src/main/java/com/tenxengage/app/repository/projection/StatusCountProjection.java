package com.tenxengage.app.repository.projection;

import com.tenxengage.app.entity.enums.RedemptionStatus;

public interface StatusCountProjection {
    RedemptionStatus getStatus();
    Long getCount();
}
