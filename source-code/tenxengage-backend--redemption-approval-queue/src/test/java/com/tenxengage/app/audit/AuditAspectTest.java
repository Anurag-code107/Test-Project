package com.tenxengage.app.audit;

import com.tenxengage.app.entity.enums.AuditAction;
import com.tenxengage.app.entity.enums.AuditResourceType;
import com.tenxengage.app.service.AuditLogService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.mockito.ArgumentMatchers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditAspectTest {

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private Audited audited;

    private AuditAspect aspect;

    // A no-parameter method we can use as a placeholder for MethodSignature.getMethod()
    private static final Method PLACEHOLDER_METHOD;

    static {
        try {
            PLACEHOLDER_METHOD = Object.class.getDeclaredMethod("toString");
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @BeforeEach
    void setUp() throws Throwable {
        aspect = new AuditAspect(auditLogService);
    }

    /**
     * Configures the mocked joinPoint with a MethodSignature backed by Object#toString()
     * so that buildEvaluationContext can iterate over zero parameters without NPE.
     */
    private void stubJoinPointSignature() throws Throwable {
        MethodSignature signature = mock(MethodSignature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(PLACEHOLDER_METHOD);
        when(joinPoint.getArgs()).thenReturn(new Object[]{});
    }

    // -------------------------------------------------------------------------
    // Test 1: successful join point → logs success audit
    // -------------------------------------------------------------------------

    @Test
    void audit_successfulJoinPoint_logsSuccessAudit() throws Throwable {
        stubJoinPointSignature();
        when(joinPoint.proceed()).thenReturn("result");

        when(audited.action()).thenReturn("Created");
        when(audited.resourceType()).thenReturn("INCENTIVE");
        when(audited.resourceId()).thenReturn("");
        when(audited.resourceName()).thenReturn("");
        when(audited.description()).thenReturn("Incentive created");

        Object returnValue = aspect.audit(joinPoint, audited);

        assertThat(returnValue).isEqualTo("result");

        // Exactly one call: the success path, with null metadata
        verify(auditLogService).logAsync(
                eq(AuditAction.CREATED),
                eq(AuditResourceType.INCENTIVE),
                isNull(),
                isNull(),
                eq("Incentive created"),
                isNull()
        );

        // The failure variant passes a non-null metadata map — verify it was never triggered
        verify(auditLogService, never()).logAsync(
                any(AuditAction.class),
                any(AuditResourceType.class),
                isNull(),
                isNull(),
                ArgumentMatchers.startsWith("Failed:"),
                ArgumentMatchers.<Map<String, Object>>any()
        );
    }

    // -------------------------------------------------------------------------
    // Test 2: join point throws → logs failure with "Failed: ..." and rethrows
    // -------------------------------------------------------------------------

    @Test
    void audit_joinPointThrows_logsFailureAndRethrows() throws Throwable {
        RuntimeException cause = new RuntimeException("test error");
        when(joinPoint.proceed()).thenThrow(cause);

        when(audited.action()).thenReturn("Created");
        when(audited.resourceType()).thenReturn("INCENTIVE");

        assertThatThrownBy(() -> aspect.audit(joinPoint, audited))
                .isSameAs(cause);

        ArgumentCaptor<String> descCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).logAsync(
                eq(AuditAction.CREATED),
                eq(AuditResourceType.INCENTIVE),
                isNull(),
                isNull(),
                descCaptor.capture(),
                ArgumentMatchers.<Map<String, Object>>any()
        );

        assertThat(descCaptor.getValue()).contains("Failed: test error");
    }

    // -------------------------------------------------------------------------
    // Test 3: literal (non-SpEL) description is forwarded as-is
    // -------------------------------------------------------------------------

    @Test
    void audit_literalDescription_usesLiteralNotSpel() throws Throwable {
        stubJoinPointSignature();
        when(joinPoint.proceed()).thenReturn("ok");

        when(audited.action()).thenReturn("Edited");
        when(audited.resourceType()).thenReturn("USER");
        when(audited.resourceId()).thenReturn("");
        when(audited.resourceName()).thenReturn("");
        when(audited.description()).thenReturn("User profile updated by admin");

        aspect.audit(joinPoint, audited);

        verify(auditLogService).logAsync(
                eq(AuditAction.EDITED),
                eq(AuditResourceType.USER),
                isNull(),
                isNull(),
                eq("User profile updated by admin"),
                isNull()
        );
    }

    // -------------------------------------------------------------------------
    // Test 4: empty description → fallback "action resourcetype" string
    // -------------------------------------------------------------------------

    @Test
    void audit_emptyDescription_generatesFallback() throws Throwable {
        stubJoinPointSignature();
        when(joinPoint.proceed()).thenReturn("ok");

        when(audited.action()).thenReturn("Deleted");
        when(audited.resourceType()).thenReturn("CLAIM");
        when(audited.resourceId()).thenReturn("");
        when(audited.resourceName()).thenReturn("");
        when(audited.description()).thenReturn("");

        aspect.audit(joinPoint, audited);

        // Fallback: audited.action() + " " + audited.resourceType().toLowerCase().replace("_", " ")
        // "Deleted" + " " + "claim" = "Deleted claim"
        ArgumentCaptor<String> descCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).logAsync(
                eq(AuditAction.DELETED),
                eq(AuditResourceType.CLAIM),
                isNull(),
                isNull(),
                descCaptor.capture(),
                isNull()
        );

        assertThat(descCaptor.getValue()).isEqualTo("Deleted claim");
    }
}
