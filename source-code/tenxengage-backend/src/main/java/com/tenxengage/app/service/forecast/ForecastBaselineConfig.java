package com.tenxengage.app.service.forecast;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Fallback defaults per incentive type, used when Claude API is unavailable
 * or when there is insufficient historical data.
 */
@Configuration
@ConfigurationProperties(prefix = "forecast.baseline")
public class ForecastBaselineConfig {

    private Map<String, IncentiveTypeDefaults> defaults = Map.of(
            "SALES", new IncentiveTypeDefaults(
                    new BigDecimal("55"), new BigDecimal("2.5"), new BigDecimal("35"), new BigDecimal("10")),
            "TRAINING", new IncentiveTypeDefaults(
                    new BigDecimal("65"), new BigDecimal("1.8"), new BigDecimal("45"), new BigDecimal("15")),
            "ACTIVITY", new IncentiveTypeDefaults(
                    new BigDecimal("60"), new BigDecimal("2.0"), new BigDecimal("40"), new BigDecimal("8")),
            "JOURNEY", new IncentiveTypeDefaults(
                    new BigDecimal("45"), new BigDecimal("3.0"), new BigDecimal("25"), new BigDecimal("12"))
    );

    public Map<String, IncentiveTypeDefaults> getDefaults() {
        return defaults;
    }

    public void setDefaults(Map<String, IncentiveTypeDefaults> defaults) {
        this.defaults = defaults;
    }

    public IncentiveTypeDefaults getDefaultsForType(String incentiveType) {
        return defaults.getOrDefault(incentiveType,
                new IncentiveTypeDefaults(
                        new BigDecimal("50"), new BigDecimal("2.0"), new BigDecimal("30"), new BigDecimal("10")));
    }

    public static class IncentiveTypeDefaults {
        private BigDecimal utilizationPct;
        private BigDecimal roiMultiplier;
        private BigDecimal participationPct;
        private BigDecimal maxRoiCap;

        public IncentiveTypeDefaults() {}

        public IncentiveTypeDefaults(BigDecimal utilizationPct, BigDecimal roiMultiplier,
                                     BigDecimal participationPct, BigDecimal maxRoiCap) {
            this.utilizationPct = utilizationPct;
            this.roiMultiplier = roiMultiplier;
            this.participationPct = participationPct;
            this.maxRoiCap = maxRoiCap;
        }

        public BigDecimal getUtilizationPct() { return utilizationPct; }
        public void setUtilizationPct(BigDecimal utilizationPct) { this.utilizationPct = utilizationPct; }
        public BigDecimal getRoiMultiplier() { return roiMultiplier; }
        public void setRoiMultiplier(BigDecimal roiMultiplier) { this.roiMultiplier = roiMultiplier; }
        public BigDecimal getParticipationPct() { return participationPct; }
        public void setParticipationPct(BigDecimal participationPct) { this.participationPct = participationPct; }
        public BigDecimal getMaxRoiCap() { return maxRoiCap != null ? maxRoiCap : new BigDecimal("10"); }
        public void setMaxRoiCap(BigDecimal maxRoiCap) { this.maxRoiCap = maxRoiCap; }
    }
}
