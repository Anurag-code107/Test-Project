package com.tenxengage.app.batch.seed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Seeds the global LMS course catalog (lms_courses) and their product category
 * linkages (course_product_mappings). Both tables are referenced by the
 * recommendation scoring and insight services but have no data loader in V1/V2/V3
 * migrations, so the Training recommendation pipeline is a no-op without this.
 *
 * Courses are global (no client_id). Deterministic UUIDs are derived from
 * external_course_id so re-runs are idempotent on top of the partial unique
 * index on external_course_id.
 */
@Component
public class LmsCourseSeeder {

    private static final Logger log = LoggerFactory.getLogger(LmsCourseSeeder.class);

    /** Course category -> list of product categories the course covers.
     *  First entry is the primary category (relevance 1.00); rest are adjacent (0.60). */
    private static final Map<String, List<String>> COURSE_TO_PRODUCT_CATEGORIES = Map.of(
            "Security", List.of("Security"),
            "Cloud", List.of("Cloud Services", "Software & Licensing"),
            "Data Center", List.of("Servers", "Storage"),
            "Networking", List.of("Routers", "Switches"),
            "Wireless", List.of("Wireless"),
            "Storage", List.of("Storage"),
            "Collaboration", List.of("Collaboration"),
            "Services", List.of("Managed Services")
    );

    private static final BigDecimal PRIMARY_RELEVANCE = new BigDecimal("1.00");
    private static final BigDecimal ADJACENT_RELEVANCE = new BigDecimal("0.60");

    private final JdbcTemplate jdbc;

    public LmsCourseSeeder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Insert (or no-op) the 15 seeded training courses and their product category mappings. */
    public void seedLmsCoursesAndMappings() {
        Timestamp now = Timestamp.from(Instant.now());

        List<Object[]> courseBatch = new ArrayList<>(SeedConstants.TRAINING_COURSES.length);
        List<Object[]> mappingBatch = new ArrayList<>();

        for (int i = 0; i < SeedConstants.TRAINING_COURSES.length; i++) {
            String[] c = SeedConstants.TRAINING_COURSES[i];
            String name = c[0];
            String category = c[1];
            String provider = c[2];
            String duration = c[3];
            String level = c[4];

            String externalCourseId = String.format("EXT-COURSE-%03d", i + 1);
            UUID courseId = deterministicCourseId(externalCourseId);
            String slug = name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
            String courseUrl = "https://learning.example.com/course/" + slug;
            String description = buildDescription(name, category, level);

            List<String> productCategories = COURSE_TO_PRODUCT_CATEGORIES.getOrDefault(category, List.of());
            String primaryProductCategory = productCategories.isEmpty() ? category : productCategories.get(0);
            String metadataJson = buildMetadataJson(level, duration, provider, primaryProductCategory, courseUrl);

            courseBatch.add(new Object[]{
                    courseId, name, description, category, externalCourseId, metadataJson, now, now
            });

            for (int p = 0; p < productCategories.size(); p++) {
                String productCategory = productCategories.get(p);
                BigDecimal relevance = (p == 0) ? PRIMARY_RELEVANCE : ADJACENT_RELEVANCE;
                mappingBatch.add(new Object[]{
                        UUID.randomUUID(), courseId, productCategory, relevance, now, now
                });
            }
        }

        int[] courseResult = jdbc.batchUpdate(
                "INSERT INTO lms_courses " +
                "(id, name, description, category, external_course_id, metadata, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?) " +
                "ON CONFLICT (external_course_id) WHERE external_course_id IS NOT NULL DO NOTHING",
                courseBatch);

        int[] mappingResult = jdbc.batchUpdate(
                "INSERT INTO course_product_mappings " +
                "(id, course_id, product_category, relevance_score, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (course_id, product_category) DO NOTHING",
                mappingBatch);

        int coursesInserted = countNonZero(courseResult);
        int mappingsInserted = countNonZero(mappingResult);
        log.info("LMS catalog seed: {} courses (of {}), {} product mappings (of {})",
                coursesInserted, courseBatch.size(), mappingsInserted, mappingBatch.size());
    }

    private static UUID deterministicCourseId(String externalCourseId) {
        return UUID.nameUUIDFromBytes(("lms-course:" + externalCourseId).getBytes(StandardCharsets.UTF_8));
    }

    private static int countNonZero(int[] results) {
        int n = 0;
        for (int r : results) {
            if (r > 0) n++;
        }
        return n;
    }

    private static String buildDescription(String name, String category, String level) {
        return String.format("%s — a %s-level course in %s. Covers foundational concepts, hands-on labs, "
                + "and real-world scenarios to help you advance deals in this category.",
                name, level.toLowerCase(), category);
    }

    private static String buildMetadataJson(String level, String duration, String provider,
                                             String productCategory, String courseUrl) {
        return String.format(
                "{\"level\":\"%s\",\"duration\":\"%s\",\"provider\":\"%s\",\"product_category\":\"%s\",\"course_url\":\"%s\"}",
                escape(level), escape(duration), escape(provider), escape(productCategory), escape(courseUrl));
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
