package com.documind.common.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

/**
 * Writes the same {@link ApiError} body {@link GlobalExceptionHandler} uses
 * for every other error response -- Spring Security's filter-chain rejections
 * (unauthenticated / access-denied) never reach {@code @RestControllerAdvice},
 * since that only intercepts exceptions from DispatcherServlet's own handler
 * invocation, not from filters running ahead of it.
 *
 * <p>Also guards against a real, reproducible failure mode on SSE endpoints
 * (ChatController, DocumentProgressController): those hand a Flux/reactive
 * chain off to a different thread pool (Reactor Netty's event-loop threads
 * for Spring AI's streaming ChatClient), which does not carry
 * SecurityContextHolder's ThreadLocal context across the hop. When Tomcat
 * re-enters the filter chain during async completion, authorization is
 * re-evaluated against that empty context and rejected -- but by then the
 * SseEmitter has already started writing the response, so attempting to
 * write a second, clean error response throws
 * "Unable to handle the Spring Security Exception because the response is
 * already committed." Checking {@code response.isCommitted()} first turns
 * that into a silent no-op instead of a logged exception, since there is
 * nothing meaningful left to send to the client at that point anyway.
 */
public class SecurityResponseHandler implements AccessDeniedHandler, AuthenticationEntryPoint {

    private static final Logger logger = LoggerFactory.getLogger(SecurityResponseHandler.class);

    private final ObjectMapper objectMapper;

    public SecurityResponseHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception) throws IOException {
        writeErrorOrSkipIfCommitted(request, response, HttpStatus.FORBIDDEN, "Access Denied", exception.getMessage());
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException {
        writeErrorOrSkipIfCommitted(request, response, HttpStatus.UNAUTHORIZED, "Authentication Failed", exception.getMessage());
    }

    private void writeErrorOrSkipIfCommitted(
            HttpServletRequest request, HttpServletResponse response, HttpStatus status, String title, String detail
    ) throws IOException {
        if (response.isCommitted()) {
            logger.debug(
                    "Skipping {} error body for {} {} -- response already committed (likely an SSE stream already in progress)",
                    status.value(), request.getMethod(), request.getRequestURI()
            );
            return;
        }

        ApiError body = ApiError.of(title, status.value(), detail, request.getRequestURI());

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
