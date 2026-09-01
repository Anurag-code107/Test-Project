package com.tenxengage.app.util;

/**
 * Shared CSV utility — promoted from {@code RedemptionAnalyticsService} (was private) so that
 * {@code BalanceBreakageReportService} and any future CSV-producing service can reuse the same
 * formula-injection escaping logic without cross-service calls or duplication.
 *
 * <p>Do not call the private {@code escapeCsv} in {@code RedemptionAnalyticsService} from
 * another service — that would violate service-layer isolation. Use this class instead.</p>
 */
public final class CsvUtil {

    private CsvUtil() {
        // Utility class — not instantiable
    }

    /**
     * Escapes a CSV field value per RFC 4180 and neutralises CSV formula injection
     * (CWE-1236): values whose first character is {@code =}, {@code +}, {@code -},
     * {@code @}, {@code \t}, or {@code \r} are prefixed with a single-quote so spreadsheet
     * applications do not interpret them as formulas. Embedded double-quotes are escaped by
     * doubling them. A {@code null} input returns an empty string.
     *
     * @param value raw field value (may be {@code null})
     * @return escaped CSV field — safe to embed directly in a CSV line without extra quoting
     */
    public static String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        // Neutralise formula-injection prefixes before quoting (CWE-1236).
        if (!value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0) {
            value = "'" + value;
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
