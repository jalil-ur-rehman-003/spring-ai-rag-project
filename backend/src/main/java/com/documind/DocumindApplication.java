package com.documind;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the DocuMind AI Portal backend.
 *
 * Scheduling is enabled because the document ingestion pipeline is driven by a
 * polling worker ({@code IngestionJobScheduler}) rather than an external message
 * broker. Async is enabled so long-running ingestion steps (extraction, chunking,
 * embedding) can run off the request thread.
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
public class DocumindApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocumindApplication.class, args);
    }
}
