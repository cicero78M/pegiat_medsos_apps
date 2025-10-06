package com.google.errorprone.annotations;

import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.CLASS;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Indicates that it is acceptable to ignore the return value of an annotated method or constructor.
 */
@Documented
@Target({METHOD, CONSTRUCTOR})
@Retention(CLASS)
public @interface CanIgnoreReturnValue {}
