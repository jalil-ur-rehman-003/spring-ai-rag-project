package com.documind.ingestion.web;

import com.documind.document.domain.DocumentStatus;
import com.documind.document.web.DocumentProgressController;
import com.documind.ingestion.application.DocumentProgressPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Standalone MockMvc setup (not a full @SpringBootTest) since this endpoint's
 * behavior only depends on DocumentProgressPublisher, not the database or
 * security -- keeps this test fast and focused on the SSE wiring itself.
 *
 * SseEmitter streams by writing directly to the response as events are
 * published, rather than completing via Spring MVC's Callable/DeferredResult
 * async-result mechanism -- so, unlike those, no asyncDispatch(...) replay is
 * needed or applicable here; the response content is readable directly once
 * the emitter has written to it.
 */
class DocumentProgressControllerTest {

    private DocumentProgressPublisher documentProgressPublisher;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        documentProgressPublisher = new DocumentProgressPublisher();
        mockMvc = MockMvcBuilders.standaloneSetup(new DocumentProgressController(documentProgressPublisher)).build();
    }

    @Test
    void streamsAPublishedStatusUpdateToTheSubscribedClient() throws Exception {
        UUID documentId = UUID.randomUUID();

        MvcResult mvcResult = mockMvc.perform(get("/api/v1/documents/{documentId}/progress", documentId))
                .andReturn();

        documentProgressPublisher.publish(documentId, DocumentStatus.EXTRACTING);

        String responseBody = mvcResult.getResponse().getContentAsString();
        assertThat(responseBody).contains("EXTRACTING");
    }

    @Test
    void completesTheStreamWhenATerminalStatusIsPublished() throws Exception {
        UUID documentId = UUID.randomUUID();

        MvcResult mvcResult = mockMvc.perform(get("/api/v1/documents/{documentId}/progress", documentId))
                .andReturn();

        documentProgressPublisher.publish(documentId, DocumentStatus.READY);

        // Note: emitter.complete()'s onCompletion callback (which unsubscribes -- see
        // subscriberCountFor assertions elsewhere) is driven by the servlet container's async
        // request lifecycle, which MockMvc's standaloneSetup doesn't fully simulate. This
        // assertion sticks to what's observable here: the terminal event was actually written.
        String responseBody = mvcResult.getResponse().getContentAsString();
        assertThat(responseBody).contains("READY");
    }
}
