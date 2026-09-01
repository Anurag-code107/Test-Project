package com.tenxengage.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "permissions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Permission extends BaseEntity {

    @Column(name = "permission_key", nullable = false, unique = true, length = 100)
    private String permissionKey;

    @Column(name = "display_name", nullable = false, length = 150)
    private String displayName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(name = "permission_type", nullable = false, length = 20)
    private String permissionType;

    /**
     * Permission scope determines which types of roles can be assigned this permission.
     * Values: INTERNAL, EXTERNAL, ALL, PLATFORM
     */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String scope = "ALL";

    @Column(name = "sort_order")
    @Builder.Default
    private int sortOrder = 0;
}
