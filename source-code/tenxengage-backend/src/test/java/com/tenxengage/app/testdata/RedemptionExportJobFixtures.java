package com.tenxengage.app.testdata;

import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.redemption.ExportFormat;
import com.tenxengage.app.entity.enums.redemption.RedemptionExportStatus;
import com.tenxengage.app.entity.redemption.RedemptionExportJob;

import java.util.Map;
import java.util.UUID;

public final class RedemptionExportJobFixtures {

    private RedemptionExportJobFixtures() {
    }

    public static RedemptionExportJob.RedemptionExportJobBuilder defaultExportJob(UUID clientId, User requestedBy) {
        return RedemptionExportJob.builder()
                .clientId(clientId)
                .requestedBy(requestedBy)
                .status(RedemptionExportStatus.PENDING)
                .format(ExportFormat.CSV)
                .scope("PERSONAL")
                .filterSnapshot(Map.of())
                .deleted(false);
    }

    public static RedemptionExportJob.RedemptionExportJobBuilder withFormat(
            UUID clientId, User requestedBy, ExportFormat format) {
        return defaultExportJob(clientId, requestedBy).format(format);
    }

    public static RedemptionExportJob.RedemptionExportJobBuilder withStatus(
            UUID clientId, User requestedBy, RedemptionExportStatus status) {
        return defaultExportJob(clientId, requestedBy).status(status);
    }

    public static RedemptionExportJob.RedemptionExportJobBuilder completed(
            UUID clientId, User requestedBy, String fileKey) {
        return defaultExportJob(clientId, requestedBy)
                .status(RedemptionExportStatus.COMPLETED)
                .fileKey(fileKey)
                .rowCount(100);
    }
}
