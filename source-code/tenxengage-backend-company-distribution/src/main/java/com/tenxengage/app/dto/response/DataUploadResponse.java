package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.DataUpload;
import com.tenxengage.app.entity.enums.DataUploadSource;
import com.tenxengage.app.entity.enums.DataUploadStatus;

import java.time.Instant;
import java.util.UUID;

public record DataUploadResponse(
        UUID id,
        String fileName,
        DataUploadSource source,
        DataUploadStatus status,
        int totalRows,
        int newRows,
        int updatedRows,
        int skippedRows,
        String errorMessage,
        Instant createdAt
) {
    public static DataUploadResponse from(DataUpload upload) {
        return new DataUploadResponse(
                upload.getId(),
                upload.getFileName(),
                upload.getSource(),
                upload.getStatus(),
                upload.getTotalRows(),
                upload.getNewRows(),
                upload.getUpdatedRows(),
                upload.getSkippedRows(),
                upload.getErrorMessage(),
                upload.getCreatedAt()
        );
    }
}
