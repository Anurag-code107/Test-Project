package com.tenxengage.app.audit;

import com.tenxengage.app.entity.enums.AuditAction;
import com.tenxengage.app.entity.enums.AuditResourceType;
import com.tenxengage.app.service.AuditLogService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;
import java.util.UUID;

@Aspect
@Component
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);
    private static final ExpressionParser PARSER = new SpelExpressionParser();

    private final AuditLogService auditLogService;

    public AuditAspect(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Around("@annotation(audited)")
    public Object audit(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable ex) {
            // Log the failed attempt
            try {
                AuditAction action = AuditAction.valueOf(audited.action().toUpperCase().replace(" ", "_"));
                AuditResourceType resourceType = AuditResourceType.valueOf(audited.resourceType());
                auditLogService.logAsync(action, resourceType, null, null,
                        "Failed: " + ex.getMessage(), Map.of("error", ex.getClass().getSimpleName()));
            } catch (Exception auditEx) {
                log.warn("Failed to write failure audit log: {}", auditEx.getMessage());
            }
            throw ex;
        }

        // Log the successful action asynchronously
        try {
            EvaluationContext ctx = buildEvaluationContext(joinPoint, result);

            AuditAction action = AuditAction.valueOf(audited.action().toUpperCase().replace(" ", "_"));
            AuditResourceType resourceType = AuditResourceType.valueOf(audited.resourceType());

            UUID resourceId = evaluateUuid(audited.resourceId(), ctx);
            String resourceName = evaluateString(audited.resourceName(), ctx);
            String description = evaluateString(audited.description(), ctx);

            if (description == null || description.isEmpty()) {
                description = audited.action() + " " + audited.resourceType().toLowerCase().replace("_", " ");
            }

            auditLogService.logAsync(action, resourceType, resourceId, resourceName, description, null);
        } catch (Exception ex) {
            log.warn("Failed to write audit log for {}: {}", joinPoint.getSignature().getName(), ex.getMessage());
        }

        return result;
    }

    private EvaluationContext buildEvaluationContext(ProceedingJoinPoint joinPoint, Object result) {
        StandardEvaluationContext ctx = new StandardEvaluationContext();
        ctx.setVariable("result", result);

        // Make method parameters available by name
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Parameter[] parameters = method.getParameters();
        Object[] args = joinPoint.getArgs();
        for (int i = 0; i < parameters.length; i++) {
            ctx.setVariable(parameters[i].getName(), args[i]);
        }

        return ctx;
    }

    private String evaluateString(String expression, EvaluationContext ctx) {
        if (expression == null || expression.isEmpty()) {
            return null;
        }
        try {
            Object value = PARSER.parseExpression(expression).getValue(ctx);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            log.debug("SpEL evaluation failed for '{}': {}", expression, e.getMessage());
            // If it's not a valid SpEL expression, treat it as a literal
            if (!expression.startsWith("#")) {
                return expression;
            }
            return null;
        }
    }

    private UUID evaluateUuid(String expression, EvaluationContext ctx) {
        String value = evaluateString(expression, ctx);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
