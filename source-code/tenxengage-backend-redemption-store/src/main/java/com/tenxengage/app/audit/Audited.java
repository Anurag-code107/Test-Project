package com.tenxengage.app.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method for automatic audit logging.
 * The AuditAspect intercepts annotated methods and records an audit log entry
 * on successful completion.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    /** The action performed: "Created", "Edited", "Deleted", etc. */
    String action();

    /** The resource type: "INCENTIVE", "USER", "CLAIM", etc. */
    String resourceType();

    /** SpEL expression to extract the resource name from the return value, e.g. "#result.body.data.name" */
    String resourceName() default "";

    /** SpEL expression to extract the resource ID from the return value, e.g. "#result.body.data.id" */
    String resourceId() default "";

    /** SpEL expression or literal description. */
    String description() default "";
}
