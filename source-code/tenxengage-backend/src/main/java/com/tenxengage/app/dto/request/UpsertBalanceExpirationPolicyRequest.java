package com.tenxengage.app.dto.request;

import com.tenxengage.app.entity.enums.ExpirationMode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public class UpsertBalanceExpirationPolicyRequest {

    @NotNull
    private Boolean enabled;

    @NotNull
    private ExpirationMode expirationMode;

    private Integer inactivityDays;

    private LocalDate fixedExpiryDate;

    @NotNull
    @Min(1)
    private Integer leadTimeDays;

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public ExpirationMode getExpirationMode() {
        return expirationMode;
    }

    public void setExpirationMode(ExpirationMode expirationMode) {
        this.expirationMode = expirationMode;
    }

    public Integer getInactivityDays() {
        return inactivityDays;
    }

    public void setInactivityDays(Integer inactivityDays) {
        this.inactivityDays = inactivityDays;
    }

    public LocalDate getFixedExpiryDate() {
        return fixedExpiryDate;
    }

    public void setFixedExpiryDate(LocalDate fixedExpiryDate) {
        this.fixedExpiryDate = fixedExpiryDate;
    }

    public Integer getLeadTimeDays() {
        return leadTimeDays;
    }

    public void setLeadTimeDays(Integer leadTimeDays) {
        this.leadTimeDays = leadTimeDays;
    }
}
