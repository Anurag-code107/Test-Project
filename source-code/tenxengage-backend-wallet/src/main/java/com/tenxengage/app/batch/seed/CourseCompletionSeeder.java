package com.tenxengage.app.batch.seed;

import com.tenxengage.app.batch.seed.SeedRecords.CourseCompletionRecord;
import com.tenxengage.app.batch.seed.SeedRecords.SellerRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static com.tenxengage.app.batch.seed.SeedConstants.BATCH_SIZE;

/**
 * Pre-computes and persists course completion records for the seed data pipeline.
 *
 * Course completions must be scheduled BEFORE PO generation so training boosts
 * can influence product selection (temporal causation). The records are persisted
 * to the DB after POs are generated.
 *
 * Learner profiles:
 *   - Heavy learners (20%): 20-25 courses
 *   - Average learners (50%): 12-18 courses
 *   - Light learners (30%): 5-10 courses
 *
 * Source distribution: 70% ORGANIC, 30% INCENTIVE-driven.
 */
@Component
public class CourseCompletionSeeder {

    private static final Logger log = LoggerFactory.getLogger(CourseCompletionSeeder.class);

    // 36-month range: March 2023 through March 2026
    private static final LocalDate SEED_START_DATE = FiscalQuarterCalculator.getSeedStartDate();

    private final JdbcTemplate jdbc;

    public CourseCompletionSeeder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Pre-computes the full course completion schedule in memory. This must run BEFORE
     * PO generation so training boosts can influence product selection (temporal causation).
     * The records are persisted to the DB after POs are generated.
     *
     * Learner profiles:
     *   - Heavy learners (20%): 20-25 courses
     *   - Average learners (50%): 12-18 courses
     *   - Light learners (30%): 5-10 courses
     *
     * Source distribution: 70% ORGANIC, 30% INCENTIVE-driven.
     */
    public List<CourseCompletionRecord> preComputeCourseCompletions(
            UUID clientId, List<SellerRef> allSellers,
            Map<UUID, Timestamp> userCreationDates, Random random) {

        // Load all LMS courses with their categories
        List<Map<String, Object>> courseRows = jdbc.queryForList(
                "SELECT id, category FROM lms_courses WHERE category IS NOT NULL");
        if (courseRows.isEmpty()) {
            log.warn("No LMS courses with category found — skipping course completions");
            return List.of();
        }

        List<UUID> courseIds = new ArrayList<>();
        Map<UUID, String> courseCategoryMap = new HashMap<>();
        for (Map<String, Object> row : courseRows) {
            UUID cId = (UUID) row.get("id");
            String cat = (String) row.get("category");
            courseIds.add(cId);
            courseCategoryMap.put(cId, cat);
        }
        log.info("Loaded {} LMS courses with categories for completion scheduling",
                courseIds.size());

        // Load training incentive date ranges for INCENTIVE-sourced completions
        List<Map<String, Object>> trainingIncentives = jdbc.queryForList(
                "SELECT i.id, i.start_date, i.end_date FROM incentives i " +
                        "WHERE i.client_id = ? AND i.incentive_type = 'TRAINING' AND i.deleted = false",
                clientId);

        long seedStartEpoch = SEED_START_DATE.toEpochDay();
        long seedEndEpoch = LocalDate.now().toEpochDay();

        List<CourseCompletionRecord> allCompletions = new ArrayList<>();
        int processedUsers = 0;

        for (SellerRef seller : allSellers) {
            UUID userId = seller.userId();
            Timestamp userCreated = userCreationDates.get(userId);
            long userStartEpoch;
            if (userCreated != null) {
                LocalDate userStart = userCreated.toLocalDateTime().toLocalDate();
                userStartEpoch = Math.max(userStart.toEpochDay(), seedStartEpoch);
            } else {
                userStartEpoch = seedStartEpoch;
            }
            int availableDays = (int) (seedEndEpoch - userStartEpoch);
            if (availableDays <= 0) continue;

            // Determine learner profile
            double profileRoll = random.nextDouble();
            int minCourses;
            int maxCourses;
            if (profileRoll < 0.20) {
                // Heavy learner
                minCourses = 20;
                maxCourses = 25;
            } else if (profileRoll < 0.70) {
                // Average learner
                minCourses = 12;
                maxCourses = 18;
            } else {
                // Light learner
                minCourses = 5;
                maxCourses = 10;
            }

            int numCourses = minCourses + random.nextInt(maxCourses - minCourses + 1);
            numCourses = Math.min(numCourses, courseIds.size()); // can't exceed catalog

            // Pick random courses (no duplicates per user)
            List<UUID> shuffledCourses = new ArrayList<>(courseIds);
            Collections.shuffle(shuffledCourses, random);

            Set<UUID> completedCourseIds = new HashSet<>();
            for (int c = 0; c < numCourses; c++) {
                UUID courseId = shuffledCourses.get(c);
                if (!completedCourseIds.add(courseId)) continue;

                // Distribute completion dates throughout the user's employment period
                int daysOffset = random.nextInt(availableDays);
                LocalDate completedAt = LocalDate.ofEpochDay(userStartEpoch + daysOffset);

                // Source: 70% ORGANIC, 30% INCENTIVE
                String source;
                if (random.nextDouble() < 0.30) {
                    UUID matchedIncentive = findMatchingTrainingIncentive(
                            trainingIncentives, completedAt, random);
                    source = matchedIncentive != null ? "INCENTIVE" : "ORGANIC";
                } else {
                    source = "ORGANIC";
                }

                String productCategory = courseCategoryMap.get(courseId);
                allCompletions.add(new CourseCompletionRecord(
                        userId, courseId, productCategory, completedAt, source));
            }

            processedUsers++;
            if (processedUsers % 100 == 0) {
                log.info("Pre-computed course completions for {}/{} users ({} records so far)",
                        processedUsers, allSellers.size(), allCompletions.size());
            }
        }

        log.info("Course completion pre-computation complete: {} records for {} users",
                allCompletions.size(), processedUsers);
        return allCompletions;
    }

    /** Find a training incentive whose date range covers the given completion date. */
    private UUID findMatchingTrainingIncentive(List<Map<String, Object>> trainingIncentives,
                                               LocalDate completedAt, Random random) {
        // Shuffle to avoid always picking the same incentive
        List<Map<String, Object>> shuffled = new ArrayList<>(trainingIncentives);
        Collections.shuffle(shuffled, random);
        for (Map<String, Object> inc : shuffled) {
            LocalDate startDate = localDateFromObj(inc.get("start_date"));
            LocalDate endDate = localDateFromObj(inc.get("end_date"));
            if (!completedAt.isBefore(startDate) && !completedAt.isAfter(endDate)) {
                return (UUID) inc.get("id");
            }
        }
        return null;
    }

    /**
     * Computes per-partner, per-product-category training boosts from the pre-computed
     * course completion schedule.
     *
     * Boost formula:
     *   - Base: 1.15 for any completion in that category
     *   - +0.05 per additional completion (capped at 1.40)
     *
     * Returns: partnerId -> {category -> boost multiplier}
     */
    public Map<UUID, Map<String, Double>> computeTrainingBoosts(
            List<CourseCompletionRecord> completions, List<SellerRef> sellers) {

        // Build user-to-partner mapping
        Map<UUID, UUID> userToPartner = new HashMap<>();
        for (SellerRef s : sellers) {
            userToPartner.put(s.userId(), s.partnerId());
        }

        // Aggregate completions per partner per category
        // partnerId -> category -> list of completion dates
        Map<UUID, Map<String, List<LocalDate>>> partnerCategoryCompletions = new HashMap<>();
        for (CourseCompletionRecord rec : completions) {
            UUID partnerId = userToPartner.get(rec.userId());
            if (partnerId == null || rec.productCategory() == null) continue;
            partnerCategoryCompletions
                    .computeIfAbsent(partnerId, k -> new HashMap<>())
                    .computeIfAbsent(rec.productCategory(), k -> new ArrayList<>())
                    .add(rec.completedAt());
        }

        // Compute boost multipliers
        Map<UUID, Map<String, Double>> result = new HashMap<>();
        for (Map.Entry<UUID, Map<String, List<LocalDate>>> partnerEntry
                : partnerCategoryCompletions.entrySet()) {
            Map<String, Double> boosts = new HashMap<>();
            for (Map.Entry<String, List<LocalDate>> catEntry
                    : partnerEntry.getValue().entrySet()) {
                int count = catEntry.getValue().size();
                // Base 1.15 + 0.05 per additional, capped at 1.40
                double boost = Math.min(1.40, 1.15 + 0.05 * (count - 1));
                boosts.put(catEntry.getKey(), boost);
            }
            result.put(partnerEntry.getKey(), boosts);
        }

        return result;
    }

    /**
     * Applies training boosts to category weights, creating a new array.
     * Only applies boosts for categories where completions exist (temporal causation
     * is handled by the boost map already representing cumulative completions).
     */
    public double[] applyTrainingBoost(double[] baseWeights, List<String> catList,
                                       Map<String, Double> partnerBoosts,
                                       LocalDate orderDate) {
        double[] boosted = new double[baseWeights.length];
        double sum = 0;
        for (int i = 0; i < baseWeights.length; i++) {
            String cat = catList.get(i);
            Double boost = partnerBoosts.get(cat);
            if (boost != null) {
                boosted[i] = baseWeights[i] * boost;
            } else {
                boosted[i] = baseWeights[i];
            }
            sum += boosted[i];
        }
        // Re-normalize
        if (sum > 0) {
            for (int i = 0; i < boosted.length; i++) {
                boosted[i] /= sum;
            }
        }
        return boosted;
    }

    /**
     * Persists pre-computed course completion records to the user_course_completions table.
     * Uses batch inserts with ON CONFLICT DO NOTHING for idempotency.
     */
    public void persistCourseCompletions(UUID clientId,
                                          List<CourseCompletionRecord> completions) {
        List<Object[]> batch = new ArrayList<>(BATCH_SIZE);
        Timestamp now = Timestamp.from(Instant.now());
        int persisted = 0;

        for (CourseCompletionRecord rec : completions) {
            Timestamp completedTs = Timestamp.from(
                    rec.completedAt().atStartOfDay(ZoneOffset.UTC).toInstant());
            batch.add(new Object[]{
                    UUID.randomUUID(), clientId, rec.userId(), rec.courseId(),
                    completedTs, rec.source(), now
            });
            persisted++;

            if (batch.size() >= BATCH_SIZE) {
                flushCourseCompletions(batch);
                batch.clear();
            }
        }

        if (!batch.isEmpty()) {
            flushCourseCompletions(batch);
        }

        log.info("Persisted {} course completion records to user_course_completions", persisted);
    }

    private void flushCourseCompletions(List<Object[]> batch) {
        jdbc.batchUpdate("INSERT INTO user_course_completions " +
                "(id, client_id, user_id, course_id, completed_at, source, " +
                "created_at) VALUES (?,?,?,?,?,?,?) " +
                "ON CONFLICT DO NOTHING", batch);
    }

    private LocalDate localDateFromObj(Object dateObj) {
        if (dateObj instanceof java.sql.Date d) {
            return d.toLocalDate();
        }
        if (dateObj instanceof LocalDate ld) {
            return ld;
        }
        if (dateObj instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime().toLocalDate();
        }
        return LocalDate.parse(dateObj.toString());
    }
}
