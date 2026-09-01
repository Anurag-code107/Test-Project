package com.tenxengage.app.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableCell;
import org.apache.poi.xslf.usermodel.XSLFTableRow;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * Extracts text content from uploaded documents (PDF, PPTX) for AI processing.
 * The extracted text is passed to the AI copilot as conversation context so it
 * can identify incentive-relevant fields (name, budget, products, payout tiers, etc.).
 */
@Service
public class DocumentTextExtractor {

    private static final Logger log = LoggerFactory.getLogger(DocumentTextExtractor.class);

    /** Maximum characters to extract to avoid overwhelming the LLM context window. */
    private static final int MAX_EXTRACT_LENGTH = 30_000;

    /**
     * Extract text from a MultipartFile. Dispatches to the appropriate extractor
     * based on content type / file extension.
     */
    public String extract(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
        String contentType = file.getContentType() != null ? file.getContentType() : "";

        if (contentType.equals("application/pdf") || filename.toLowerCase().endsWith(".pdf")) {
            return extractPdf(file.getInputStream());
        }

        if (contentType.contains("presentationml") || contentType.contains("powerpoint")
                || filename.toLowerCase().endsWith(".pptx")) {
            return extractPptx(file.getInputStream());
        }

        if (contentType.contains("spreadsheetml") || contentType.contains("ms-excel")
                || filename.toLowerCase().endsWith(".xlsx") || filename.toLowerCase().endsWith(".xls")) {
            return extractExcel(file.getInputStream());
        }

        // Fallback: treat as plain text (covers .txt, .csv, .md, etc.)
        String text = new String(file.getBytes());
        return truncate(text);
    }

    /**
     * Returns true if the file type is supported for text extraction.
     */
    public boolean isSupported(MultipartFile file) {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
        String lower = filename.toLowerCase();
        return lower.endsWith(".pdf") || lower.endsWith(".pptx")
                || lower.endsWith(".xlsx") || lower.endsWith(".xls")
                || lower.endsWith(".txt") || lower.endsWith(".csv") || lower.endsWith(".md");
    }

    // ── PDF extraction ──────────────────────────────────────────────────

    private String extractPdf(InputStream in) throws IOException {
        try (PDDocument doc = PDDocument.load(in)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            log.debug("Extracted {} chars from PDF ({} pages)", text.length(), doc.getNumberOfPages());
            return truncate(text);
        }
    }

    // ── PPTX extraction ─────────────────────────────────────────────────

    private String extractPptx(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();

        try (XMLSlideShow pptx = new XMLSlideShow(in)) {
            int slideNum = 0;
            for (XSLFSlide slide : pptx.getSlides()) {
                slideNum++;
                sb.append("\n--- Slide ").append(slideNum).append(" ---\n");

                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        String text = textShape.getText();
                        if (text != null && !text.isBlank()) {
                            sb.append(text.strip()).append("\n");
                        }
                    } else if (shape instanceof XSLFTable table) {
                        extractTable(sb, table);
                    }
                }

                // Also extract from slide notes if present
                if (slide.getNotes() != null) {
                    for (XSLFShape shape : slide.getNotes().getShapes()) {
                        if (shape instanceof XSLFTextShape textShape) {
                            String text = textShape.getText();
                            if (text != null && !text.isBlank()) {
                                sb.append("[Notes] ").append(text.strip()).append("\n");
                            }
                        }
                    }
                }
            }
            log.debug("Extracted {} chars from PPTX ({} slides)", sb.length(), slideNum);
        }

        return truncate(sb.toString());
    }

    // ── Excel extraction ────────────────────────────────────────────────

    private String extractExcel(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();

        try (Workbook workbook = WorkbookFactory.create(in)) {
            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                Sheet sheet = workbook.getSheetAt(s);
                sb.append("\n--- Sheet: ").append(sheet.getSheetName()).append(" ---\n");

                for (Row row : sheet) {
                    boolean first = true;
                    for (Cell cell : row) {
                        if (!first) sb.append(" | ");
                        first = false;
                        sb.append(getCellText(cell));
                    }
                    sb.append("\n");
                }
            }
            log.debug("Extracted {} chars from Excel ({} sheets)", sb.length(), workbook.getNumberOfSheets());
        }

        return truncate(sb.toString());
    }

    private String getCellText(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().strip();
            case NUMERIC -> {
                double val = cell.getNumericCellValue();
                yield val == Math.floor(val) ? String.valueOf((long) val) : String.valueOf(val);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue().strip();
                } catch (Exception e) {
                    try {
                        double val = cell.getNumericCellValue();
                        yield val == Math.floor(val) ? String.valueOf((long) val) : String.valueOf(val);
                    } catch (Exception e2) {
                        yield cell.getCellFormula();
                    }
                }
            }
            default -> "";
        };
    }

    private void extractTable(StringBuilder sb, XSLFTable table) {
        for (XSLFTableRow row : table.getRows()) {
            boolean first = true;
            for (XSLFTableCell cell : row.getCells()) {
                if (!first) sb.append(" | ");
                first = false;
                String text = cell.getText();
                sb.append(text != null ? text.strip() : "");
            }
            sb.append("\n");
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private String truncate(String text) {
        if (text.length() <= MAX_EXTRACT_LENGTH) return text;
        log.info("Truncating extracted text from {} to {} chars", text.length(), MAX_EXTRACT_LENGTH);
        return text.substring(0, MAX_EXTRACT_LENGTH) + "\n\n[... document truncated — remaining content omitted ...]";
    }
}
