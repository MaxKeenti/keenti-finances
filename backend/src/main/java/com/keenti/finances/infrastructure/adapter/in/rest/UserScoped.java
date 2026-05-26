package com.keenti.finances.infrastructure.adapter.in.rest;

import jakarta.interceptor.InterceptorBinding;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a CDI bean whose methods must run with Hibernate's {@code userScope}
 * and {@code softDelete} filters enabled on the active session.
 *
 * <p>Necessary because {@link UserScopeFilter} runs in its own {@code @Transactional}
 * scope — its {@code session.enableFilter(...)} calls don't survive into the
 * resource method's separate transaction/session. The interceptor that backs
 * this annotation re-enables both filters inside the method's transactional
 * scope, where the queries actually run.
 */
@Inherited
@InterceptorBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface UserScoped {
}
