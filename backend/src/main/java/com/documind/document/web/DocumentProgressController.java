package com.documind.document.web;

import com.documind.document.domain.DocumentStatus;
import com.documind.ingestion.application.DocumentProgressPublisher;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;

/**
 * Pushes ingestion status changes to the browser via SSE rather than
 * polling: unidirectional (server -> client fits this exactly), works over
 * plain HTTP, and the browser's EventSource auto-reconnects if the
 * connection drops. See DocumentProgressPublisher for the in-memory pub/sub
 * this subscribes to.
 */
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentProgressController {

    private static final long NO_TIMEOUT = 0L;

    private final DocumentProgressPublisher documentProgressPublisher;

    public DocumentProgressController(DocumentProgressPublisher documentProgressPublisher) {
        this.documentProgressPublisher = documentProgressPublisher;
    }

    @GetMapping("/{documentId}/progress")
    public SseEmitter streamProgress(@PathVariable UUID documentId) {
        SseEmitter emitter = new SseEmitter(NO_TIMEOUT);

        DocumentProgressPublisher.Subscription subscription = documentProgressPublisher.subscribe(documentId, status -> {
            try {
                emitter.send(status);
                if (isTerminalStatus(status)) {
                    emitter.complete();
                }
            } catch (IOException exception) {
                emitter.completeWithError(exception);
            }
        });

        emitter.onCompletion(subscription::close);
        emitter.onTimeout(subscription::close);
        emitter.onError(throwable -> subscription.close());

        return emitter;
    }

    private boolean isTerminalStatus(DocumentStatus status) {
        return status == DocumentStatus.READY || status == DocumentStatus.FAILED;
    }
}
