package com.documind.ingestion.application;

import com.documind.document.domain.DocumentStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * In-memory pub/sub bridging IngestionJobScheduler's status transitions to
 * SSE subscribers (DocumentProgressController). Single-instance only -- fine
 * for the current single-backend-instance deployment; if the app scales to
 * multiple instances, a subscriber connected to instance A won't see updates
 * from a job processed on instance B, and this would need to move to a
 * shared pub/sub (e.g. Postgres LISTEN/NOTIFY or Redis) at that point.
 */
@Component
public class DocumentProgressPublisher {

    private final Map<UUID, List<Consumer<DocumentStatus>>> subscribersByDocumentId = new ConcurrentHashMap<>();

    public Subscription subscribe(UUID documentId, Consumer<DocumentStatus> listener) {
        List<Consumer<DocumentStatus>> subscribers =
                subscribersByDocumentId.computeIfAbsent(documentId, key -> new CopyOnWriteArrayList<>());
        subscribers.add(listener);
        return () -> subscribers.remove(listener);
    }

    public void publish(UUID documentId, DocumentStatus status) {
        List<Consumer<DocumentStatus>> subscribers = subscribersByDocumentId.get(documentId);
        if (subscribers != null) {
            subscribers.forEach(listener -> listener.accept(status));
        }
    }

    /** Number of currently-subscribed listeners for a document -- useful for diagnostics and for tests confirming a subscription was torn down. */
    public int subscriberCountFor(UUID documentId) {
        List<Consumer<DocumentStatus>> subscribers = subscribersByDocumentId.get(documentId);
        return subscribers == null ? 0 : subscribers.size();
    }

    @FunctionalInterface
    public interface Subscription extends AutoCloseable {
        @Override
        void close();
    }
}
