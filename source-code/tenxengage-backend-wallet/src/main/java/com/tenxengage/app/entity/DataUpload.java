package com.tenxengage.app.entity;

import com.tenxengage.app.entity.enums.DataUploadSource;
import com.tenxengage.app.entity.enums.DataUploadStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Filter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "data_uploads")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Filter(name = "tenantFilter", condition = "client_id = :clientId")
public class DataUpload extends BaseEntity implements TenantAware {

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "data_object_id", nullable = false)
    private DataObject dataObject;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private DataUploadSource source = DataUploadSource.MANUAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private DataUploadStatus status = DataUploadStatus.PROCESSING;

    @Column(name = "total_rows")
    @Builder.Default
    private int totalRows = 0;

    @Column(name = "new_rows")
    @Builder.Default
    private int newRows = 0;

    @Column(name = "updated_rows")
    @Builder.Default
    private int updatedRows = 0;

    @Column(name = "skipped_rows")
    @Builder.Default
    private int skippedRows = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
}
