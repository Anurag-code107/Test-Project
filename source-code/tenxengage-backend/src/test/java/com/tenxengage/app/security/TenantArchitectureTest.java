package com.tenxengage.app.security;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.hibernate.annotations.Filter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Architectural tests that enforce multi-tenancy invariants at build time.
 * These tests prevent the "forgot to add @Filter" class of bugs by failing
 * the build if a TenantAware entity is missing its Hibernate filter annotation.
 */
class TenantArchitectureTest {

    private static JavaClasses importedClasses;

    @BeforeAll
    static void setUp() {
        importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.tenxengage.app.entity");
    }

    @Test
    void tenantAwareEntities_mustHaveFilterAnnotation() {
        classes()
                .that().implement(com.tenxengage.app.entity.TenantAware.class)
                .should(haveFilterAnnotation())
                .because("All TenantAware entities must have @Filter(name=\"tenantFilter\") " +
                        "to ensure Hibernate automatically scopes queries by tenant. " +
                        "Without this annotation, queries may return cross-tenant data.")
                .check(importedClasses);
    }

    @Test
    void tenantAwareEntities_mustHaveClientIdField() {
        classes()
                .that().implement(com.tenxengage.app.entity.TenantAware.class)
                .should(haveClientIdField())
                .because("All TenantAware entities must declare a clientId field " +
                        "mapped to the client_id column for tenant isolation.")
                .check(importedClasses);
    }

    private static ArchCondition<JavaClass> haveFilterAnnotation() {
        return new ArchCondition<>("have @Filter(name=\"tenantFilter\") annotation") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                boolean hasFilter = javaClass.isAnnotatedWith(Filter.class);
                if (!hasFilter) {
                    events.add(SimpleConditionEvent.violated(javaClass,
                            javaClass.getName() + " implements TenantAware but is missing " +
                                    "@Filter(name=\"tenantFilter\", condition=\"client_id = :clientId\")"));
                }
            }
        };
    }

    private static ArchCondition<JavaClass> haveClientIdField() {
        return new ArchCondition<>("have a clientId field") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                boolean hasClientId = javaClass.getAllFields().stream()
                        .anyMatch(field -> "clientId".equals(field.getName()));
                if (!hasClientId) {
                    events.add(SimpleConditionEvent.violated(javaClass,
                            javaClass.getName() + " implements TenantAware but has no clientId field"));
                }
            }
        };
    }
}
