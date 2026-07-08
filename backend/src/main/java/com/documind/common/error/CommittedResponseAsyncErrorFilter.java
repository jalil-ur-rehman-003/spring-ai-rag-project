package com.documind.common.error;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * Denies a noisy, harmless log pattern: Tomcat's internal async re-dispatch
 * (closing out an SseEmitter once ChatController's/
 * DocumentProgressController's stream completes) re-enters the whole servlet
 * filter chain, including Spring Security's AuthorizationFilter. That
 * re-dispatch carries no SecurityContext (JWT auth only runs on the original
 * request, not Tomcat's internal completion dispatch), so AuthorizationFilter
 * throws {@code AuthorizationDeniedException} -- but the SSE response has
 * already been committed by then (the real answer was already streamed to
 * the client successfully), so Spring Security's own
 * ExceptionTranslationFilter can't write a clean error response and instead
 * lets the servlet container log the exception directly (as
 * "Servlet.service() ... threw exception"), followed by a second log entry
 * when Tomcat's own error-page dispatch hits the same already-committed
 * problem ("Unable to handle the Spring Security Exception because the
 * response is already committed."). Both are misleading ERROR-level entries
 * for a request that already completed successfully from the client's
 * perspective.
 *
 * <p>{@link SecurityResponseHandler} (a custom AccessDeniedHandler /
 * AuthenticationEntryPoint) fixes the response body for a genuine,
 * not-yet-committed rejection, but cannot reach this case at all --
 * ExceptionTranslationFilter checks {@code response.isCommitted()} before
 * ever calling a custom handler. Since every genuine, reachable
 * authentication/authorization failure in this app is now handled cleanly by
 * {@link SecurityResponseHandler} before a response is committed, an
 * {@code AuthorizationDeniedException} surfacing as an uncaught,
 * directly-logged exception at this point is only ever this specific,
 * already-diagnosed async-redispatch artifact -- so both of its log entries
 * are denied here. Every other exception, including any other kind of
 * unhandled failure, passes through unaffected.
 */
public class CommittedResponseAsyncErrorFilter extends Filter<ILoggingEvent> {

    private static final String AUTHORIZATION_DENIED_EXCEPTION =
            "org.springframework.security.authorization.AuthorizationDeniedException";

    @Override
    public FilterReply decide(ILoggingEvent event) {
        if (isCausedByAuthorizationDeniedException(event.getThrowableProxy())) {
            return FilterReply.DENY;
        }
        return FilterReply.NEUTRAL;
    }

    private boolean isCausedByAuthorizationDeniedException(IThrowableProxy throwableProxy) {
        for (IThrowableProxy current = throwableProxy; current != null; current = current.getCause()) {
            if (AUTHORIZATION_DENIED_EXCEPTION.equals(current.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
