package com.tenxengage.app.audit;

import com.tenxengage.app.entity.enums.AuditAction;
import com.tenxengage.app.entity.enums.AuditResourceType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every {@code @Audited} names an enum constant that actually exists.
 *
 * <p>{@link AuditAspect} resolves {@code action} and {@code resourceType} with {@code valueOf} at runtime
 * and catches the failure, so a name that matches nothing costs the audit row and nothing else — no
 * exception reaches the caller, no test goes red, and the endpoint keeps working. That is the right
 * behaviour for the request (an audit write must never fail a payout) and the reason the mistake is
 * invisible.</p>
 *
 * <p>It bit us: {@code @Audited(action = "DISTRIBUTED")} shipped on the distribution endpoint before the
 * constant existed, and every distribution silently lost its audit row until someone happened to read the
 * log. This scans the source rather than the Spring context so it needs no application to start, and it
 * covers annotations added later by anyone.</p>
 */
class AuditedAnnotationsResolveTest {

    /** Matches the literal in {@code @Audited(action = "…")} and {@code resourceType = "…"}. */
    private static final Pattern ACTION = Pattern.compile("@Audited\\s*\\([^)]*?action\\s*=\\s*\"([^\"]+)\"",
            Pattern.DOTALL);
    private static final Pattern RESOURCE_TYPE = Pattern.compile(
            "@Audited\\s*\\([^)]*?resourceType\\s*=\\s*\"([^\"]+)\"", Pattern.DOTALL);

    private record Usage(String value, Path file) {
        @Override public String toString() {
            return "\"" + value + "\" in " + file.getFileName();
        }
    }

    private List<Usage> scan(Pattern pattern) throws IOException {
        Path root = Path.of("src", "main", "java");
        assertThat(root).as("source root — this test reads the annotations from source").exists();

        List<Usage> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher m = pattern.matcher(Files.readString(file));
                while (m.find()) {
                    found.add(new Usage(m.group(1), file));
                }
            }
        }
        return found;
    }

    /** The aspect's own normalisation, so this test accepts exactly what it accepts. */
    private static String normalise(String raw) {
        return raw.toUpperCase().replace(" ", "_");
    }

    @Test
    void everyAuditedActionNamesARealConstant() throws IOException {
        List<Usage> usages = scan(ACTION);
        assertThat(usages).as("no @Audited(action=…) found — the scan pattern has probably rotted").isNotEmpty();

        List<Usage> unresolvable = usages.stream()
                .filter(u -> !isEnumConstant(AuditAction.class, normalise(u.value())))
                .toList();

        assertThat(unresolvable)
                .as("@Audited actions with no matching AuditAction constant. The aspect swallows this, so "
                        + "the endpoint still works and the audit row is silently lost.")
                .isEmpty();
    }

    @Test
    void everyAuditedResourceTypeNamesARealConstant() throws IOException {
        List<Usage> usages = scan(RESOURCE_TYPE);
        assertThat(usages).isNotEmpty();

        List<Usage> unresolvable = usages.stream()
                .filter(u -> !isEnumConstant(AuditResourceType.class, u.value()))
                .toList();

        // resourceType is NOT normalised by the aspect — it is passed to valueOf as written.
        assertThat(unresolvable)
                .as("@Audited resourceTypes with no matching AuditResourceType constant")
                .isEmpty();
    }

    private static boolean isEnumConstant(Class<? extends Enum<?>> type, String name) {
        for (Enum<?> c : type.getEnumConstants()) {
            if (c.name().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
