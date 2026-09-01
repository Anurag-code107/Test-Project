package com.tenxengage.app.batch.seed;

import com.tenxengage.app.batch.seed.SeedRecords.FiscalQuarter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Dynamically computes fiscal quarters from SEED_START_DATE through today.
 * Calendar-aligned: Q1=Jan-Mar, Q2=Apr-Jun, Q3=Jul-Sep, Q4=Oct-Dec.
 */
public final class FiscalQuarterCalculator {

    private static final LocalDate SEED_START_DATE = LocalDate.of(2023, 3, 1);

    private FiscalQuarterCalculator() {}

    /** Builds fiscal quarters from FY2023 Q1 through the quarter containing today. */
    public static List<FiscalQuarter> buildFiscalQuarters() {
        return buildFiscalQuartersThrough(LocalDate.now());
    }

    /** Builds fiscal quarters from FY2023 Q1 through the quarter containing endDate. */
    public static List<FiscalQuarter> buildFiscalQuartersThrough(LocalDate endDate) {
        List<FiscalQuarter> quarters = new ArrayList<>();
        int year = SEED_START_DATE.getYear();
        int q = 0; // Start at Q1 of 2023

        while (true) {
            int monthStart = q * 3 + 1;
            LocalDate qStart = LocalDate.of(year, monthStart, 1);
            LocalDate qEnd = qStart.plusMonths(3).minusDays(1);
            String qLabel = "Q" + (q + 1);

            quarters.add(new FiscalQuarter(year, qLabel, qStart, qEnd));

            if (!qEnd.isBefore(endDate)) {
                break;
            }

            q++;
            if (q > 3) {
                q = 0;
                year++;
            }
        }
        return quarters;
    }

    /** Returns only quarters that end after the given date. */
    public static List<FiscalQuarter> quartersAfter(LocalDate afterDate) {
        return buildFiscalQuarters().stream()
                .filter(fq -> fq.endDate().isAfter(afterDate))
                .toList();
    }

    /** Returns the quarter containing the given date. */
    public static FiscalQuarter quarterContaining(LocalDate date) {
        int q = (date.getMonthValue() - 1) / 3;
        int monthStart = q * 3 + 1;
        LocalDate qStart = LocalDate.of(date.getYear(), monthStart, 1);
        LocalDate qEnd = qStart.plusMonths(3).minusDays(1);
        return new FiscalQuarter(date.getYear(), "Q" + (q + 1), qStart, qEnd);
    }

    public static LocalDate getSeedStartDate() {
        return SEED_START_DATE;
    }
}
