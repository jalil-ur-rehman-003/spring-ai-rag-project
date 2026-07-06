package com.documind.ingestion.application;

import com.documind.document.domain.DocumentStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentProgressPublisherTest {

    private final DocumentProgressPublisher documentProgressPublisher = new DocumentProgressPublisher();

    @Test
    void subscriberReceivesStatusUpdatesPublishedForItsDocument() {
        UUID documentId = UUID.randomUUID();
        List<DocumentStatus> receivedStatuses = new CopyOnWriteArrayList<>();

        documentProgressPublisher.subscribe(documentId, receivedStatuses::add);
        documentProgressPublisher.publish(documentId, DocumentStatus.EXTRACTING);
        documentProgressPublisher.publish(documentId, DocumentStatus.CHUNKING);

        assertThat(receivedStatuses).containsExactly(DocumentStatus.EXTRACTING, DocumentStatus.CHUNKING);
    }

    @Test
    void subscriberDoesNotReceiveUpdatesPublishedForADifferentDocument() {
        UUID subscribedDocumentId = UUID.randomUUID();
        UUID otherDocumentId = UUID.randomUUID();
        List<DocumentStatus> receivedStatuses = new CopyOnWriteArrayList<>();

        documentProgressPublisher.subscribe(subscribedDocumentId, receivedStatuses::add);
        documentProgressPublisher.publish(otherDocumentId, DocumentStatus.EXTRACTING);

        assertThat(receivedStatuses).isEmpty();
    }

    @Test
    void publishingWithNoSubscribersDoesNotThrow() {
        assertThat(catchThrowable(() -> documentProgressPublisher.publish(UUID.randomUUID(), DocumentStatus.READY))).isNull();
    }

    @Test
    void unsubscribingStopsFurtherDeliveryToThatListener() {
        UUID documentId = UUID.randomUUID();
        List<DocumentStatus> receivedStatuses = new CopyOnWriteArrayList<>();

        DocumentProgressPublisher.Subscription subscription = documentProgressPublisher.subscribe(documentId, receivedStatuses::add);
        documentProgressPublisher.publish(documentId, DocumentStatus.EXTRACTING);
        subscription.close();
        documentProgressPublisher.publish(documentId, DocumentStatus.CHUNKING);

        assertThat(receivedStatuses).containsExactly(DocumentStatus.EXTRACTING);
    }

    private static Throwable catchThrowable(Runnable runnable) {
        try {
            runnable.run();
            return null;
        } catch (Throwable throwable) {
            return throwable;
        }
    }
}
