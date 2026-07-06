package com.documind.ingestion.infrastructure;

import com.documind.ingestion.application.DocumentExtractionException;
import com.documind.ingestion.application.TextExtractionService;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;

/**
 * First-pass extraction implementation: Tika's AutoDetectParser handles PDF
 * (and other formats) but only produces plain text, not real Markdown
 * structure (headings/lists/tables). This is a deliberate starting point --
 * see docs/DECISIONS.md -- to unblock the rest of the ingestion pipeline;
 * swap in a layout-aware converter behind the same TextExtractionService
 * interface later without touching ChunkingService or the scheduler.
 */
@Service
public class TikaTextExtractionService implements TextExtractionService {

    private static final int NO_WRITE_LIMIT = -1;

    @Override
    public String extractToMarkdown(InputStream documentContent) {
        try {
            AutoDetectParser parser = new AutoDetectParser();
            BodyContentHandler contentHandler = new BodyContentHandler(NO_WRITE_LIMIT);
            parser.parse(documentContent, contentHandler, new Metadata(), new ParseContext());
            return contentHandler.toString();
        } catch (IOException | SAXException | TikaException exception) {
            throw new DocumentExtractionException("Failed to extract text content from uploaded document", exception);
        }
    }
}
